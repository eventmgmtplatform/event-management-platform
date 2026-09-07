package com.eventmanagement.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.camel.Exchange;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.support.DefaultExchange;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class GnmDuplicateCloseReconciliationProcessorTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void reconcilesDuplicateCloseThroughAuthoritativeGet()
            throws Exception {

        GnmIncidentLookupClient lookup =
                (organizationId, incidentId) ->
                        new GnmIncidentSnapshot(
                                "2713046552385248",
                                "453003085618991",
                                "Closed",
                                1003,
                                "Close",
                                "End",
                                "2713733747119088"
                        );

        GnmDuplicateCloseReconciliationProcessor processor =
                new GnmDuplicateCloseReconciliationProcessor(
                        lookup,
                        mapper
                );

        Exchange exchange =
                new DefaultExchange(
                        new DefaultCamelContext()
                );

        exchange.setProperty(
                "commandId",
                "cmd-close-duplicate"
        );

        exchange.setProperty(
                "eventId",
                "evt-close-duplicate"
        );

        exchange.setProperty(
                "gnmOrganizationId",
                "453003085618991"
        );

        exchange.setProperty(
                "gnmIncidentId",
                "2713046552385248"
        );

        exchange.setProperty(
                "gnmCloseExecutionResult",
                new GnmRetryExecutor.ExecutionResult(
                        GnmRetryExecutor.Outcome.RECONCILIATION_REQUIRED,
                        1,
                        new GnmHttpInvoker.HttpResult(
                                400,
                                "{\"message\":\"Invalid Incident operation.\"}"
                        ),
                        null
                )
        );

        processor.process(exchange);

        JsonNode result =
                mapper.readTree(
                        exchange.getMessage()
                                .getBody(String.class)
                );

        assertEquals(
                "SUCCESS",
                result.path("status").asText()
        );

        assertEquals(
                "CLOSE_NOTIFICATION",
                result.path("operation").asText()
        );

        JsonNode identity =
                result.path(
                        "providerNotificationIdentity"
                );

        assertEquals(
                "2713046552385248",
                identity.path("incidentId").asText()
        );

        assertEquals(
                "Closed",
                identity.path("incidentStatus").asText()
        );

        assertEquals(
                "CLOSED_CONFIRMED",
                identity.path("lifecycleState").asText()
        );

        assertEquals(
                "2713733747119088",
                identity.path("closeNotificationId").asText()
        );
    }

    @Test
    void rejectsSuccessOutcomeWithoutPerformingReconciliation() {

        GnmIncidentLookupClient lookup =
                (organizationId, incidentId) -> {
                    fail("GET must not execute");
                    return null;
                };

        GnmDuplicateCloseReconciliationProcessor processor =
                new GnmDuplicateCloseReconciliationProcessor(
                        lookup,
                        mapper
                );

        Exchange exchange =
                new DefaultExchange(
                        new DefaultCamelContext()
                );

        exchange.setProperty(
                "gnmCloseExecutionResult",
                new GnmRetryExecutor.ExecutionResult(
                        GnmRetryExecutor.Outcome.SUCCESS,
                        1,
                        new GnmHttpInvoker.HttpResult(
                                200,
                                "{}"
                        ),
                        null
                )
        );

        assertThrows(
                IllegalStateException.class,
                () -> processor.process(exchange)
        );
    }
}
