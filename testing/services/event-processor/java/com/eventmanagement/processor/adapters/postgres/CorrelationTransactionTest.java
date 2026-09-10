package com.eventmanagement.processor.adapters.postgres;
import com.eventmanagement.processor.adapters.rules.*;
import com.eventmanagement.processor.application.*;
import com.eventmanagement.processor.domain.*;
import com.fasterxml.jackson.databind.*;
import org.junit.jupiter.api.*;
import org.postgresql.ds.PGSimpleDataSource;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;
class CorrelationTransactionTest {
    static PGSimpleDataSource admin,ds;static String database;
    final ObjectMapper mapper=new ObjectMapper().findAndRegisterModules();PostgresProcessingUnitOfWork uow;String tenant;
    @BeforeAll static void database()throws Exception {
        String url=System.getProperty("processor.test.jdbc.url");Assumptions.assumeTrue(url!=null,"Isolated database required");
        assertEquals("jdbc:postgresql://127.0.0.1:15439/cacf_test",url);admin=source(url);
        database="ep_correlation_"+UUID.randomUUID().toString().replace("-","");
        try(var c=admin.getConnection();var s=c.createStatement()){s.execute("CREATE DATABASE "+database);}
        ds=source(url.substring(0,url.lastIndexOf('/')+1)+database);
        try(var c=ds.getConnection();var s=c.createStatement()) {
            for(String file:List.of("009-event-processor.sql","010-processor-outbox-recovery.sql","011-processor-rule-registry.sql","013-processor-correlation.sql","013-processor-correlation.sql","014-processor-command-ledger.sql","020-processor-lifecycle.sql"))
                s.execute(Files.readString(Path.of("../../infrastructure/postgres/init",file)).replace("\\set ON_ERROR_STOP on",""));
        }
    }
    static PGSimpleDataSource source(String url){var ds=new PGSimpleDataSource();ds.setURL(url);ds.setUser("cacf_test");ds.setPassword("cacf-test-only");return ds;}
    @AfterAll static void cleanup()throws Exception {if(database!=null)try(var c=admin.getConnection();var s=c.createStatement()){s.execute("DROP DATABASE "+database);}}
    @BeforeEach void prepare()throws Exception {
        tenant="tenant-"+UUID.randomUUID();var rules=new PostgresRuleStore(ds,new RuleCompiler());
        rules.createVersion(tenant,CorrelationTest.definition("group",4),"test","test");rules.changeStatus(tenant,"group",1,PostgresRuleStore.Status.ENABLED,1,"test","test");
        uow=new PostgresProcessingUnitOfWork(ds,mapper,rules);
    }
    Event event(String id,String key,long seconds){var e=CorrelationTest.event(id,key,Event.Status.PROBLEM,seconds);return new Event(e.eventId(),e.eventKey(),tenant,e.status(),e.severity(),e.receivedAt(),e.originalJson(),e.selectors());}
    void process(Event event,boolean fail)throws Exception {
        String hash=StableIdentity.of("payload-v1",event.originalJson());
        uow.execute(event,hash,(store,correlation,snapshots,commands)->{
            var context=EventProcessingPipeline.configured(snapshots).usingCorrelation(correlation).process(event);
            correlation.apply(tenant,context.correlation());if(fail)throw new IllegalStateException("INJECTED_FAILURE");
            store.accept(context.processingId(),hash,event.eventId(),tenant,mapper.writeValueAsString(context.correlation()),"events.normalized",event.eventKey(),"{}");
        });
    }
    @Test void concurrentEventsAndReplayHaveOneAtomicRelationshipHistory()throws Exception {
        var a=event("a","a",0);var b=event("b","b",0);
        try(var pool=Executors.newFixedThreadPool(2)) {
            var start=new CountDownLatch(1);
            var one=pool.submit(()->{start.await();process(a,false);return true;});var two=pool.submit(()->{start.await();process(b,false);return true;});start.countDown();one.get(15,TimeUnit.SECONDS);two.get(15,TimeUnit.SECONDS);
        }
        process(a,false);
        try(var c=ds.getConnection();var s=c.prepareStatement("SELECT revision,jsonb_array_length(document->'members') FROM event_processor.correlation_group WHERE tenant=?")) {
            s.setString(1,tenant);try(var r=s.executeQuery()){assertTrue(r.next());assertEquals(2,r.getInt(1));assertEquals(2,r.getInt(2));}
        }
        assertEquals(2,count("processing_record"));assertEquals(2,count("output_outbox"));
    }
    @Test void rollbackRemovesRelationshipAndOutputTogether()throws Exception {
        var event=event("a","a",0);assertThrows(IllegalStateException.class,()->process(event,true));
        assertEquals(0,count("correlation_group"));assertEquals(0,count("processing_record"));assertEquals(0,count("output_outbox"));
        process(event,false);assertEquals(1,count("correlation_group"));assertEquals(1,count("processing_record"));assertEquals(1,count("output_outbox"));
    }
    @Test void collisionDoesNotReevaluateOrChangeRelationship()throws Exception {
        var event=event("a","a",0);process(event,false);
        assertThrows(IllegalArgumentException.class,()->uow.execute(event,"other-hash",(s,p,r,commands)->fail("Must not reevaluate")));
        assertEquals(1,count("correlation_group"));assertEquals(1,count("processing_record"));
    }
    void enableRouting()throws Exception {
        var rules=new PostgresRuleStore(ds,new RuleCompiler());rules.createVersion(tenant,RoutingTest.definition("route","group"),"test","test");
        rules.changeStatus(tenant,"route",1,PostgresRuleStore.Status.ENABLED,1,"test","test");
    }
    void deliver(Event event,boolean fail)throws Exception {
        var enriched=new Event(event.eventId(),event.eventKey(),tenant,event.status(),event.severity(),event.receivedAt(),event.originalJson(),Map.of("node","router-1","summary","Summary "+event.eventId()));
        String hash=StableIdentity.of("payload-v1",enriched.originalJson());
        uow.execute(enriched,hash,(store,correlation,snapshots,commands)->{
            var context=EventProcessingPipeline.configured(snapshots).usingCorrelation(correlation).usingCommands(commands).process(enriched);
            correlation.apply(tenant,context.correlation());
            store.accept(context.processingId(),hash,enriched.eventId(),tenant,mapper.writeValueAsString(context.correlation()),"events.normalized",enriched.eventKey(),"{}");
            commands.enqueue(enriched,context.processingId(),context.evaluatedAt(),context.candidates());
            if(fail)throw new IllegalStateException("FAIL_AFTER_COMMAND_WRITE");
        });
    }
    String envelope()throws Exception {
        try(var c=ds.getConnection();var s=c.prepareStatement("SELECT envelope::text FROM event_processor.integration_command WHERE tenant=?")) {
            s.setString(1,tenant);try(var r=s.executeQuery()){assertTrue(r.next());return r.getString(1);}
        }
    }
    @Test void commandsRelationshipsAuditAndOutboxRollbackTogether()throws Exception {
        enableRouting();assertThrows(IllegalStateException.class,()->deliver(event("a","a",0),true));
        assertEquals(0,count("integration_command"));assertEquals(0,count("correlation_group"));assertEquals(0,count("output_outbox"));assertEquals(0,count("processing_record"));
        deliver(event("a","a",0),false);assertEquals(1,count("integration_command"));assertEquals(2,count("output_outbox"));
        var command=mapper.readTree(envelope());assertEquals("SERVICENOW",command.path("integrationType").asText());
        assertEquals("Summary a",command.path("payload").path("summary").asText());assertEquals("router-1",command.path("payload").path("resource").asText());
        assertEquals("a",command.path("metadata").path("sourceEventKey").asText());assertTrue(command.path("eventKey").asText().startsWith("correlation:"));
    }
    @Test void firstCommandEnvelopeSurvivesDifferentEventsAndDeliveredOutboxCleanup()throws Exception {
        enableRouting();deliver(event("a","a",0),false);String first=envelope();
        deliver(event("b","b",1),false);assertEquals(first,envelope());assertEquals(1,count("integration_command"));
        try(var c=ds.getConnection();var s=c.prepareStatement("DELETE FROM event_processor.output_outbox WHERE message_id IN (SELECT command_id FROM event_processor.integration_command WHERE tenant=?)")) {s.setString(1,tenant);s.executeUpdate();}
        deliver(event("c","c",2),false);assertEquals(first,envelope());assertEquals(1,count("integration_command"));assertEquals(3,count("output_outbox"));
    }
    @Test void durableCommandEnvelopeCannotBeOverwritten()throws Exception {
        enableRouting();deliver(event("a","a",0),false);String first=envelope();
        try(var c=ds.getConnection();var s=c.prepareStatement("UPDATE event_processor.integration_command SET envelope='{}'::jsonb WHERE tenant=?")) {
            s.setString(1,tenant);assertThrows(java.sql.SQLException.class,s::executeUpdate);
        }
        assertEquals(first,envelope());
    }
    long count(String table)throws Exception {
        String sql=table.equals("output_outbox")?"SELECT count(*) FROM event_processor.output_outbox o JOIN event_processor.processing_record p ON p.processing_id=o.processing_id WHERE p.tenant=?":"SELECT count(*) FROM event_processor."+table+" WHERE tenant=?";
        try(var c=ds.getConnection();var s=c.prepareStatement(sql)){s.setString(1,tenant);try(var r=s.executeQuery()){r.next();return r.getLong(1);}}
    }
    @Test void stateRequestSharesTheDecisionTransactionAndReplayBoundary() throws Exception {
        var raw=event("state-source","state-key",0);
        var input=new Event(raw.eventId(),raw.eventKey(),raw.tenant(),raw.status(),raw.severity(),raw.receivedAt(),"{}",raw.selectors());
        var work=(com.eventmanagement.processor.ports.out.ProcessingUnitOfWork.Work)(store,correlation,snapshots,commands)->{
            String id=StableIdentity.of("processing-v1",input.tenant(),input.eventId());
            store.accept(id,"state-hash",input.eventId(),input.tenant(),"{\"correlationApplied\":true,\"directive\":\"CONTINUE\"}","events.normalized",input.eventKey(),"{}");
        };
        assertThrows(IllegalStateException.class,()->uow.execute(input,"state-hash",(store,correlation,snapshots,commands)->{
            work.run(store,correlation,snapshots,commands);throw new IllegalStateException("AFTER_STATE_INTENT");
        }));
        assertEquals(0,count("output_outbox"));assertEquals(0,count("processing_record"));
        uow.execute(input,"state-hash",work);uow.execute(input,"state-hash",work);
        assertEquals(2,count("output_outbox"));assertEquals(1,count("processing_record"));
    }
}
