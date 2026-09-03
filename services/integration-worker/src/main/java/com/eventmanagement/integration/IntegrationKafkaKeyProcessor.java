package com.eventmanagement.integration;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Named;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.component.kafka.KafkaConstants;

@Named("integrationKafkaKeyProcessor")
@ApplicationScoped
public class IntegrationKafkaKeyProcessor implements Processor {

    @Override
    public void process(Exchange exchange) {

        String eventKey =
                exchange.getProperty("eventKey", String.class);

        if (eventKey == null || eventKey.isBlank()) {
            throw new IllegalStateException(
                    "No se puede publicar el resultado sin eventKey"
            );
        }

        exchange.getMessage().setHeader(
                KafkaConstants.KEY,
                eventKey
        );
    }
}
