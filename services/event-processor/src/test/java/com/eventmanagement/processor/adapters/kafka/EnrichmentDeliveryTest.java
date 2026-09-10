package com.eventmanagement.processor.adapters.kafka;
import com.eventmanagement.processor.adapters.rules.*;
import com.eventmanagement.processor.domain.rules.*;
import com.eventmanagement.processor.application.EventProcessingPipeline;
import com.fasterxml.jackson.databind.*;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.support.DefaultExchange;
import org.apache.camel.component.kafka.KafkaConstants;
import org.apache.camel.component.kafka.consumer.KafkaManualCommit;
import org.junit.jupiter.api.Test;
import java.time.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import static org.junit.jupiter.api.Assertions.*;

class EnrichmentDeliveryTest {
    @Test void normalizedOutputAndEvidenceContainTheSameCanonicalFacts()throws Exception {deliver(true);}
    @Test void requiredMissingInventoryUsesEnrichmentDlqStageAndCommitsOnlyAfterDurableAcceptance()throws Exception {deliver(false);}
    private void deliver(boolean found)throws Exception {
        var mapper=new ObjectMapper().findAndRegisterModules();var compiler=new RuleCompiler();
        var rules=new ArrayList<Rule>();rules.add(compiler.compile(EnrichmentTest.plan("lookup",true)).rule());
        if(found)rules.add(compiler.compile(EnrichmentTest.inventory("ci",1,"network")).rule());
        var snapshot=new RuleSnapshot("SDC",rules);var pipeline=EventProcessingPipeline.configured(t->snapshot,Clock.fixed(Instant.EPOCH,ZoneOffset.UTC));
        var accepted=new AtomicBoolean();var committed=new AtomicBoolean();
        var processor=new AcceptedEventProcessor(new GatewayEventAdapter(mapper),(id,hash,event,tenant,evidence,topic,key,output)->{
            var body=mapper.readTree(output);var audit=mapper.readTree(evidence);
            assertEquals(found?"events.normalized":"events.dlq",topic);
            if(found) {
                assertEquals("router-1",body.path("resource").path("name").asText());
                assertEquals("network",body.path("processing").path("enrichment").path("result").path("facts").path("assignment.group").asText());
                assertEquals(audit.path("enrichment"),body.path("processing").path("enrichment").path("result"));
            }else {
                assertEquals("ContextEnrichment",body.path("stage").asText());
                assertEquals("ENRICHMENT_REQUIRED_LOOKUP_FAILED",body.path("error").path("code").asText());
                assertTrue(body.path("originalEvent").path("redacted").asBoolean());
                assertEquals("SUCCESS",body.path("stages").get(11).path("status").asText());
            }
            assertFalse(committed.get());accepted.set(true);return true;
        },mapper,pipeline);
        processor.outputTopic="events.normalized";processor.dlqTopic="events.dlq";
        try(var camel=new DefaultCamelContext()) {
            var exchange=new DefaultExchange(camel);var event=(com.fasterxml.jackson.databind.node.ObjectNode)mapper.readTree(GatewayAdapterTest.OPEN);
            event.putObject("resource").put("name","router-1");exchange.getMessage().setBody(event.toString());
            exchange.getMessage().setHeader(KafkaConstants.MANUAL_COMMIT,(KafkaManualCommit)()->{assertTrue(accepted.get());committed.set(true);});
            processor.process(exchange);assertTrue(committed.get());
        }
    }
}
