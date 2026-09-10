package com.eventmanagement.processor.adapters.postgres;

import com.eventmanagement.processor.domain.StableIdentity;
import com.eventmanagement.processor.domain.admin.*;
import com.eventmanagement.processor.ports.out.RuleAdministration;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.*;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import javax.sql.DataSource;
import java.sql.*;

@ApplicationScoped
public class PostgresRuleAdministration implements RuleAdministration {
    private final DataSource dataSource;
    private final PostgresRuleStore rules;
    private final ObjectMapper mapper;
    @Inject public PostgresRuleAdministration(DataSource dataSource,PostgresRuleStore rules,ObjectMapper mapper) {
        this.dataSource=dataSource;this.rules=rules;this.mapper=mapper;
    }
    @Override public Receipt mutate(Mutation m) {
        String hash=StableIdentity.of("admin-mutation-v1",m.change().name(),m.ruleId(),Integer.toString(m.version()),
                Long.toString(m.expectedRevision()),m.definition()==null?"":m.definition(),m.reason());
        try(var c=dataSource.getConnection()) {
            c.setAutoCommit(false);
            try {
                try(var s=c.prepareStatement("INSERT INTO event_processor.admin_request(tenant,actor,request_id,request_hash) VALUES (?,?,?,?) ON CONFLICT DO NOTHING")) {
                    s.setString(1,m.actor().tenant());s.setString(2,m.actor().subject());s.setString(3,m.requestId());s.setString(4,hash);s.executeUpdate();
                }
                try(var s=c.prepareStatement("SELECT request_hash,response::text FROM event_processor.admin_request WHERE tenant=? AND actor=? AND request_id=? FOR UPDATE")) {
                    s.setString(1,m.actor().tenant());s.setString(2,m.actor().subject());s.setString(3,m.requestId());
                    try(var r=s.executeQuery()) {
                        r.next();if(!hash.equals(r.getString(1)))throw new AdminFailure(AdminFailure.Kind.CONFLICT,"IDEMPOTENCY_CONFLICT");
                        if(r.getString(2)!=null) {var result=mapper.readValue(r.getString(2),Receipt.class);c.commit();return result;}
                    }
                }
                long revision;
                if(m.change()==Change.CREATE)revision=rules.createVersion(c,m.actor().tenant(),m.definition(),m.actor().subject(),m.reason(),m.expectedRevision());
                else revision=rules.changeStatus(c,m.actor().tenant(),m.ruleId(),m.version(),switch(m.change()) {
                    case ENABLE->PostgresRuleStore.Status.ENABLED;case DISABLE->PostgresRuleStore.Status.DISABLED;
                    case RETIRE->PostgresRuleStore.Status.RETIRED;default->throw new IllegalArgumentException("INVALID_CHANGE");
                },m.expectedRevision(),m.actor().subject(),m.reason());
                String status=switch(m.change()){case CREATE->"CREATED";case ENABLE->"ENABLED";case DISABLE->"DISABLED";case RETIRE->"RETIRED";};
                var receipt=new Receipt(m.ruleId(),m.version(),revision,status,m.requestId());
                try(var s=c.prepareStatement("UPDATE event_processor.admin_request SET response=?::jsonb WHERE tenant=? AND actor=? AND request_id=?")) {
                    s.setString(1,mapper.writeValueAsString(receipt));s.setString(2,m.actor().tenant());s.setString(3,m.actor().subject());s.setString(4,m.requestId());s.executeUpdate();
                }
                audit(c,m.actor(),m.requestId(),m.change().name(),m.ruleId(),m.expectedRevision(),revision,"SUCCESS");
                c.commit();return receipt;
            } catch(Exception e) {c.rollback();throw e;}
        } catch(AdminFailure failure) {recordRejected(m);throw failure;}
        catch(IllegalArgumentException failure) {recordRejected(m);throw mapped(failure.getMessage());}
        catch(Exception e) {throw unavailable();}
    }
    @Override public String list(String tenant,int limit,String after) {
        try(var c=dataSource.getConnection();var s=c.prepareStatement("SELECT rule_id,latest_version,active_version,status,revision FROM event_processor.rule_definition WHERE tenant=? AND rule_id>? ORDER BY rule_id LIMIT ?")) {
            s.setString(1,tenant);s.setString(2,after);s.setInt(3,limit+1);s.setQueryTimeout(5);
            ObjectNode page=mapper.createObjectNode();ArrayNode items=page.putArray("items");
            try(var r=s.executeQuery()) {
                while(r.next()) {if(items.size()==limit){page.put("next",items.get(items.size()-1).path("id").asText());break;}items.add(summary(r));}
            }
            if(!page.has("next"))page.putNull("next");return page.toString();
        } catch(SQLException e){throw unavailable();}
    }
    @Override public String get(String tenant,String id,Integer version) {
        try(var c=dataSource.getConnection();var s=c.prepareStatement("SELECT d.rule_id,d.latest_version,d.active_version,d.status,d.revision,v.definition::text,v.checksum FROM event_processor.rule_definition d JOIN event_processor.rule_version v ON v.tenant=d.tenant AND v.rule_id=d.rule_id AND v.version=COALESCE(?,d.latest_version) WHERE d.tenant=? AND d.rule_id=?")) {
            if(version==null)s.setNull(1,Types.INTEGER);else s.setInt(1,version);s.setString(2,tenant);s.setString(3,id);s.setQueryTimeout(5);
            try(var r=s.executeQuery()) {if(!r.next())throw missing();var result=summary(r);result.set("rule",mapper.readTree(r.getString(6)));result.put("checksum",r.getString(7));return result.toString();}
        } catch(AdminFailure e){throw e;}catch(Exception e){throw unavailable();}
    }
    @Override public String history(String tenant,String id,int limit,long after) {
        get(tenant,id,null);
        try(var c=dataSource.getConnection();var s=c.prepareStatement("SELECT h.revision,h.version,h.status,h.actor,h.reason,h.created_at,v.checksum FROM event_processor.rule_change h JOIN event_processor.rule_version v ON v.tenant=h.tenant AND v.rule_id=h.rule_id AND v.version=h.version WHERE h.tenant=? AND h.rule_id=? AND h.revision>? ORDER BY h.revision LIMIT ?")) {
            s.setString(1,tenant);s.setString(2,id);s.setLong(3,after);s.setInt(4,limit+1);s.setQueryTimeout(5);
            ObjectNode page=mapper.createObjectNode();ArrayNode items=page.putArray("items");
            try(var r=s.executeQuery()) {
                while(r.next()) {if(items.size()==limit){page.put("next",items.get(items.size()-1).path("revision").asLong());break;}
                    items.addObject().put("revision",r.getLong(1)).put("version",r.getInt(2)).put("status",r.getString(3))
                        .put("actor",r.getString(4)).put("reason",r.getString(5)).put("createdAt",r.getTimestamp(6).toInstant().toString()).put("checksum",r.getString(7));}
            }
            if(!page.has("next"))page.putNull("next");return page.toString();
        }catch(SQLException e){throw unavailable();}
    }
    @Override public String explain(String tenant,String processingId) {
        try(var c=dataSource.getConnection();var s=c.prepareStatement("SELECT evidence::text FROM event_processor.processing_record WHERE tenant=? AND processing_id=?")) {
            s.setString(1,tenant);s.setString(2,processingId);s.setQueryTimeout(5);
            try(var r=s.executeQuery()){if(!r.next())throw missing();return r.getString(1);}
        }catch(SQLException e){throw unavailable();}
    }
    @Override public void rejected(AdminActor actor,String requestId,String action,String resource) {
        try(var c=dataSource.getConnection()){audit(c,actor,requestId,action,resource,null,null,"REJECTED");}
        catch(SQLException e){throw unavailable();}
    }
    private void recordRejected(Mutation m) {
        try(var c=dataSource.getConnection()){audit(c,m.actor(),m.requestId(),m.change().name(),m.ruleId(),m.expectedRevision(),null,"REJECTED");}
        catch(SQLException e){throw unavailable();}
    }
    private ObjectNode summary(ResultSet r)throws SQLException {
        var result=mapper.createObjectNode().put("id",r.getString(1)).put("latestVersion",r.getInt(2));
        if(r.getObject(3)==null)result.putNull("activeVersion");else result.put("activeVersion",r.getInt(3));
        return result.put("status",r.getString(4)).put("revision",r.getLong(5));
    }
    private void audit(Connection c,AdminActor actor,String requestId,String action,String resource,Long previous,Long next,String outcome)throws SQLException {
        try(var s=c.prepareStatement("INSERT INTO event_processor.admin_audit(tenant,actor,request_id,action,resource,previous_revision,new_revision,outcome) VALUES (?,?,?,?,?,?,?,?)")) {
            s.setString(1,actor.tenant());s.setString(2,actor.subject());s.setString(3,requestId);s.setString(4,action);s.setString(5,resource);
            if(previous==null)s.setNull(6,Types.BIGINT);else s.setLong(6,previous);if(next==null)s.setNull(7,Types.BIGINT);else s.setLong(7,next);
            s.setString(8,outcome);s.executeUpdate();
        }
    }
    private static AdminFailure missing(){return new AdminFailure(AdminFailure.Kind.NOT_FOUND,"RESOURCE_NOT_FOUND");}
    private static AdminFailure unavailable(){return new AdminFailure(AdminFailure.Kind.UNAVAILABLE,"ADMIN_STORE_UNAVAILABLE");}
    private static AdminFailure mapped(String code) {
        if("RULE_NOT_FOUND".equals(code)||"VERSION_NOT_FOUND".equals(code))return missing();
        if(java.util.Set.of("REVISION_CONFLICT","NEXT_VERSION_REQUIRED","RULE_RETIRED","ACTIVE_VERSION_MISMATCH").contains(code))
            return new AdminFailure(AdminFailure.Kind.CONFLICT,code);
        return new AdminFailure(AdminFailure.Kind.INVALID,"INVALID_RULE_TRANSITION");
    }
}
