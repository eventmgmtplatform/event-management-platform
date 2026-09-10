package com.eventmanagement.integration;

import static org.junit.jupiter.api.Assertions.*;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.camel.Exchange;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.support.DefaultExchange;
import org.junit.jupiter.api.Test;

class GnmCloseResultProcessorTest {

    private final ObjectMapper mapper =
            new ObjectMapper();

    private final GnmCloseResultProcessor processor =
            new GnmCloseResultProcessor(mapper);

    @Test
    void shouldBuildCloseAcceptedWithoutFabricatingPhaseIdentity()
            throws Exception {

        Exchange exchange = baseExchange();

        exchange.setProperty(
                "gnmCloseExecutionResult",
                new GnmRetryExecutor.ExecutionResult(
                        GnmRetryExecutor.Outcome.SUCCESS,
                        1,
                        new GnmHttpInvoker.HttpResult(
                                200,
                                """
                                {
                                  "status":200,
                                  "result":{
                                    "id":"2713046552385248"
                                  }
                                }
                                """
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

        JsonNode identity =
                result.path(
                        "providerNotificationIdentity"
                );

        assertEquals(
                "CLOSE_NOTIFICATION",
                result.path("operation").asText()
        );

        assertEquals(
                "2713046552385248",
                identity.path("incidentId").asText()
        );

        assertEquals(
                "CLOSE_ACCEPTED",
                identity.path("lifecycleState").asText()
        );

        assertFalse(
                identity.has("closeNotificationId")
        );

        assertFalse(
                identity.has("incidentStatus")
        );

        assertFalse(
                result.has("organizationId")
        );
    }

    @Test
    void shouldPreserveExistingOpenNotificationIdentity()
            throws Exception {

        Exchange exchange = baseExchange();

        exchange.setProperty(
                "gnmOpenNotificationId",
                "2713046552442022"
        );

        exchange.setProperty(
                "gnmCloseExecutionResult",
                new GnmRetryExecutor.ExecutionResult(
                        GnmRetryExecutor.Outcome.SUCCESS,
                        1,
                        new GnmHttpInvoker.HttpResult(
                                200,
                                """
                                {
                                  "status":200,
                                  "result":{
                                    "id":"2713046552385248"
                                  }
                                }
                                """
                        ),
                        null
                )
        );

        processor.process(exchange);

        JsonNode identity =
                mapper.readTree(
                        exchange.getMessage()
                                .getBody(String.class)
                ).path(
                        "providerNotificationIdentity"
                );

        assertEquals(
                "2713046552442022",
                identity.path(
                        "openNotificationId"
                ).asText()
        );

        assertFalse(
                identity.has("closeNotificationId")
        );
    }

    @Test
    void shouldRejectProviderIncidentMismatch()
            throws Exception {

        Exchange exchange = baseExchange();

        exchange.setProperty(
                "gnmCloseExecutionResult",
                new GnmRetryExecutor.ExecutionResult(
                        GnmRetryExecutor.Outcome.SUCCESS,
                        1,
                        new GnmHttpInvoker.HttpResult(
                                200,
                                """
                                {
                                  "status":200,
                                  "result":{
                                    "id":"different"
                                  }
                                }
                                """
                        ),
                        null
                )
        );

        assertThrows(
                IllegalStateException.class,
                () -> processor.process(exchange)
        );
    }

    private Exchange baseExchange() {

        Exchange exchange =
                new DefaultExchange(
                        new DefaultCamelContext()
                );

        exchange.setProperty(
                "commandId",
                "cmd-gnm-close-001"
        );

        exchange.setProperty(
                "eventId",
                "evt-gnm-close-001"
        );

        exchange.setProperty(
                "gnmIncidentId",
                "2713046552385248"
        );

        return exchange;
    }
}
