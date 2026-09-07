package com.eventmanagement.integration;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Named;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.jboss.logging.Logger;

@Named("serviceNowCommandProcessor")
@ApplicationScoped
public class ServiceNowCommandProcessor implements Processor {

    private static final Logger LOG =
            Logger.getLogger(ServiceNowCommandProcessor.class);

    @Override
    public void process(Exchange exchange) {

        String operation =
                exchange.getProperty(
                        "operation",
                        String.class
                );

        if (!"CREATE_TICKET".equals(operation)) {
            throw new IllegalArgumentException(
                    "Operación ServiceNow no soportada: " + operation
            );
        }

        JsonNode payload =
                exchange.getProperty(
                        "integrationPayload",
                        JsonNode.class
                );

        if (payload == null || !payload.isObject()) {
            throw new IllegalArgumentException(
                    "Payload ServiceNow ausente o inválido"
            );
        }

        String resource =
                requiredText(payload, "resource");

        String summary =
                requiredText(payload, "summary");

        int severity =
                payload.path("severity").asInt(-1);

        if (severity < 0) {
            throw new IllegalArgumentException(
                    "Campo obligatorio ausente o inválido: severity"
            );
        }

        exchange.setProperty("resource", resource);
        exchange.setProperty("summary", summary);
        exchange.setProperty("severity", severity);

        LOG.infov(
                "ServiceNow command validated: commandId={0}, operation={1}",
                exchange.getProperty("commandId"),
                operation
        );
    }

    private String requiredText(
            JsonNode node,
            String fieldName
    ) {

        JsonNode value = node.get(fieldName);

        if (value == null ||
                value.isNull() ||
                value.asText().isBlank()) {

            throw new IllegalArgumentException(
                    "Campo obligatorio ausente: " + fieldName
            );
        }

        return value.asText().trim();
    }
}
