package com.eventmanagement.processor.adapters.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.eventmanagement.processor.ports.out.ProcessingStore;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.support.DefaultExchange;
import org.apache.camel.component.kafka.KafkaConstants;
import org.apache.camel.component.kafka.consumer.KafkaManualCommit;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class DurableBoundaryTest {
    @Test void durableCommitPrecedesKafkaAckAndNoEarlyAckOnFailure() throws Exception {
        for(boolean fail:List.of(false,true)) {
            var calls=new ArrayList<String>();
            ProcessingStore store=(a,b,c,d,e,f,g,h)->{calls.add("database");if(fail)throw new java.sql.SQLException();return true;};
            var exchange=new DefaultExchange(new DefaultCamelContext());
            exchange.getMessage().setBody(GatewayAdapterTest.OPEN);
            exchange.getMessage().setHeader(KafkaConstants.MANUAL_COMMIT,(KafkaManualCommit)()->calls.add("ack"));
            var mapper=new ObjectMapper();var processor=new AcceptedEventProcessor(new GatewayEventAdapter(mapper),store,mapper,com.eventmanagement.processor.application.EventProcessingPipeline.foundation());
            processor.outputTopic="events.normalized";processor.dlqTopic="events.dlq";
            if(fail)assertThrows(java.sql.SQLException.class,()->processor.process(exchange));else processor.process(exchange);
            assertEquals(fail?List.of("database"):List.of("database","ack"),calls);
        }
    }
    @Test void invalidContractIsDurablySanitizedBeforeAck() throws Exception {
        var calls=new ArrayList<String>();var mapper=new ObjectMapper();
        ProcessingStore store=(a,b,c,d,e,f,g,h)->{
            assertEquals("events.dlq",f);assertFalse(h.contains("secret-input"));
            assertTrue(h.contains("INVALID_GATEWAY_CONTRACT"));
            var failure=mapper.readTree(h);
            try(var schemaInput=getClass().getResourceAsStream("/contracts/events.dlq-v1.schema.json")) {
                var schema=com.networknt.schema.JsonSchemaFactory.getInstance(com.networknt.schema.SpecVersion.VersionFlag.V202012)
                        .getSchema(mapper.readTree(schemaInput));
                assertTrue(schema.validate(failure).isEmpty());
            }
            for(String field:List.of("schemaVersion","dlqId","eventId","failedAt","stage","error","source","originalEvent"))assertTrue(failure.has(field));
            assertTrue(failure.path("originalEvent").path("redacted").asBoolean());
            assertFalse(failure.path("error").path("retryable").asBoolean());
            calls.add("dlq");return true;
        };
        var processor=new AcceptedEventProcessor(new GatewayEventAdapter(mapper),store,mapper,com.eventmanagement.processor.application.EventProcessingPipeline.foundation());
        processor.dlqTopic="events.dlq";
        var exchange=new DefaultExchange(new DefaultCamelContext());exchange.getMessage().setBody("secret-input");
        exchange.getMessage().setHeader(KafkaConstants.MANUAL_COMMIT,(KafkaManualCommit)()->calls.add("ack"));
        processor.process(exchange);assertEquals(List.of("dlq","ack"),calls);
    }
    @Test void unavailableSnapshotNeverAcknowledgesOrPublishes() throws Exception {
        var mapper=new ObjectMapper();var calls=new ArrayList<String>();
        ProcessingStore store=(a,b,c,d,e,f,g,h)->{calls.add("store");return true;};
        var pipeline=com.eventmanagement.processor.application.EventProcessingPipeline.configured(tenant->{throw new IllegalStateException("RULE_SNAPSHOT_UNAVAILABLE");});
        var processor=new AcceptedEventProcessor(new GatewayEventAdapter(mapper),store,mapper,pipeline);
        var exchange=new DefaultExchange(new DefaultCamelContext());exchange.getMessage().setBody(GatewayAdapterTest.OPEN);
        exchange.getMessage().setHeader(KafkaConstants.MANUAL_COMMIT,(KafkaManualCommit)()->calls.add("ack"));
        assertThrows(IllegalStateException.class,()->processor.process(exchange));assertTrue(calls.isEmpty());
    }
    @Test void ruleInputFailureIsSanitizedDurableAndStillAudited() throws Exception {
        var mapper=new ObjectMapper();var calls=new ArrayList<String>();
        var compiler=new com.eventmanagement.processor.adapters.rules.RuleCompiler();
        String definition=com.eventmanagement.processor.adapters.rules.RuleCompilerTest.definition("limit",1,1,
                com.eventmanagement.processor.adapters.rules.RuleCompilerTest.leaf("event.identifier","REGEX","\"a+\""),"STATE_ONLY");
        var rule=compiler.compile(definition).rule();
        var pipeline=com.eventmanagement.processor.application.EventProcessingPipeline.configured(tenant->
                new com.eventmanagement.processor.domain.rules.RuleSnapshot(tenant,List.of(rule)));
        ProcessingStore store=(a,b,c,d,e,f,g,h)->{
            assertEquals("events.dlq",f);var json=mapper.readTree(h);
            assertEquals("RULE_EVALUATION_FAILED",json.path("error").path("code").asText());
            assertTrue(json.path("originalEvent").path("redacted").asBoolean());
            assertEquals("SUCCESS",json.path("stages").get(11).path("status").asText());
            assertFalse(h.contains("private-summary"));calls.add("store");return true;
        };
        var processor=new AcceptedEventProcessor(new GatewayEventAdapter(mapper),store,mapper,pipeline);processor.dlqTopic="events.dlq";
        var body=(com.fasterxml.jackson.databind.node.ObjectNode)mapper.readTree(GatewayAdapterTest.OPEN);
        body.put("eventId","a".repeat(4097));body.put("summary","private-summary");
        var exchange=new DefaultExchange(new DefaultCamelContext());exchange.getMessage().setBody(body.toString());
        exchange.getMessage().setHeader(KafkaConstants.MANUAL_COMMIT,(KafkaManualCommit)()->calls.add("ack"));
        processor.process(exchange);assertEquals(List.of("store","ack"),calls);
    }
}
