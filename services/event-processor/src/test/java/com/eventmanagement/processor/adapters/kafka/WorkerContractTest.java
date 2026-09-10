package com.eventmanagement.processor.adapters.kafka;

import com.eventmanagement.processor.domain.Event;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import java.time.Instant;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class WorkerContractTest {
    final ObjectMapper mapper=new ObjectMapper();
    final WorkerCommandAdapter adapter=new WorkerCommandAdapter(mapper);
    Event event(String tenant) {return new Event("event","key",tenant,Event.Status.PROBLEM,3,Instant.EPOCH,"{}");}
    @Test void wireEnvelopePreservesWorkerRequiredFieldsAndPayload() throws Exception {
        var payload=mapper.createObjectNode().put("summary","test");
        var command=adapter.encode(event("tenant"),"processing","cycle","target","SERVICENOW","CREATE_TICKET",payload,Instant.EPOCH);
        try(var fixture=getClass().getResourceAsStream("/contracts/worker-command.json")) {
            assertEquals(mapper.readTree(fixture),command);
        }
        for(String required:List.of("commandId","eventId","eventKey","tenant","integrationType","operation"))
            assertTrue(command.path(required).isTextual()&&!command.path(required).asText().isBlank());
        assertTrue(command.path("payload").isObject());assertFalse(command.has("target"));assertFalse(command.has("integration"));
        payload.put("summary","changed");assertEquals("test",command.path("payload").path("summary").asText());
        assertEquals(command.path("commandId"),command.path("metadata").path("idempotencyKey"));
    }
    @Test void commandIdentityIsStableAcrossProcessingAttemptsAndSeparatesCyclesTargetsTenants() {
        var payload=mapper.createObjectNode();
        var original=adapter.encode(event("tenant"),"p1","cycle1","target","GNM","SEND_NOTIFICATION",payload,Instant.EPOCH);
        var replay=adapter.encode(event("tenant"),"p2","cycle1","target","GNM","SEND_NOTIFICATION",payload,Instant.now());
        assertEquals(original.path("commandId"),replay.path("commandId"));
        for(var other:List.of(
                adapter.encode(event("tenant"),"p1","cycle2","target","GNM","SEND_NOTIFICATION",payload,Instant.EPOCH),
                adapter.encode(event("tenant"),"p1","cycle1","other","GNM","SEND_NOTIFICATION",payload,Instant.EPOCH),
                adapter.encode(event("other"),"p1","cycle1","target","GNM","SEND_NOTIFICATION",payload,Instant.EPOCH),
                adapter.encode(event("tenant"),"p1","cycle1","target","GNM","CLOSE_NOTIFICATION",payload,Instant.EPOCH)))
            assertNotEquals(original.path("commandId"),other.path("commandId"));
    }
    @Test void groupCommandFixtureMatchesTheVersionedEnvelopeMapping()throws Exception {
        var compiler=new com.eventmanagement.processor.adapters.rules.RuleCompiler();
        var snapshot=new com.eventmanagement.processor.domain.rules.RuleSnapshot("tenant",List.of(
                compiler.compile(com.eventmanagement.processor.adapters.rules.CorrelationTest.definition("by-node",4)).rule(),
                compiler.compile(com.eventmanagement.processor.adapters.rules.RoutingTest.definition("route","by-node")).rule()));
        var event=com.eventmanagement.processor.adapters.rules.RoutingTest.event("event-a","a",0);
        var context=com.eventmanagement.processor.application.EventProcessingPipeline.configured(t->snapshot,java.time.Clock.fixed(Instant.EPOCH,java.time.ZoneOffset.UTC)).simulate(event);
        var intent=context.candidates().getFirst();var aggregate=new Event(intent.cycleId(),"correlation:"+intent.cycleId(),"tenant",event.status(),3,Instant.EPOCH,"{}");
        var envelope=adapter.encode(aggregate,context.processingId(),intent.cycleId(),intent.configuration(),intent.integrationType(),intent.operation(),mapper.valueToTree(intent.payload()),Instant.EPOCH);
        var metadata=(com.fasterxml.jackson.databind.node.ObjectNode)envelope.path("metadata");metadata.put("sourceEventId",event.eventId());metadata.put("sourceEventKey",event.eventKey());metadata.put("correlationGroupId",intent.cycleId());
        try(var fixture=getClass().getResourceAsStream("/contracts/processor-group-command.json")){assertEquals(mapper.readTree(fixture),envelope);}
    }
    @Test void unsupportedOperationsAndMissingLifecycleIdentityFailClosed() {
        assertThrows(IllegalArgumentException.class,()->adapter.encode(event("tenant"),"p","c","t","SERVICENOW","CLOSE_TICKET",mapper.createObjectNode(),Instant.EPOCH));
        assertThrows(IllegalArgumentException.class,()->adapter.encode(event("tenant"),"p","","t","GNM","SEND_NOTIFICATION",mapper.createObjectNode(),Instant.EPOCH));
    }
}
