package com.eventmanagement.integration;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.camel.ProducerTemplate;
import org.junit.jupiter.api.*;
import org.postgresql.ds.PGSimpleDataSource;
import java.nio.file.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
class WorkerDeliveryRecoveryTest {
    static PGSimpleDataSource admin,ds;static String database;
    @BeforeAll static void setup()throws Exception {
        var url=System.getProperty("cacf.test.jdbc.url");Assumptions.assumeTrue(url!=null,"Isolated PostgreSQL required");
        assertEquals("jdbc:postgresql://127.0.0.1:15439/cacf_test",url);admin=source(url);
        database="worker_delivery_"+UUID.randomUUID().toString().replace("-","");
        try(var c=admin.getConnection();var s=c.createStatement()){s.execute("CREATE DATABASE "+database);}
        ds=source("jdbc:postgresql://127.0.0.1:15439/"+database);
        try(var c=ds.getConnection();var s=c.createStatement()) {
            s.execute("CREATE SCHEMA event_management");
            for(String name:List.of("004-integration-command-idempotency.sql","005-integration-command-recovery-lease.sql","007-integration-command-provider-checkpoint.sql","021-worker-delivery-recovery.sql","021-worker-delivery-recovery.sql"))
                s.execute(Files.readString(Path.of("../../infrastructure/postgres/init",name)).replace("\\set ON_ERROR_STOP on",""));
        }
    }
    static PGSimpleDataSource source(String url){var d=new PGSimpleDataSource();d.setURL(url);d.setUser("cacf_test");d.setPassword("cacf-test-only");return d;}
    @AfterAll static void cleanup()throws Exception {if(database!=null)try(var c=admin.getConnection();var s=c.createStatement()){s.execute("DROP DATABASE "+database);}}
    @Test void committedResultSurvivesPublisherFailureAndNewDispatcherInstance()throws Exception {
        var mapper=new ObjectMapper();String id=UUID.randomUUID().toString();
        var cmd=mapper.createObjectNode().put("commandId",id).put("eventId",id).put("eventKey",id).put("tenant","synthetic").put("integrationType","SERVICENOW").put("operation","CREATE_TICKET");cmd.putObject("payload").put("resource","synthetic");
        var ledger=new JdbcIntegrationCommandLedger(ds,mapper,60000);var claim=ledger.claim(cmd);
        ledger.complete(id,claim.claimOwner(),mapper.createObjectNode().put("resultId",id).put("commandId",id).toString());
        assertEquals(IntegrationCommandLedger.Decision.REPLAY,ledger.claim(cmd).decision());
        var deliveries=new ArrayList<String>();var fail=dispatcher(deliveries,true);assertThrows(IllegalStateException.class,fail::dispatch);
        assertTrue(pending(id));var resumed=dispatcher(deliveries,false);resumed.dispatch();assertFalse(pending(id));
        int count=deliveries.size();resumed.dispatch();assertEquals(count,deliveries.size());assertEquals(1,count);
    }
    boolean pending(String id)throws Exception {try(var c=ds.getConnection();var s=c.prepareStatement("SELECT result_published_at IS NULL FROM event_management.integration_command_execution WHERE command_id=?")){s.setString(1,id);try(var r=s.executeQuery()){assertTrue(r.next());return r.getBoolean(1);}}}
    WorkerDeliveryRecovery dispatcher(List<String> deliveries,boolean fail) {
        var d=new WorkerDeliveryRecovery();d.ds=ds;d.brokers="unused";d.commands="integration.commands";d.results="integration.results";
        d.producer=(ProducerTemplate)java.lang.reflect.Proxy.newProxyInstance(getClass().getClassLoader(),new Class[]{ProducerTemplate.class},(proxy,method,args)->{
            if(method.getName().equals("sendBodyAndHeader")){if(fail)throw new IllegalStateException("KAFKA_UNAVAILABLE");deliveries.add((String)args[1]);return null;}throw new AssertionError(method.getName());});return d;
    }
}
