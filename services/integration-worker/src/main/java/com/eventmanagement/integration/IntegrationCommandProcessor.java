package com.eventmanagement.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.jboss.logging.Logger;

@Named("integrationCommandProcessor")
@ApplicationScoped
public class IntegrationCommandProcessor implements Processor {

    private static final Logger LOG =
            Logger.getLogger(IntegrationCommandProcessor.class);

    private final ObjectMapper objectMapper;

    @Inject
    public IntegrationCommandProcessor(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public void process(Exchange exchange) throws Exception {

        String rawBody =
                exchange.getMessage().getBody(String.class);

        JsonNode command =
                objectMapper.readTree(rawBody);

        if (command == null || !command.isObject()) {
            throw new IllegalArgumentException(
                    "El comando de integración debe ser un objeto JSON"
            );
        }

        String commandId =
                requiredText(command, "commandId");

        String eventId =
                requiredText(command, "eventId");

        String eventKey =
                requiredText(command, "eventKey");

        String tenant =
                requiredText(command, "tenant");

        String integrationType =
                requiredText(command, "integrationType")
                        .toUpperCase();

        String operation =
                requiredText(command, "operation")
                        .toUpperCase();

        JsonNode payload =
                command.get("payload");

        if (payload == null ||
                payload.isNull() ||
                !payload.isObject()) {

            throw new IllegalArgumentException(
                    "Campo obligatorio ausente o inválido: payload"
            );
        }

        /*
         * Preserve the complete canonical command before any
         * provider-specific transformation.
         *
         * The durable idempotency ledger must always claim this
         * original representation.
         */
        exchange.setProperty(
                "originalIntegrationCommand",
                command.deepCopy()
        );

        exchange.setProperty("commandId", commandId);
        exchange.setProperty("eventId", eventId);
        exchange.setProperty("eventKey", eventKey);
        exchange.setProperty("tenant", tenant);
        exchange.setProperty("integrationType", integrationType);
        exchange.setProperty("operation", operation);

        /*
         * Provider processors consume this property.
         * No provider-specific interpretation occurs here.
         */
        exchange.setProperty(
                "integrationPayload",
                payload.deepCopy()
        );

        LOG.infov(
                "Integration command envelope prepared: commandId={0}, eventId={1}, integration={2}, operation={3}",
                commandId,
                eventId,
                integrationType,
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
