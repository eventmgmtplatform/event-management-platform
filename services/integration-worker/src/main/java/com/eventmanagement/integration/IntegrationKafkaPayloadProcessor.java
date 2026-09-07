package com.eventmanagement.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;

/**
 * Enforces the integration.results Kafka payload contract.
 *
 * <p>The Kafka producer uses StringSerializer, therefore every terminal
 * integration result must cross the publication boundary as a
 * java.lang.String regardless of the provider-specific processor that
 * produced it.</p>
 */
@Named("integrationKafkaPayloadProcessor")
@ApplicationScoped
public class IntegrationKafkaPayloadProcessor implements Processor {

    private final ObjectMapper objectMapper;

    @Inject
    public IntegrationKafkaPayloadProcessor(
            ObjectMapper objectMapper
    ) {
        this.objectMapper = objectMapper;
    }

    @Override
    public void process(Exchange exchange) throws Exception {

        Object body = exchange.getMessage().getBody();

        if (body == null) {
            throw new IllegalStateException(
                    "Cannot publish integration result with null body"
            );
        }

        if (body instanceof String) {
            String value = (String) body;

            if (value.isBlank()) {
                throw new IllegalStateException(
                        "Cannot publish integration result with blank body"
                );
            }

            return;
        }

        if (body instanceof JsonNode) {
            exchange.getMessage().setBody(
                    objectMapper.writeValueAsString(body)
            );
            return;
        }

        throw new IllegalStateException(
                "Unsupported integration.results payload type: "
                        + body.getClass().getName()
        );
    }
}
