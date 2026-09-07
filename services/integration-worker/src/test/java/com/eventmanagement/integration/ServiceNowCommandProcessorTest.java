package com.eventmanagement.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.camel.Exchange;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.support.DefaultExchange;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ServiceNowCommandProcessorTest {

    private final ObjectMapper mapper =
            new ObjectMapper();

    private final ServiceNowCommandProcessor processor =
            new ServiceNowCommandProcessor();

    @Test
    void shouldPreserveExistingCreateTicketContract()
            throws Exception {

        Exchange exchange =
                new DefaultExchange(
                        new DefaultCamelContext()
                );

        exchange.setProperty(
                "commandId",
                "cmd-sn-001"
        );

        exchange.setProperty(
                "operation",
                "CREATE_TICKET"
        );

        exchange.setProperty(
                "integrationPayload",
                mapper.readTree("""
                        {
                          "resource": "server01",
                          "summary": "CPU high",
                          "severity": 4
                        }
                        """)
        );

        processor.process(exchange);

        assertEquals(
                "server01",
                exchange.getProperty("resource")
        );

        assertEquals(
                "CPU high",
                exchange.getProperty("summary")
        );

        assertEquals(
                4,
                exchange.getProperty("severity")
        );
    }

    @Test
    void shouldRejectUnsupportedServiceNowOperation()
            throws Exception {

        Exchange exchange =
                new DefaultExchange(
                        new DefaultCamelContext()
                );

        exchange.setProperty(
                "operation",
                "SEND_NOTIFICATION"
        );

        exchange.setProperty(
                "integrationPayload",
                mapper.readTree("{}")
        );

        assertThrows(
                IllegalArgumentException.class,
                () -> processor.process(exchange)
        );
    }
}
