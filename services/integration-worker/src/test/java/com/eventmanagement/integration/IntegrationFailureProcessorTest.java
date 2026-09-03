package com.eventmanagement.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.camel.Exchange;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.support.DefaultExchange;
import org.junit.jupiter.api.Test;

import java.net.ConnectException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class IntegrationFailureProcessorTest {

    private final ObjectMapper objectMapper =
            new ObjectMapper();

    private final IntegrationFailureProcessor processor =
            new IntegrationFailureProcessor(
                    objectMapper,
                    new ServiceNowErrorClassifier()
            );

    @Test
    void shouldProduceClassifiedInternalFailure()
            throws Exception {

        JsonNode result = processFailure(
                new IllegalStateException(
                        "Unexpected processing failure"
                ),
                1
        );

        assertEquals("1.1", result.path("schemaVersion").asText());
        assertEquals("FAILED", result.path("status").asText());
        assertEquals(
                "corr-failure-001",
                result.path("correlationId").asText()
        );

        assertTrue(result.path("externalId").isNull());
        assertTrue(result.path("externalReference").isNull());
        assertTrue(result.path("externalSystemId").isNull());

        JsonNode error = result.path("error");

        assertEquals(
                "INTERNAL_INTEGRATION_ERROR",
                error.path("code").asText()
        );
        assertEquals(
                "INTERNAL",
                error.path("category").asText()
        );
        assertFalse(error.path("retryable").asBoolean());
        assertEquals(1, error.path("attempt").asInt());
    }

    @Test
    void shouldProduceRetryableTransportFailure()
            throws Exception {

        JsonNode result = processFailure(
                new ConnectException("Connection refused"),
                2
        );

        JsonNode error = result.path("error");

        assertEquals(
                "SERVICENOW_UNAVAILABLE",
                error.path("code").asText()
        );
        assertEquals(
                "TRANSPORT",
                error.path("category").asText()
        );
        assertTrue(error.path("retryable").asBoolean());
        assertEquals(2, error.path("attempt").asInt());
    }

    @Test
    void shouldProducePermanentValidationFailure()
            throws Exception {

        JsonNode result = processFailure(
                new IllegalArgumentException(
                        "Campo obligatorio ausente: eventId"
                ),
                null
        );

        JsonNode error = result.path("error");

        assertEquals(
                "INVALID_INTEGRATION_COMMAND",
                error.path("code").asText()
        );
        assertEquals(
                "VALIDATION",
                error.path("category").asText()
        );
        assertFalse(error.path("retryable").asBoolean());
        assertEquals(1, error.path("attempt").asInt());
    }

    private JsonNode processFailure(
            Exception exception,
            Integer attempt
    ) throws Exception {

        Exchange exchange =
                new DefaultExchange(new DefaultCamelContext());

        exchange.setProperty(
                Exchange.EXCEPTION_CAUGHT,
                exception
        );

        exchange.setProperty("commandId", "cmd-failure-001");
        exchange.setProperty(
                "correlationId",
                "corr-failure-001"
        );
        exchange.setProperty("eventId", "evt-failure-001");
        exchange.setProperty(
                "eventKey",
                "lab|host01|failure"
        );
        exchange.setProperty("tenant", "LAB");
        exchange.setProperty(
                "integrationType",
                "SERVICENOW"
        );
        exchange.setProperty(
                "operation",
                "CREATE_TICKET"
        );

        if (attempt != null) {
            exchange.setProperty(
                    "integrationAttempt",
                    attempt
            );
        }

        processor.process(exchange);

        return objectMapper.readTree(
                exchange.getMessage().getBody(String.class)
        );
    }
}
