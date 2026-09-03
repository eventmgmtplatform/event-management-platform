package com.eventmanagement.integration;

import org.apache.camel.Exchange;
import org.apache.camel.component.kafka.KafkaConstants;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.support.DefaultExchange;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class IntegrationKafkaKeyProcessorTest {

    private final IntegrationKafkaKeyProcessor processor =
            new IntegrationKafkaKeyProcessor();

    @Test
    void shouldUseEventKeyAsKafkaRecordKey() {

        Exchange exchange =
                new DefaultExchange(new DefaultCamelContext());

        exchange.setProperty(
                "eventKey",
                "bc1|server01|filesystem|var"
        );

        processor.process(exchange);

        assertEquals(
                "bc1|server01|filesystem|var",
                exchange.getMessage().getHeader(
                        KafkaConstants.KEY,
                        String.class
                )
        );
    }

    @Test
    void shouldRejectMissingEventKey() {

        Exchange exchange =
                new DefaultExchange(new DefaultCamelContext());

        assertThrows(
                IllegalStateException.class,
                () -> processor.process(exchange)
        );
    }

    @Test
    void shouldRejectBlankEventKey() {

        Exchange exchange =
                new DefaultExchange(new DefaultCamelContext());

        exchange.setProperty("eventKey", " ");

        assertThrows(
                IllegalStateException.class,
                () -> processor.process(exchange)
        );
    }
}
