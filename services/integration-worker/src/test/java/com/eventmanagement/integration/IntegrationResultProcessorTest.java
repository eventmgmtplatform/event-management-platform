package com.eventmanagement.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.camel.Exchange;
import org.apache.camel.component.kafka.KafkaConstants;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.support.DefaultExchange;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class IntegrationResultProcessorTest {

    private final ObjectMapper objectMapper =
            new ObjectMapper();

    private final IntegrationResultProcessor processor =
            new IntegrationResultProcessor(objectMapper);

    @Test
    void shouldProduceCompatibleServiceNowResultContract()
            throws Exception {

        Exchange exchange = successfulExchange("""
                {
                  "result": {
                    "sys_id": "snow-sys-id-001",
                    "number": "INC0019284",
                    "state": "1"
                  }
                }
                """);

        processor.process(exchange);

        JsonNode result = objectMapper.readTree(
                exchange.getMessage().getBody(String.class)
        );

        assertEquals("1.1", result.path("schemaVersion").asText());
        assertEquals("cmd-001", result.path("commandId").asText());
        assertEquals("corr-001", result.path("correlationId").asText());
        assertEquals("evt-001", result.path("eventId").asText());
        assertEquals("SERVICENOW", result.path("integrationType").asText());
        assertEquals("CREATE_TICKET", result.path("operation").asText());
        assertEquals("SUCCESS", result.path("status").asText());

        /*
         * Legacy field retained for event-state-service compatibility.
         */
        assertEquals(
                "INC0019284",
                result.path("externalId").asText()
        );

        assertEquals(
                "INC0019284",
                result.path("externalReference").asText()
        );

        assertEquals(
                "snow-sys-id-001",
                result.path("externalSystemId").asText()
        );

        assertEquals(
                "bc1|server01|filesystem|var",
                exchange.getMessage().getHeader(
                        KafkaConstants.KEY,
                        String.class
                )
        );
    }

    @Test
    void shouldRejectResponseWithoutTicketNumber() {

        Exchange exchange = successfulExchange("""
                {
                  "result": {
                    "sys_id": "snow-sys-id-001"
                  }
                }
                """);

        assertThrows(
                IllegalStateException.class,
                () -> processor.process(exchange)
        );
    }

    @Test
    void shouldRejectResponseWithoutSysId() {

        Exchange exchange = successfulExchange("""
                {
                  "result": {
                    "number": "INC0019284"
                  }
                }
                """);

        assertThrows(
                IllegalStateException.class,
                () -> processor.process(exchange)
        );
    }

    private Exchange successfulExchange(String body) {

        Exchange exchange =
                new DefaultExchange(new DefaultCamelContext());

        exchange.getMessage().setBody(body);
        exchange.getMessage().setHeader(
                Exchange.HTTP_RESPONSE_CODE,
                201
        );

        exchange.setProperty("commandId", "cmd-001");
        exchange.setProperty("correlationId", "corr-001");
        exchange.setProperty("eventId", "evt-001");
        exchange.setProperty(
                "eventKey",
                "bc1|server01|filesystem|var"
        );
        exchange.setProperty("tenant", "bc1");
        exchange.setProperty("integrationType", "SERVICENOW");
        exchange.setProperty("operation", "CREATE_TICKET");

        return exchange;
    }
}
