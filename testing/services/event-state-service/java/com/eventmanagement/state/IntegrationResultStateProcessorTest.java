package com.eventmanagement.state;

import com.fasterxml.jackson.databind.*;
import org.apache.camel.*;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.support.DefaultExchange;
import org.apache.camel.component.kafka.KafkaConstants;
import org.apache.camel.component.kafka.consumer.KafkaManualCommit;
import org.junit.jupiter.api.Test;
import java.lang.reflect.Proxy;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class IntegrationResultStateProcessorTest {
    void exercise(String failure, List<String> calls) throws Exception {
        var mapper = new ObjectMapper();
        var repository = new EventStateRepository(null, mapper) {
            @Override public ConsolidatedEventState consolidate(JsonNode result) throws Exception {
                calls.add("postgres");
                if (failure.equals("postgres")) throw new java.sql.SQLException("test failure");
                var state = new ConsolidatedEventState(); state.version = 1; return state;
            }
            @Override public void quarantine(String topic, int partition, long offset, String body, String reason) throws Exception {
                calls.add("quarantine");
                assertEquals("integration.results", topic); assertEquals(0, partition); assertEquals(10, offset);
                if (failure.equals("quarantineFailure")) throw new java.sql.SQLException("test failure");
            }
        };
        var search = new StateProjectionService() {
            @Override public void project(String eventKey) {
                calls.add("opensearch");
                if (failure.equals("opensearch")) throw new IllegalStateException("test failure");
            }
        };
        try (var context = new DefaultCamelContext()) {
            var exchange = new DefaultExchange(context);
            exchange.getMessage().setBody(IntegrationResultContractTest.fixture().toString());
            if (failure.startsWith("quarantine")) {
                exchange.getMessage().setBody("invalid-json");
                exchange.getMessage().setHeader(KafkaConstants.TOPIC, "integration.results");
                exchange.getMessage().setHeader(KafkaConstants.PARTITION, 0);
                exchange.getMessage().setHeader(KafkaConstants.OFFSET, 10L);
            }
            if (!failure.equals("missingCommit")) exchange.getMessage().setHeader(KafkaConstants.MANUAL_COMMIT,
                    Proxy.newProxyInstance(getClass().getClassLoader(), new Class[]{KafkaManualCommit.class},
                            (proxy, method, args) -> { calls.add("commit"); return null; }));
            new IntegrationResultStateProcessor(mapper, repository, search).process(exchange);
            if (failure.equals("quarantine")) assertEquals("QUARANTINED", exchange.getProperty("disposition"));
            else assertEquals(1L, exchange.getProperty("consolidatedVersion"));
        }
    }
    @Test void acknowledgesOnlyAfterBothStoresSucceed() throws Exception {
        var calls = new ArrayList<String>(); exercise("", calls);
        assertEquals(List.of("postgres", "opensearch", "commit"), calls);
    }
    @Test void postgresFailureNeverProjectsOrAcknowledges() {
        var calls = new ArrayList<String>(); assertThrows(Exception.class, () -> exercise("postgres", calls));
        assertEquals(List.of("postgres"), calls);
    }
    @Test void searchFailureNeverAcknowledges() {
        var calls = new ArrayList<String>(); assertThrows(Exception.class, () -> exercise("opensearch", calls));
        assertEquals(List.of("postgres", "opensearch"), calls);
    }
    @Test void missingManualCommitFailsBeforeMutation() {
        var calls = new ArrayList<String>(); assertThrows(Exception.class, () -> exercise("missingCommit", calls));
        assertTrue(calls.isEmpty());
    }
    @Test void invalidMessageAcknowledgesOnlyAfterDurableQuarantine() throws Exception {
        var calls = new ArrayList<String>(); exercise("quarantine", calls);
        assertEquals(List.of("quarantine", "commit"), calls);
    }
    @Test void failedQuarantineNeverAcknowledges() {
        var calls = new ArrayList<String>(); assertThrows(Exception.class, () -> exercise("quarantineFailure", calls));
        assertEquals(List.of("quarantine"), calls);
    }
}
