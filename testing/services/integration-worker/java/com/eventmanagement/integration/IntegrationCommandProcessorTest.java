package com.eventmanagement.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.camel.Exchange;
import org.apache.camel.support.DefaultExchange;
import org.apache.camel.impl.DefaultCamelContext;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class IntegrationCommandProcessorTest {

    private final ObjectMapper objectMapper =
            new ObjectMapper();

    private final IntegrationCommandProcessor processor =
            new IntegrationCommandProcessor(objectMapper);

    @Test
    void acceptsProcessorCompatibilityFixture() throws Exception {
        String json=java.nio.file.Files.readString(java.nio.file.Path.of(
                "../../testing/services/event-processor/resources/contracts/worker-command.json"));
        Exchange exchange=exchange(json);
        processor.process(exchange);
        assertEquals("SERVICENOW",exchange.getProperty("integrationType"));
        assertEquals("tenant",exchange.getProperty("tenant"));
        assertEquals("key",exchange.getProperty("eventKey"));
        assertEquals("CREATE_TICKET",exchange.getProperty("operation"));
        assertEquals(objectMapper.readTree(json),exchange.getProperty("originalIntegrationCommand",JsonNode.class));
    }

    @Test
    void shouldPrepareServiceNowEnvelopeWithoutParsingProviderPayload()
            throws Exception {

        Exchange exchange = exchange("""
                {
                  "schemaVersion": "1.1",
                  "commandId": "cmd-001",
                  "eventId": "evt-001",
                  "eventKey": "key-001",
                  "tenant": "default",
                  "integrationType": "SERVICENOW",
                  "operation": "CREATE_TICKET",
                  "payload": {
                    "resource": "server01",
                    "summary": "CPU high",
                    "severity": 4
                  }
                }
                """);

        processor.process(exchange);

        assertEquals(
                "SERVICENOW",
                exchange.getProperty("integrationType")
        );

        assertEquals(
                "CREATE_TICKET",
                exchange.getProperty("operation")
        );

        JsonNode original =
                exchange.getProperty(
                        "originalIntegrationCommand",
                        JsonNode.class
                );

        assertNotNull(original);
        assertEquals(
                "cmd-001",
                original.path("commandId").asText()
        );

        JsonNode payload =
                exchange.getProperty(
                        "integrationPayload",
                        JsonNode.class
                );

        assertEquals(
                "server01",
                payload.path("resource").asText()
        );

        assertNull(exchange.getProperty("resource"));
        assertNull(exchange.getProperty("summary"));
    }

    @Test
    void shouldPrepareGnmEnvelopeWithoutEverbridgeKnowledge()
            throws Exception {

        Exchange exchange = exchange("""
                {
                  "schemaVersion": "1.1",
                  "commandId": "cmd-gnm-001",
                  "eventId": "evt-gnm-001",
                  "eventKey": "vit:test",
                  "tenant": "default",
                  "integrationType": "GNM",
                  "operation": "SEND_NOTIFICATION",
                  "payload": {
                    "customerCode": "vit"
                  }
                }
                """);

        processor.process(exchange);

        assertEquals(
                "GNM",
                exchange.getProperty("integrationType")
        );

        assertEquals(
                "SEND_NOTIFICATION",
                exchange.getProperty("operation")
        );

        JsonNode payload =
                exchange.getProperty(
                        "integrationPayload",
                        JsonNode.class
                );

        assertEquals(
                "vit",
                payload.path("customerCode").asText()
        );
    }

    @Test
    void shouldRejectMissingPayload() {

        Exchange exchange = exchange("""
                {
                  "commandId": "cmd-002",
                  "eventId": "evt-002",
                  "eventKey": "key-002",
                  "tenant": "default",
                  "integrationType": "GNM",
                  "operation": "SEND_NOTIFICATION"
                }
                """);

        assertThrows(
                IllegalArgumentException.class,
                () -> processor.process(exchange)
        );
    }

    private Exchange exchange(String body) {

        Exchange exchange =
                new DefaultExchange(
                        new DefaultCamelContext()
                );

        exchange.getMessage().setBody(body);

        return exchange;
    }
}
