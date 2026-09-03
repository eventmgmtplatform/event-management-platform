package com.eventmanagement.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.camel.Exchange;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.support.DefaultExchange;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class IntegrationCommandProcessorTest {

    private final ObjectMapper objectMapper =
            new ObjectMapper();

    private final IntegrationCommandProcessor processor =
            new IntegrationCommandProcessor(objectMapper);

    @Test
    void shouldPropagateExplicitCorrelationId() throws Exception {

        Exchange exchange = exchangeWithCommand("""
                {
                  "schemaVersion": "1.1",
                  "commandId": "cmd-001",
                  "correlationId": "corr-001",
                  "eventId": "evt-001",
                  "eventKey": "bc1|server01|filesystem|var",
                  "tenant": "bc1",
                  "integrationType": "SERVICENOW",
                  "operation": "CREATE_TICKET",
                  "payload": {
                    "resource": "server01",
                    "summary": "Filesystem usage is high",
                    "severity": 4
                  }
                }
                """);

        processor.process(exchange);

        assertEquals(
                "corr-001",
                exchange.getProperty(
                        "correlationId",
                        String.class
                )
        );
    }

    @Test
    void shouldUseCommandIdWhenCorrelationIdIsMissing()
            throws Exception {

        Exchange exchange = exchangeWithCommand("""
                {
                  "schemaVersion": "1.0",
                  "commandId": "cmd-002",
                  "eventId": "evt-002",
                  "eventKey": "bc1|server02|cpu",
                  "tenant": "bc1",
                  "integrationType": "SERVICENOW",
                  "operation": "CREATE_TICKET",
                  "payload": {
                    "resource": "server02",
                    "summary": "CPU utilization is high",
                    "severity": 3
                  }
                }
                """);

        processor.process(exchange);

        assertEquals(
                "cmd-002",
                exchange.getProperty(
                        "correlationId",
                        String.class
                )
        );
    }

    @Test
    void shouldPreserveServiceNowCorrelationIdAsEventKey()
            throws Exception {

        Exchange exchange = exchangeWithCommand("""
                {
                  "schemaVersion": "1.1",
                  "commandId": "cmd-003",
                  "correlationId": "corr-003",
                  "eventId": "evt-003",
                  "eventKey": "lab|host01|service",
                  "tenant": "LAB",
                  "integrationType": "SERVICENOW",
                  "operation": "CREATE_TICKET",
                  "payload": {
                    "resource": "host01",
                    "summary": "Service unavailable",
                    "severity": 5
                  }
                }
                """);

        processor.process(exchange);

        JsonNode serviceNowRequest =
                objectMapper.readTree(
                        exchange.getMessage().getBody(String.class)
                );

        assertEquals(
                "lab|host01|service",
                serviceNowRequest
                        .path("correlation_id")
                        .asText()
        );
    }

    private Exchange exchangeWithCommand(String command) {

        Exchange exchange =
                new DefaultExchange(new DefaultCamelContext());

        exchange.getMessage().setBody(command);

        return exchange;
    }
}
