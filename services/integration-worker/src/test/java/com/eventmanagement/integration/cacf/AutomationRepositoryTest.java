package com.eventmanagement.integration.cacf;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.charset.StandardCharsets;
import java.sql.*;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import org.postgresql.ds.PGSimpleDataSource;
import static org.junit.jupiter.api.Assertions.*;

class AutomationRepositoryTest {
    private final ObjectMapper mapper=new ObjectMapper();
    private PGSimpleDataSource dataSource;
    private AutomationRepository repository;
    private UUID id;
    private ObjectNode request;
    @BeforeEach void setup() throws Exception {
        String url=System.getProperty("cacf.test.jdbc.url");
        Assumptions.assumeTrue(url!=null,"Run against the isolated cacf-certification PostgreSQL using cacf.test.jdbc.url");
        dataSource=new PGSimpleDataSource();dataSource.setURL(url);dataSource.setUser("cacf_test");dataSource.setPassword("cacf-test-only");
        try(Connection c=dataSource.getConnection();Statement s=c.createStatement();ResultSet r=s.executeQuery("SELECT current_database()")){r.next();assertEquals("cacf_test",r.getString(1));}
        repository=new AutomationRepository(dataSource,mapper);id=UUID.randomUUID();
        request=(ObjectNode)mapper.readTree(getClass().getResourceAsStream("/cacf/request.json"));
        request.put("executionId",id.toString());((ObjectNode)request.path("event")).put("sourceSerial",id.toString());
    }
    @AfterEach void cleanup() throws Exception {
        if(dataSource==null || id==null)return;
        try(Connection c=dataSource.getConnection()) {
            for(String table:List.of("automation_provider_message","automation_result","automation_outbox","automation_provider_dispatch","automation_execution"))
                try(PreparedStatement s=c.prepareStatement("DELETE FROM event_management."+table+" WHERE execution_id=?")){s.setObject(1,id);s.executeUpdate();}
        }
    }
    private void accept() throws Exception {repository.accept(AutomationRequest.parse(request,null,null,600));}
    private NextAdapter.Message message(String type,String status) {
        String xml="<ServiceIncident xmlns='"+NextAdapter.NS+"'><RequesterID>"+NextAdapter.requesterId(request)+"</RequesterID><ProviderID>"+id+"</ProviderID><Transaction><TransactionName>"+type+"</TransactionName></Transaction><WorkflowStatus>"+status+"</WorkflowStatus></ServiceIncident>";
        return NextAdapter.parse(xml.getBytes(StandardCharsets.UTF_8),10000);
    }
    private long count(String table,String extra) throws Exception {
        try(Connection c=dataSource.getConnection();PreparedStatement s=c.prepareStatement("SELECT count(*) FROM event_management."+table+" WHERE execution_id=? "+extra)) {
            s.setObject(1,id);try(ResultSet r=s.executeQuery()){r.next();return r.getLong(1);}
        }
    }
    @Test void duplicateAdmissionPersistsOnceAndRejectsCollision() throws Exception {
        accept();accept();assertEquals(1,count("automation_execution",""));assertEquals(1,count("automation_provider_dispatch",""));
        ((ObjectNode)request.path("event")).put("summary","changed");
        assertThrows(AutomationRepository.Conflict.class,this::accept);
    }
    @Test void duplicateAcknowledgementDoesNotResetDeadlineAndQueuesUpdateOnce() throws Exception {
        accept();repository.callback(message("Acknowledge_Create",""));var deadline=repository.get(id).path("deadline_at");
        repository.callback(message("Acknowledge_Create",""));
        assertEquals(deadline,repository.get(id).path("deadline_at"));assertEquals(1,count("automation_provider_message","AND duplicate"));
        assertEquals(1,count("automation_provider_dispatch","AND operation='TKTUPDATE'"));
    }
    @Test void concurrentTerminalCallbacksCreateOneResultAndOnePublicationIntent() throws Exception {
        accept();repository.callback(message("Acknowledge_Create",""));
        try(var pool=Executors.newFixedThreadPool(2)) {
            var a=pool.submit(()->{repository.callback(message("result","ESCALATE"));return null;});
            var b=pool.submit(()->{repository.callback(message("result","ESCALATE"));return null;});
            a.get(10,TimeUnit.SECONDS);b.get(10,TimeUnit.SECONDS);
        }
        assertEquals(1,count("automation_result",""));assertEquals(1,count("automation_outbox","AND event_type='AUTOMATION_COMPLETED'"));
        assertEquals("ESCALATED",repository.get(id).path("outcome").asText());
    }
    @Test void lateCallbackAfterWallClockDeadlineCannotRemediateTicket() throws Exception {
        accept();repository.callback(message("Acknowledge_Create",""));
        try(Connection c=dataSource.getConnection();PreparedStatement s=c.prepareStatement("UPDATE event_management.automation_execution SET deadline_at=now()-interval '1 second' WHERE execution_id=?")){s.setObject(1,id);s.executeUpdate();}
        repository.callback(message("result","RESOLVE"));
        assertEquals("TIMEOUT",repository.get(id).path("outcome").asText());assertEquals(1,count("automation_provider_message","AND late_result"));
        assertEquals(0,repository.expire(780));assertEquals(1,count("automation_result",""));
    }
    @Test void unknownResultCreatesReviewWithoutTerminalTicketMutation() throws Exception {
        accept();repository.callback(message("result","unexpected-value"));
        assertEquals("UNKNOWN",repository.get(id).path("outcome").asText());
        assertEquals(1,count("automation_result","AND requires_review"));assertEquals(0,count("automation_outbox","AND event_type='SERVICENOW_RESULT'"));
    }
    @Test void failedPublicationRollsBackAndSurvivesRepositoryRestart() throws Exception {
        accept();
        assertThrows(IllegalStateException.class,()->repository.publishOne((type,payload)->{throw new IllegalStateException("broker unavailable");}));
        assertEquals(1,count("automation_outbox","AND NOT published"));
        repository=new AutomationRepository(dataSource,mapper);
        assertTrue(repository.publishOne((type,payload)->assertEquals(id+"-holding",payload.path("commandId").asText())));
        assertEquals(0,count("automation_outbox","AND NOT published"));
    }
    @Test void providerIdentityConflictRollsBackAcknowledgement() throws Exception {
        accept();repository.callback(message("Acknowledge_Create",""));
        var original=message("result","RESOLVE");
        var conflicting=new NextAdapter.Message(original.requesterId(),"different-provider",original.transactionNumber(),original.transactionName(),original.status(),original.substatus(),original.estimatedWait(),original.raw());
        assertThrows(AutomationRepository.Conflict.class,()->repository.callback(conflicting));
        assertEquals(0,count("automation_result",""));
    }
}
