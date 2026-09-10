package com.eventmanagement.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.apache.camel.Exchange;
import org.apache.camel.support.DefaultExchange;
import org.apache.camel.impl.DefaultCamelContext;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class IntegrationKafkaPayloadProcessorTest {

    private final ObjectMapper objectMapper =
            new ObjectMapper();

    private final IntegrationKafkaPayloadProcessor processor =
            new IntegrationKafkaPayloadProcessor(objectMapper);

    @Test
    void preservesStringPayload() throws Exception {

        Exchange exchange =
                new DefaultExchange(new DefaultCamelContext());

        String payload =
                "{\"status\":\"SUCCESS\"}";

        exchange.getMessage().setBody(payload);

        processor.process(exchange);

        assertEquals(
                String.class,
                exchange.getMessage().getBody().getClass()
        );

        assertEquals(
                payload,
                exchange.getMessage().getBody(String.class)
        );
    }

    @Test
    void convertsJsonNodePayloadToString() throws Exception {

        Exchange exchange =
                new DefaultExchange(new DefaultCamelContext());

        ObjectNode payload =
                objectMapper.createObjectNode();

        payload.put("status", "SUCCESS");
        payload.put("integrationType", "GNM");

        exchange.getMessage().setBody(payload);

        processor.process(exchange);

        Object result =
                exchange.getMessage().getBody();

        assertEquals(String.class, result.getClass());

        assertEquals(
                "SUCCESS",
                objectMapper.readTree((String) result)
                        .path("status")
                        .asText()
        );

        assertEquals(
                "GNM",
                objectMapper.readTree((String) result)
                        .path("integrationType")
                        .asText()
        );
    }

    @Test
    void rejectsNullPayload() {

        Exchange exchange =
                new DefaultExchange(new DefaultCamelContext());

        exchange.getMessage().setBody(null);

        IllegalStateException exception =
                assertThrows(
                        IllegalStateException.class,
                        () -> processor.process(exchange)
                );

        assertTrue(
                exception.getMessage().contains("null body")
        );
    }

    @Test
    void rejectsUnsupportedPayloadType() {

        Exchange exchange =
                new DefaultExchange(new DefaultCamelContext());

        exchange.getMessage().setBody(12345);

        IllegalStateException exception =
                assertThrows(
                        IllegalStateException.class,
                        () -> processor.process(exchange)
                );

        assertTrue(
                exception.getMessage().contains(
                        "Unsupported integration.results payload type"
                )
        );
    }
}
