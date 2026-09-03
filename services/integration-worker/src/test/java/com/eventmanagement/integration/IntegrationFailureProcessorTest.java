package com.eventmanagement.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.camel.Exchange;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.support.DefaultExchange;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class IntegrationFailureProcessorTest {

    private final ObjectMapper objectMapper =
            new ObjectMapper();

    private final IntegrationFailureProcessor processor =
            new IntegrationFailureProcessor(objectMapper);

    @Test
    void shouldProduceVersion11FailureContract()
            throws Exception {

        Exchange exchange =
                new DefaultExchange(new DefaultCamelContext());

        exchange.setProperty(
                Exchange.EXCEPTION_CAUGHT,
                new IllegalStateException("ServiceNow unavailable")
        );

        exchange.setProperty("commandId", "cmd-failure-001");
        exchange.setProperty("correlationId", "corr-failure-001");
        exchange.setProperty("eventId", "evt-failure-001");
        exchange.setProperty("eventKey", "lab|host01|failure");
        exchange.setProperty("tenant", "LAB");
        exchange.setProperty("integrationType", "SERVICENOW");
        exchange.setProperty("operation", "CREATE_TICKET");

        processor.process(exchange);

        JsonNode result = objectMapper.readTree(
                exchange.getMessage().getBody(String.class)
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

        assertEquals(
                "IllegalStateException",
                result.path("error").path("type").asText()
        );
    }
}
