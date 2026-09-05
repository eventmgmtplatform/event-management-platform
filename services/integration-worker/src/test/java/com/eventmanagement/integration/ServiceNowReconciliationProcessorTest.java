package com.eventmanagement.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.camel.Exchange;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.support.DefaultExchange;
import org.junit.jupiter.api.Test;

import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Deque;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ServiceNowReconciliationProcessorTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void shouldRecoverExistingTicketWithoutCreate()
            throws Exception {
        JsonNode ticket = objectMapper.readTree(
                """
                {"sys_id":"snow-001","number":"INC001"}
                """
        );
        StubLookup lookup = new StubLookup(
                new ServiceNowLookupClient.LookupResult(
                        ServiceNowLookupClient.Status.FOUND,
                        ticket
                )
        );
        Exchange exchange = exchange();

        new ServiceNowReconciliationProcessor(
                lookup,
                objectMapper,
                3,
                0
        ).process(exchange);

        assertEquals(
                "FOUND",
                exchange.getProperty(
                        ServiceNowReconciliationProcessor.OUTCOME_PROPERTY
                )
        );
        assertEquals(1, lookup.calls);
        assertEquals(
                "INC001",
                objectMapper.readTree(
                        exchange.getMessage().getBody(String.class)
                ).path("result").path("number").asText()
        );
    }

    @Test
    void shouldRequireAllNotFoundConfirmations()
            throws Exception {
        ServiceNowLookupClient.LookupResult notFound =
                new ServiceNowLookupClient.LookupResult(
                        ServiceNowLookupClient.Status.NOT_FOUND,
                        null
                );
        StubLookup lookup = new StubLookup(
                notFound,
                notFound,
                notFound
        );
        Exchange exchange = exchange();

        new ServiceNowReconciliationProcessor(
                lookup,
                objectMapper,
                3,
                0
        ).process(exchange);

        assertEquals(
                "NOT_FOUND_CONFIRMED",
                exchange.getProperty(
                        ServiceNowReconciliationProcessor.OUTCOME_PROPERTY
                )
        );
        assertEquals(3, lookup.calls);
    }

    @Test
    void shouldDeferWithoutCreateWhenLookupFails()
            throws Exception {
        ServiceNowLookupClient lookup = eventId -> {
            throw new IllegalStateException("lookup unavailable");
        };
        Exchange exchange = exchange();

        new ServiceNowReconciliationProcessor(
                lookup,
                objectMapper,
                3,
                0
        ).process(exchange);
        assertEquals(
                "RETRY_LATER",
                exchange.getProperty(
                        ServiceNowReconciliationProcessor.OUTCOME_PROPERTY
                )
        );
    }

    private Exchange exchange() {
        Exchange exchange = new DefaultExchange(new DefaultCamelContext());
        exchange.setProperty("commandId", "cmd-reconcile-001");
        exchange.setProperty("eventId", "evt-reconcile-001");
        return exchange;
    }

    private static final class StubLookup
            implements ServiceNowLookupClient {
        private final Deque<LookupResult> results;
        private int calls;

        private StubLookup(LookupResult... results) {
            this.results = new ArrayDeque<>(Arrays.asList(results));
        }

        @Override
        public LookupResult findByEventId(String eventId) {
            calls++;
            return results.removeFirst();
        }
    }
}
