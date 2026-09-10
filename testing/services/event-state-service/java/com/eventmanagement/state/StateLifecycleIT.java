package com.eventmanagement.state;

import com.fasterxml.jackson.databind.node.ObjectNode;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.common.QuarkusTestResource;
import jakarta.inject.Inject;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import java.util.UUID;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;

@QuarkusTest
@QuarkusTestResource(value=StateTestEnvironment.class,restrictToAnnotatedClass=true)
class StateLifecycleIT {
    @Inject StateTransitionRepository transitions;
    @Inject EventStateRepository integrations;
    @Inject DataSource dataSource;

    ObjectNode fixture() throws Exception {
        String id=UUID.randomUUID().toString();
        return StateRequestTest.fixture().put("messageId",id).put("eventId",id).put("eventKey","life:"+id);
    }
    ObjectNode next(ObjectNode base,String transition,int second) {
        return base.deepCopy().put("messageId",UUID.randomUUID().toString()).put("eventId",UUID.randomUUID().toString())
                .put("transition",transition).put("effectiveSeverity",transition.equals("CLOSE")?0:3)
                .put("occurredAt","2026-09-10T00:00:"+String.format("%02d",second)+"Z");
    }
    StateTransitionRepository.Applied apply(ObjectNode json) throws Exception { return transitions.apply(StateRequest.parse(json)); }
    long count(String table,String key) throws Exception {
        try(var c=dataSource.getConnection();var s=c.prepareStatement("SELECT count(*) FROM event_management."+table+" WHERE event_key=?")) {
            s.setString(1,key);try(var r=s.executeQuery()){r.next();return r.getLong(1);}
        }
    }
    @Test void openRepeatCloseReopenPreservesIdentityAndHistory() throws Exception {
        var open=fixture();var first=apply(open).state();assertEquals("OPEN",first.lifecycleStatus);assertEquals(1,first.tally);
        var repeat=apply(next(open,"OPEN",1)).state();assertEquals(2,repeat.tally);assertEquals(2,repeat.version);
        var close=apply(next(open,"CLOSE",2).put("sourceSeverity",0)).state();
        assertEquals("CLOSED",close.lifecycleStatus);assertEquals(0,close.effectiveSeverity);assertEquals(3,close.sourceSeverity);
        assertEquals(2,close.tally);assertEquals(first.eventId,close.eventId);
        var reopened=apply(next(open,"OPEN",3)).state();assertEquals("OPEN",reopened.lifecycleStatus);
        assertEquals(3,reopened.tally);assertEquals(4,reopened.version);assertEquals(first.eventId,reopened.eventId);
        assertEquals(4,count("ess_event_transition",first.eventKey));
        try(var c=dataSource.getConnection();var s=c.prepareStatement("SELECT transition_type FROM event_management.ess_event_transition WHERE event_key=? ORDER BY aggregate_version DESC LIMIT 1")) {
            s.setString(1,first.eventKey);try(var r=s.executeQuery()){assertTrue(r.next());assertEquals("REOPEN",r.getString(1));}
        }
    }
    @Test void replayAndCollisionDoNotDuplicateTransition() throws Exception {
        var open=fixture();var first=apply(open).state();
        assertEquals("DUPLICATE",apply(open).disposition());assertEquals(1,apply(open).state().version);
        assertThrows(RejectedIntegrationResult.class,()->apply(open.deepCopy().put("sourceSeverity",4)));
        assertEquals(1,count("ess_state_request",first.eventKey));assertEquals(1,count("ess_event_transition",first.eventKey));
    }
    @Test void staleOpenCannotUndoCloseAndTimestampTiesAreStale() throws Exception {
        var open=fixture();apply(open);apply(next(open,"CLOSE",3));
        var stale=apply(next(open,"OPEN",2));assertEquals("STALE",stale.disposition());assertEquals("CLOSED",stale.state().lifecycleStatus);
        assertEquals("STALE",apply(next(open,"OPEN",3)).disposition());
        assertEquals(2,apply(open).state().version);assertEquals(2,count("ess_event_transition",open.path("eventKey").asText()));
    }
    @Test void closeBeforeOpenCreatesClosedStateAndRejectsOlderProblem() throws Exception {
        var open=fixture();var closed=apply(next(open,"CLOSE",3)).state();
        assertEquals("CLOSED",closed.lifecycleStatus);assertEquals(0,closed.tally);
        assertEquals("STALE",apply(open).disposition());assertEquals(1,apply(open).state().version);
    }
    @Test void integrationResultCannotReopenClosedLifecycleOrReplaceIdentity() throws Exception {
        var open=fixture();var initial=apply(open).state();apply(next(open,"CLOSE",1));
        var result=IntegrationResultContractTest.fixture().put("eventKey",initial.eventKey).put("tenant",initial.tenant)
                .put("eventId","new-provider-event").put("resultId",UUID.randomUUID().toString());
        var state=integrations.consolidate(result);assertEquals("CLOSED",state.lifecycleStatus);assertEquals(initial.eventId,state.eventId);
        assertEquals(0,state.effectiveSeverity);assertEquals(3,state.version);assertEquals("INC-ESS-TEST",state.ticketNumber);
    }
    @Test void tenantCollisionRollsBackRequestLedger() throws Exception {
        var open=fixture();apply(open);var other=next(open,"CLOSE",2).put("tenantId","other");
        assertThrows(RejectedIntegrationResult.class,()->apply(other));assertEquals(1,count("ess_state_request",open.path("eventKey").asText()));
    }
    @Test void concurrentOpenAndCloseEndAtLatestTimestamp() throws Exception {
        var open=fixture();var close=next(open,"CLOSE",1);
        try(var pool=Executors.newFixedThreadPool(2)) {
            var a=pool.submit(()->apply(open));var b=pool.submit(()->apply(close));a.get(15,TimeUnit.SECONDS);b.get(15,TimeUnit.SECONDS);
        }
        assertEquals("CLOSED",apply(close).state().lifecycleStatus);
    }
    @Test void historySqlFailureRollsBackStateAndRequest() throws Exception {
        var open=fixture();String constraint="test_"+UUID.randomUUID().toString().replace("-","");
        try(var c=dataSource.getConnection();var s=c.createStatement()) {
            s.execute("ALTER TABLE event_management.ess_event_transition ADD CONSTRAINT "+constraint+" CHECK (event_key <> '"+open.path("eventKey").asText()+"')");
        }
        try {
            assertThrows(Exception.class,()->apply(open));
            assertEquals(0,count("event_state",open.path("eventKey").asText()));assertEquals(0,count("ess_state_request",open.path("eventKey").asText()));
        } finally {
            try(var c=dataSource.getConnection();var s=c.createStatement()) {s.execute("ALTER TABLE event_management.ess_event_transition DROP CONSTRAINT "+constraint);}
        }
        assertEquals(1,apply(open).state().version);
    }
}
