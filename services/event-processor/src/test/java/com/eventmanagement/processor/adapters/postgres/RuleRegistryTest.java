package com.eventmanagement.processor.adapters.postgres;

import com.eventmanagement.processor.adapters.rules.RuleCompiler;
import com.eventmanagement.processor.adapters.rules.RuleCompilerTest;
import com.eventmanagement.processor.application.EventProcessingPipeline;
import com.eventmanagement.processor.domain.*;
import org.junit.jupiter.api.*;
import org.postgresql.ds.PGSimpleDataSource;
import java.nio.file.*;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;

class RuleRegistryTest {
    static PGSimpleDataSource admin,ds;
    static String database;
    PostgresRuleStore store;
    String tenant;
    @BeforeAll static void database() throws Exception {
        String url=System.getProperty("processor.test.jdbc.url");
        Assumptions.assumeTrue(url!=null,"Isolated PostgreSQL URL required");
        admin=source(url);
        try(var c=admin.getConnection();var s=c.createStatement();var r=s.executeQuery("SELECT current_database()")) {
            r.next();assertEquals("cacf_test",r.getString(1));
        }
        database="ep_rules_"+UUID.randomUUID().toString().replace("-","");
        try(var c=admin.getConnection();var s=c.createStatement()){s.execute("CREATE DATABASE "+database);}
        ds=source(url.substring(0,url.lastIndexOf('/')+1)+database);
        try(var c=ds.getConnection();var s=c.createStatement()) {
            for(String migration:List.of("009-event-processor.sql","010-processor-outbox-recovery.sql","011-processor-rule-registry.sql","011-processor-rule-registry.sql"))
                s.execute(Files.readString(Path.of("../../infrastructure/postgres/init",migration)).replace("\\set ON_ERROR_STOP on", ""));
        }
    }
    static PGSimpleDataSource source(String url) {
        var source=new PGSimpleDataSource();source.setURL(url);source.setUser("cacf_test");source.setPassword("cacf-test-only");return source;
    }
    @AfterAll static void cleanup() throws Exception {
        if(database!=null)try(var c=admin.getConnection();var s=c.createStatement()){s.execute("DROP DATABASE "+database);}
    }
    @BeforeEach void setup() {store=new PostgresRuleStore(ds,new RuleCompiler());tenant="t-"+UUID.randomUUID();}
    String rule(int version,String directive) {
        return RuleCompilerTest.definition("rule",version,10,RuleCompilerTest.leaf("event.severity","GTE","2.00"),directive);
    }
    long create(int version,String action) throws Exception {return store.createVersion(tenant,rule(version,action),"test-actor","test-reason");}
    @Test void explicitActivationReplacementDisableAndRollbackKeepHistory() throws Exception {
        assertEquals(1,create(1,"STATE_ONLY"));assertTrue(store.snapshot(tenant).rules().isEmpty());
        assertEquals(2,store.changeStatus(tenant,"rule",1,PostgresRuleStore.Status.ENABLED,1,"actor","activate"));
        var before=store.snapshot(tenant);assertEquals(1,before.rules().getFirst().version());
        assertEquals(3,create(2,"SUPPRESS_INTEGRATIONS"));assertEquals(before,store.snapshot(tenant));
        assertEquals(4,store.changeStatus(tenant,"rule",2,PostgresRuleStore.Status.ENABLED,3,"actor","replace"));
        var after=store.snapshot(tenant);assertEquals(2,after.rules().getFirst().version());
        assertNotEquals(before.checksum(),after.checksum());assertEquals(1,before.rules().getFirst().version());
        assertEquals(5,store.changeStatus(tenant,"rule",2,PostgresRuleStore.Status.DISABLED,4,"actor","disable"));
        assertTrue(store.snapshot(tenant).rules().isEmpty());
        assertEquals(6,store.changeStatus(tenant,"rule",1,PostgresRuleStore.Status.ENABLED,5,"actor","rollback"));
        assertEquals(before.checksum(),store.snapshot(tenant).checksum());
        assertEquals(7,store.changeStatus(tenant,"rule",1,PostgresRuleStore.Status.RETIRED,6,"actor","retire"));
        assertThrows(IllegalArgumentException.class,()->create(3,"CONTINUE"));
        assertTrue(store.snapshot(tenant).rules().isEmpty());
    }
    @Test void disablingCannotAuditAnUnrelatedVersion() throws Exception {
        create(1,"STATE_ONLY");store.changeStatus(tenant,"rule",1,PostgresRuleStore.Status.ENABLED,1,"a","b");
        create(2,"CONTINUE");
        assertThrows(IllegalArgumentException.class,()->store.changeStatus(tenant,"rule",2,PostgresRuleStore.Status.DISABLED,3,"a","b"));
        assertEquals(1,store.snapshot(tenant).rules().getFirst().version());
    }
    @Test void tenantIsolationAndOptimisticStatusConflict() throws Exception {
        create(1,"STATE_ONLY");
        assertTrue(store.snapshot("other").rules().isEmpty());
        assertThrows(IllegalArgumentException.class,()->store.changeStatus("other","rule",1,PostgresRuleStore.Status.ENABLED,1,"a","b"));
        try(var pool=Executors.newFixedThreadPool(2)) {
            Callable<Boolean> activate=()->{try{store.changeStatus(tenant,"rule",1,PostgresRuleStore.Status.ENABLED,1,"a","b");return true;}
                catch(IllegalArgumentException e){assertEquals("REVISION_CONFLICT",e.getMessage());return false;}};
            var a=pool.submit(activate);var b=pool.submit(activate);assertNotEquals(a.get(10,TimeUnit.SECONDS),b.get(10,TimeUnit.SECONDS));
        }
    }
    @Test void concurrentVersionWritersCannotOverwrite() throws Exception {
        try(var pool=Executors.newFixedThreadPool(2)) {
            Callable<Boolean> create=()->{try{create(1,"STATE_ONLY");return true;}catch(IllegalArgumentException e){assertEquals("NEXT_VERSION_REQUIRED",e.getMessage());return false;}};
            var a=pool.submit(create);var b=pool.submit(create);assertNotEquals(a.get(10,TimeUnit.SECONDS),b.get(10,TimeUnit.SECONDS));
        }
        try(var c=ds.getConnection();var s=c.prepareStatement("SELECT count(*) FROM event_processor.rule_version WHERE tenant=?")){
            s.setString(1,tenant);try(var r=s.executeQuery()){r.next();assertEquals(1,r.getInt(1));}
        }
    }
    @Test void invalidVersionsNeverPersistAndDisabledDefinitionsCannotActivate() throws Exception {
        assertThrows(IllegalArgumentException.class,()->store.createVersion(tenant,rule(1,"RUN_SCRIPT"),"a","b"));
        assertThrows(IllegalArgumentException.class,()->store.createVersion("",rule(1,"STATE_ONLY"),"a","b"));
        assertEquals(1,store.createVersion(tenant,rule(1,"STATE_ONLY").replace("\"enabled\":true","\"enabled\":false"),"a","b"));
        assertThrows(IllegalArgumentException.class,()->store.changeStatus(tenant,"rule",1,PostgresRuleStore.Status.ENABLED,1,"a","b"));
        assertTrue(store.snapshot(tenant).rules().isEmpty());
    }
    @Test void databaseRejectsHistoricalMutationAndDanglingActiveVersion() throws Exception {
        create(1,"STATE_ONLY");
        try(var c=ds.getConnection()) {
            for(String sql:List.of("UPDATE event_processor.rule_version SET checksum='bad' WHERE tenant=?",
                    "DELETE FROM event_processor.rule_version WHERE tenant=?", "DELETE FROM event_processor.rule_change WHERE tenant=?",
                    "UPDATE event_processor.rule_definition SET status='ENABLED',active_version=999 WHERE tenant=?")) {
                try(var s=c.prepareStatement(sql)){s.setString(1,tenant);assertThrows(java.sql.SQLException.class,s::executeUpdate);}
            }
        }
    }
    @Test void durableEvidenceUsesExactSnapshotAndReplayCannotRewriteIt() throws Exception {
        create(1,"STATE_ONLY");store.changeStatus(tenant,"rule",1,PostgresRuleStore.Status.ENABLED,1,"a","b");
        var event=new Event("id","key",tenant,Event.Status.PROBLEM,3,Instant.EPOCH,"{}");
        var context=EventProcessingPipeline.configured(store).process(event);
        var boundary=new PostgresProcessingStore(ds);
        String evidence="{\"snapshot\":\""+context.ruleSnapshot().checksum()+"\"}";
        assertTrue(boundary.accept(context.processingId(),"hash","id",tenant,evidence,"events.normalized","key","{}"));
        create(2,"CONTINUE");store.changeStatus(tenant,"rule",2,PostgresRuleStore.Status.ENABLED,3,"a","b");
        assertFalse(boundary.accept(context.processingId(),"hash","id",tenant,"{}","events.normalized","key","{}"));
        try(var c=ds.getConnection();var s=c.prepareStatement("SELECT evidence->>'snapshot' FROM event_processor.processing_record WHERE processing_id=?")){
            s.setString(1,context.processingId());try(var r=s.executeQuery()){r.next();assertEquals(context.ruleSnapshot().checksum(),r.getString(1));}
        }
        assertNotEquals(context.ruleSnapshot().checksum(),store.snapshot(tenant).checksum());
    }
}
