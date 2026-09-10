package com.eventmanagement.processor.adapters.postgres;

import com.eventmanagement.processor.adapters.rules.RuleCompiler;
import com.eventmanagement.processor.domain.rules.*;
import com.eventmanagement.processor.ports.out.RuleSnapshots;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import javax.sql.DataSource;
import java.sql.*;
import java.util.*;

/** Versioned administration; caller context is unverified while authentication is deferred. */
@ApplicationScoped
public class PostgresRuleStore implements RuleSnapshots {
    private final DataSource dataSource;
    private final RuleCompiler compiler;
    public enum Status { ENABLED, DISABLED, RETIRED }
    @Inject public PostgresRuleStore(DataSource dataSource, RuleCompiler compiler) {
        this.dataSource=dataSource; this.compiler=compiler;
    }
    public long createVersion(String tenant,String json,String actor,String reason) throws SQLException {
        try(var c=dataSource.getConnection()) {
            c.setAutoCommit(false);
            try { long revision=createVersion(c,tenant,json,actor,reason,null);c.commit();return revision; }
            catch(SQLException|RuntimeException error) {c.rollback();throw error;}
        }
    }
    public long createVersion(Connection c,String tenant,String json,String actor,String reason,Long expectedRevision) throws SQLException {
        scope(tenant,actor,reason);var compiled=compiler.compile(json);var rule=compiled.rule();
                try(var s=c.prepareStatement("INSERT INTO event_processor.rule_definition(tenant,rule_id) VALUES (?,?) ON CONFLICT DO NOTHING")) {
                    s.setString(1,tenant);s.setString(2,rule.id());s.executeUpdate();
                }
                long revision; int latest; String status;
                try(var s=c.prepareStatement("SELECT latest_version,revision,status FROM event_processor.rule_definition WHERE tenant=? AND rule_id=? FOR UPDATE")) {
                    s.setString(1,tenant);s.setString(2,rule.id());try(var r=s.executeQuery()){r.next();latest=r.getInt(1);revision=r.getLong(2);status=r.getString(3);}
                }
                if(expectedRevision!=null && revision!=expectedRevision)throw new IllegalArgumentException("REVISION_CONFLICT");
                if(status.equals("RETIRED"))throw new IllegalArgumentException("RULE_RETIRED");
                if(rule.blackout()!=null && rule.blackout().scope().containsKey("customerCode") && !tenant.equals(rule.blackout().scope().get("customerCode")))
                    throw new IllegalArgumentException("BLACKOUT_TENANT_MISMATCH");
                if(rule.inventory()!=null && rule.inventory().scope().containsKey("customerCode") && !tenant.equals(rule.inventory().scope().get("customerCode")))
                    throw new IllegalArgumentException("INVENTORY_TENANT_MISMATCH");
                if(latest>0 && !load(c,tenant,rule.id(),latest).capability().equals(rule.capability()))throw new IllegalArgumentException("RULE_CAPABILITY_IMMUTABLE");
                if(rule.version()!=latest+1)throw new IllegalArgumentException("NEXT_VERSION_REQUIRED");
                try(var s=c.prepareStatement("INSERT INTO event_processor.rule_version(tenant,rule_id,version,checksum,definition,actor,reason) VALUES (?,?,?,?,?::jsonb,?,?)")) {
                    s.setString(1,tenant);s.setString(2,rule.id());s.setInt(3,rule.version());s.setString(4,rule.checksum());
                    s.setString(5,compiled.canonicalJson());s.setString(6,actor);s.setString(7,reason);s.executeUpdate();
                }
                try(var s=c.prepareStatement("UPDATE event_processor.rule_definition SET latest_version=?,revision=revision+1 WHERE tenant=? AND rule_id=?")) {
                    s.setInt(1,rule.version());s.setString(2,tenant);s.setString(3,rule.id());s.executeUpdate();
                }
                audit(c,tenant,rule.id(),revision+1,rule.version(),"CREATED",actor,reason);
        return revision+1;
    }
    public long changeStatus(String tenant,String id,int version,Status status,long expectedRevision,String actor,String reason) throws SQLException {
        try(var c=dataSource.getConnection()) {
            c.setAutoCommit(false);
            try { long revision=changeStatus(c,tenant,id,version,status,expectedRevision,actor,reason);c.commit();return revision; }
            catch(SQLException|RuntimeException error) {c.rollback();throw error;}
        }
    }
    public long changeStatus(Connection c,String tenant,String id,int version,Status status,long expectedRevision,String actor,String reason) throws SQLException {
        scope(tenant,actor,reason);Objects.requireNonNull(status);
                try(var lock=c.prepareStatement("SELECT pg_advisory_xact_lock(hashtextextended(?, 11))")) {
                    lock.setString(1,tenant);lock.execute();
                }
                long revision;String previous;int effectiveVersion;
                try(var s=c.prepareStatement("SELECT revision,status,COALESCE(active_version,latest_version) FROM event_processor.rule_definition WHERE tenant=? AND rule_id=? FOR UPDATE")) {
                    s.setString(1,tenant);s.setString(2,id);try(var r=s.executeQuery()){
                        if(!r.next())throw new IllegalArgumentException("RULE_NOT_FOUND"); revision=r.getLong(1);previous=r.getString(2);effectiveVersion=r.getInt(3);
                    }
                }
                if(revision!=expectedRevision)throw new IllegalArgumentException("REVISION_CONFLICT");
                if(previous.equals("RETIRED"))throw new IllegalArgumentException("RULE_RETIRED");
                if(status!=Status.ENABLED && version!=effectiveVersion)throw new IllegalArgumentException("ACTIVE_VERSION_MISMATCH");
                if(status==Status.ENABLED) {
                    try(var count=c.prepareStatement("SELECT count(*) FROM event_processor.rule_definition WHERE tenant=? AND status='ENABLED' AND rule_id<>?")) {
                        count.setString(1,tenant);count.setString(2,id);try(var rows=count.executeQuery()){
                            rows.next();if(rows.getInt(1)>=256)throw new IllegalArgumentException("ACTIVE_RULE_LIMIT");
                        }
                    }
                }
                var rule=load(c,tenant,id,version);
                if(status==Status.ENABLED && !rule.enabled())throw new IllegalArgumentException("VERSION_NOT_ENABLEABLE");
                try(var s=c.prepareStatement("UPDATE event_processor.rule_definition SET active_version=?,status=?,revision=revision+1 WHERE tenant=? AND rule_id=?")) {
                    if(status==Status.ENABLED)s.setInt(1,version);else s.setNull(1,Types.INTEGER);
                    s.setString(2,status.name());s.setString(3,tenant);s.setString(4,id);s.executeUpdate();
                }
                audit(c,tenant,id,revision+1,version,status.name(),actor,reason);
        return revision+1;
    }
    @Override public RuleSnapshot snapshot(String tenant) {
        if(tenant!=null && tenant.isBlank()) return new RuleSnapshot(tenant,List.of());
        Objects.requireNonNull(tenant,"TENANT_REQUIRED");
        // One SELECT/MVCC statement sees one coherent committed configuration; no per-rule queries.
        try(var c=dataSource.getConnection();var s=c.prepareStatement("SELECT v.definition::text,v.checksum FROM event_processor.rule_definition d JOIN event_processor.rule_version v ON v.tenant=d.tenant AND v.rule_id=d.rule_id AND v.version=d.active_version WHERE d.tenant=? AND d.status='ENABLED' ORDER BY d.rule_id LIMIT 257")) {
            s.setString(1,tenant);s.setQueryTimeout(5);
            try(var r=s.executeQuery()) { var rules=new ArrayList<Rule>();while(r.next())rules.add(verified(r));return new RuleSnapshot(tenant,rules); }
        } catch(SQLException e) { throw new IllegalStateException("RULE_SNAPSHOT_UNAVAILABLE",e); }
    }
    private Rule load(Connection c,String tenant,String id,int version) throws SQLException {
        try(var s=c.prepareStatement("SELECT definition::text,checksum FROM event_processor.rule_version WHERE tenant=? AND rule_id=? AND version=?")) {
            s.setString(1,tenant);s.setString(2,id);s.setInt(3,version);
            try(var r=s.executeQuery()){if(!r.next())throw new IllegalArgumentException("VERSION_NOT_FOUND");return verified(r);}
        }
    }
    private Rule verified(ResultSet r) throws SQLException {
        Rule rule=compiler.compile(r.getString(1)).rule();
        if(!rule.checksum().equals(r.getString(2)))throw new IllegalStateException("RULE_CHECKSUM_MISMATCH");
        return rule;
    }
    private void audit(Connection c,String tenant,String id,long revision,int version,String status,String actor,String reason) throws SQLException {
        try(var s=c.prepareStatement("INSERT INTO event_processor.rule_change(tenant,rule_id,revision,version,status,actor,reason) VALUES (?,?,?,?,?,?,?)")) {
            s.setString(1,tenant);s.setString(2,id);s.setLong(3,revision);s.setInt(4,version);s.setString(5,status);s.setString(6,actor);s.setString(7,reason);s.executeUpdate();
        }
    }
    private static void scope(String tenant,String actor,String reason) {
        if(tenant==null||tenant.isBlank()||tenant.length()>128||actor==null||actor.isBlank()||actor.length()>128||reason==null||reason.isBlank()||reason.length()>2048)
            throw new IllegalArgumentException("TENANT_ACTOR_REASON_REQUIRED");
    }
}
