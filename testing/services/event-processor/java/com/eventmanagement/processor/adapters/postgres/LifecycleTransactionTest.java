package com.eventmanagement.processor.adapters.postgres;
import com.eventmanagement.processor.adapters.kafka.WorkerCommandAdapter;
import com.eventmanagement.processor.domain.*;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.*;
import java.sql.Connection;
import java.time.Instant;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
class LifecycleTransactionTest {
    static final ObjectMapper mapper=new ObjectMapper().findAndRegisterModules();
    Connection c;PostgresLifecycleSession session;String tenant,cycle,processing;Event event;
    @BeforeAll static void db()throws Exception {CorrelationTransactionTest.database();}
    @AfterAll static void cleanup()throws Exception {CorrelationTransactionTest.cleanup();}
    @BeforeEach void setup()throws Exception {
        tenant="lc-"+UUID.randomUUID();cycle=UUID.randomUUID().toString();processing=StableIdentity.of("test",cycle);
        event=new Event("source", "source-key-"+cycle,tenant,Event.Status.PROBLEM,5,Instant.now(),"{}");
        c=CorrelationTransactionTest.ds.getConnection();c.setAutoCommit(false);session=new PostgresLifecycleSession(c,mapper,tenant);
        try(var s=c.prepareStatement("INSERT INTO event_processor.processing_record(processing_id,input_hash,event_id,tenant,evidence) VALUES (?,?,?,?, '{}'::jsonb)")) {
            s.setString(1,processing);s.setString(2,processing);s.setString(3,event.eventId());s.setString(4,tenant);s.executeUpdate();
        }
        var p=mapper.createObjectNode().put("resource","node").put("summary","fatal").put("severity",5);
        p.putObject("lifecycle").put("notificationGroup","test").put("originalAssignmentGroup","human").put("holdingAssignmentGroup","robot").put("resolvedState","resolved-test").put("closeCode","recovered");
        var e=new Event(cycle,"correlation:"+cycle,tenant,Event.Status.PROBLEM,5,Instant.now(),"{}");
        var cmd=new WorkerCommandAdapter(mapper).encode(e,processing,cycle,"default","SERVICENOW","CREATE_TICKET",p,Instant.now());
        session.register(event,cmd);c.commit();
    }
    @AfterEach void close()throws Exception {if(c!=null){c.rollback();c.close();}}
    ObjectNode state()throws Exception {try(var s=c.prepareStatement("SELECT document::text FROM event_processor.lifecycle WHERE tenant=? AND cycle_id=?")){s.setString(1,tenant);s.setString(2,cycle);try(var r=s.executeQuery()){assertTrue(r.next());return (ObjectNode)mapper.readTree(r.getString(1));}}}
    ObjectNode result(String step,String provider,String operation)throws Exception {
        return mapper.createObjectNode().put("resultId",UUID.randomUUID().toString()).put("commandId",state().path("commands").path(step).asText()).put("eventId",cycle).put("eventKey","correlation:"+cycle).put("tenant",tenant).put("integrationType",provider).put("operation",operation).put("status","SUCCESS");
    }
    ObjectNode ticket()throws Exception{return result("ticket","SERVICENOW","CREATE_TICKET").put("ticketNumber","INC-DYNAMIC").put("externalSystemId","sys-dynamic");}
    void open()throws Exception {var r=result("open","GNM","SEND_NOTIFICATION");r.putObject("providerNotificationIdentity").put("incidentId","incident-dynamic").put("lifecycleState","OPEN_CONFIRMED");session.result(r);}
    void recovery(boolean resolved,boolean suppressed)throws Exception {
        var decision=mapper.createObjectNode().put("directive",suppressed?"SUPPRESS_INTEGRATIONS":"CONTINUE");
        decision.putObject("correlation").putArray("decisions").addObject().put("changed",true).putObject("group").put("groupId",cycle).put("resolved",resolved);
        session.observe(new Event("recovery",event.eventKey(),tenant,Event.Status.OK,0,Instant.now(),"{}"),decision);
    }
    @Test void confirmedDependenciesAndDynamicTicketSurviveReplayAndCommit()throws Exception {
        var t=ticket();session.result(t);c.commit();session.result(t);
        assertTrue(state().path("commands").has("open"));assertFalse(state().path("commands").has("automation"));
        open();c.commit();assertTrue(state().path("commands").has("automation"));
        try(var s=c.prepareStatement("SELECT envelope::text FROM event_processor.integration_command WHERE tenant=?")){s.setString(1,tenant);try(var r=s.executeQuery()){int n=0;while(r.next()){assertTrue(r.getString(1).contains("INC-DYNAMIC"));n++;}assertEquals(2,n);}}
        String execution=state().path("executionId").asText();var a=result("automation","CACF","AUTOMATION_REQUESTED").put("state","COMPLETED").put("outcome","REMEDIATED").put("executionId",execution);session.result(a);
        assertFalse(state().path("recovered").asBoolean());assertFalse(state().path("commands").has("close"));
        recovery(true,false);assertEquals("RECOVERED_AUTOMATION_PENDING",state().path("state").asText());
        var note=result("note","SERVICENOW","APPLY_AUTOMATION_RESULT").put("commandId",execution+"-result").put("ticketNumber","INC-DYNAMIC");session.result(note);
        assertTrue(state().path("commands").has("close"));assertFalse(state().path("commands").has("resolve"));
    }
    @Test void earlyClearDoesNotLaunchObsoleteAutomation()throws Exception {
        recovery(true,false);session.result(ticket());assertTrue(state().path("commands").has("resolve"));assertFalse(state().path("commands").has("open"));
    }
    @Test void clearWhileGnmPendingWaitsForConfirmationThenCloses()throws Exception {
        session.result(ticket());recovery(true,false);assertFalse(state().path("commands").has("resolve"));open();
        assertTrue(state().path("commands").has("close"));assertFalse(state().path("commands").has("automation"));
    }
    @Test void activeMemberAndBlackoutDoNotCloseSharedIntegrations()throws Exception {
        session.result(ticket());recovery(false,false);assertFalse(state().path("commands").has("resolve"));
        recovery(true,true);open();assertFalse(state().path("commands").has("close"));assertFalse(state().path("commands").has("automation"));
        recovery(true,false);assertTrue(state().path("commands").has("close"));
    }
    @Test void failureAndUnknownNeverBecomeSuccess()throws Exception {
        session.result(ticket().put("status","FAILED"));assertEquals("REVIEW",state().path("state").asText());assertFalse(state().path("commands").has("open"));
        var a=mapper.createObjectNode().put("status","SUCCESS").put("state","COMPLETED").put("outcome","UNKNOWN");
        assertFalse(PostgresLifecycleSession.confirmed("automation",a,state()));
    }
    @Test void rollbackCannotLoseNextIntent()throws Exception {
        session.result(ticket());assertTrue(state().path("commands").has("open"));c.rollback();
        assertFalse(state().path("commands").has("open"));session.result(ticket());c.commit();assertTrue(state().path("commands").has("open"));
    }
    @Test void identityCollisionIsAuditedAndDoesNotEmitCommand()throws Exception {
        var t=ticket().put("eventKey","wrong-key");session.result(t);assertEquals("REVIEW",state().path("state").asText());assertFalse(state().path("commands").has("open"));
    }
}
