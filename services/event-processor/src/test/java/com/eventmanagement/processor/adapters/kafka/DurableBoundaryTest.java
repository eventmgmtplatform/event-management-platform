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
            assertTrue(h.contains("INVALID_GATEWAY_CONTRACT"));calls.add("dlq");return true;
        };
        var processor=new AcceptedEventProcessor(new GatewayEventAdapter(mapper),store,mapper,com.eventmanagement.processor.application.EventProcessingPipeline.foundation());
        processor.dlqTopic="events.dlq";
        var exchange=new DefaultExchange(new DefaultCamelContext());exchange.getMessage().setBody("secret-input");
        exchange.getMessage().setHeader(KafkaConstants.MANUAL_COMMIT,(KafkaManualCommit)()->calls.add("ack"));
        processor.process(exchange);assertEquals(List.of("dlq","ack"),calls);
    }
}
