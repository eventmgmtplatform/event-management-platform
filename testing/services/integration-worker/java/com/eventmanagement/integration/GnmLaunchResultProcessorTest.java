package com.eventmanagement.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.camel.Exchange;
import org.apache.camel.support.DefaultExchange;
import org.apache.camel.impl.DefaultCamelContext;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class GnmLaunchResultProcessorTest {

    private final ObjectMapper mapper =
            new ObjectMapper();

    private final GnmLaunchResultProcessor processor =
            new GnmLaunchResultProcessor(mapper);

    @Test
    void shouldBuildLaunchAcceptedResult()
            throws Exception {

        Exchange exchange =
                baseExchange();

        GnmHttpInvoker.HttpResult http =
                new GnmHttpInvoker.HttpResult(
                        200,
                        """
                        {
                          "status":200,
                          "message":"OK",
                          "result":{
                            "id":"2713046552385248"
                          }
                        }
                        """
                );

        exchange.setProperty(
                "gnmLaunchExecutionResult",
                new GnmRetryExecutor.ExecutionResult(
                        GnmRetryExecutor.Outcome.SUCCESS,
                        1,
                        http,
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
                "cmd-gnm-open-001",
                result.path("commandId").asText()
        );

        assertEquals(
                "evt-gnm-open-001",
                result.path("eventId").asText()
        );

        assertEquals(
                "GNM",
                result.path("integrationType").asText()
        );

        assertEquals(
                "SUCCESS",
                result.path("status").asText()
        );

        assertEquals(
                "2713046552385248",
                result.path(
                        "providerNotificationIdentity"
                ).path("incidentId").asText()
        );

        assertEquals(
                "LAUNCH_ACCEPTED",
                result.path(
                        "providerNotificationIdentity"
                ).path("lifecycleState").asText()
        );

        assertEquals(
                200,
                result.path(
                        "providerTransportResult"
                ).path("httpStatus").asInt()
        );

        assertTrue(
                result.path(
                        "providerTransportResult"
                ).path("accepted").asBoolean()
        );

        assertEquals(
                "1266890674",
                result.path("correlation")
                        .path("serverSerial")
                        .asText()
        );

        assertEquals(
                "2713046552385248",
                exchange.getProperty(
                        "gnmIncidentId",
                        String.class
                )
        );

        assertEquals(
                "LAUNCH_ACCEPTED",
                exchange.getProperty(
                        "gnmLaunchLifecycleState",
                        String.class
                )
        );

        assertFalse(
                result.path(
                        "providerNotificationIdentity"
                ).has("openNotificationId")
        );

        assertFalse(
                result.path(
                        "providerNotificationIdentity"
                ).has("incidentStatus")
        );
    }

    @Test
    void shouldRejectMissingExecutionResult() {

        Exchange exchange =
                baseExchange();

        assertThrows(
                IllegalStateException.class,
                () -> processor.process(exchange)
        );
    }

    @Test
    void shouldRejectNonSuccessExecution() {

        Exchange exchange =
                baseExchange();

        exchange.setProperty(
                "gnmLaunchExecutionResult",
                new GnmRetryExecutor.ExecutionResult(
                        GnmRetryExecutor.Outcome.PERMANENT_FAILURE,
                        1,
                        null,
                        new IllegalStateException("failure")
                )
        );

        assertThrows(
                IllegalStateException.class,
                () -> processor.process(exchange)
        );
    }

    @Test
    void shouldRejectMissingIncidentId()
            throws Exception {

        Exchange exchange =
                baseExchange();

        GnmHttpInvoker.HttpResult http =
                new GnmHttpInvoker.HttpResult(
                        200,
                        """
                        {
                          "status":200,
                          "message":"OK",
                          "result":{}
                        }
                        """
                );

        exchange.setProperty(
                "gnmLaunchExecutionResult",
                new GnmRetryExecutor.ExecutionResult(
                        GnmRetryExecutor.Outcome.SUCCESS,
                        1,
                        http,
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
                "cmd-gnm-open-001"
        );

        exchange.setProperty(
                "eventId",
                "evt-gnm-open-001"
        );

        exchange.setProperty(
                "gnmCanonicalEvent",
                mapper.createObjectNode()
                        .set(
                                "legacyCorrelation",
                                mapper.createObjectNode()
                                        .put(
                                                "serverSerial",
                                                "1266890674"
                                        )
                        )
        );

        return exchange;
    }
}
