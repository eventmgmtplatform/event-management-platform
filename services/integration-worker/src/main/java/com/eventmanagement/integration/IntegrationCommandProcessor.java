package com.eventmanagement.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
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

        String commandJson =
                exchange.getMessage().getBody(String.class);

        if (commandJson == null || commandJson.isBlank()) {
            throw new IllegalArgumentException(
                    "El comando de integración está vacío"
            );
        }

        JsonNode command = objectMapper.readTree(commandJson);

        String commandId = requiredText(command, "commandId");
        String eventId = requiredText(command, "eventId");
        String eventKey = requiredText(command, "eventKey");
        String tenant = requiredText(command, "tenant");

        String integrationType =
                requiredText(command, "integrationType").toUpperCase();

        String operation =
                requiredText(command, "operation").toUpperCase();

        if (!"SERVICENOW".equals(integrationType)) {
            throw new IllegalArgumentException(
                    "Integración todavía no soportada: " + integrationType
            );
        }

        if (!"CREATE_TICKET".equals(operation)) {
            throw new IllegalArgumentException(
                    "Operación ServiceNow no soportada: " + operation
            );
        }

        JsonNode payload = command.path("payload");

        if (!payload.isObject()) {
            throw new IllegalArgumentException(
                    "El comando no contiene un payload válido"
            );
        }

        String resource =
                requiredText(payload, "resource");

        String summary =
                requiredText(payload, "summary");

        int severity = payload.path("severity").asInt(-1);

        if (severity < 0 || severity > 5) {
            throw new IllegalArgumentException(
                    "La severidad del comando debe estar entre 0 y 5"
            );
        }

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
        exchange.setProperty("resource", resource);
        exchange.setProperty("summary", summary);
        exchange.setProperty("severity", severity);

        ObjectNode serviceNowRequest =
                objectMapper.createObjectNode();

        serviceNowRequest.put(
                "short_description",
                summary
        );

        serviceNowRequest.put(
                "description",
                buildDescription(
                        eventId,
                        eventKey,
                        tenant,
                        resource,
                        summary,
                        severity
                )
        );

        serviceNowRequest.put(
                "impact",
                mapServiceNowImpact(severity)
        );

        serviceNowRequest.put(
                "urgency",
                mapServiceNowUrgency(severity)
        );

        serviceNowRequest.put(
                "severity",
                severity
        );

        serviceNowRequest.put(
                "resource",
                resource
        );

        serviceNowRequest.put(
                "correlation_id",
                eventKey
        );

        serviceNowRequest.put(
                "u_event_id",
                eventId
        );

        String requestJson =
                objectMapper.writeValueAsString(serviceNowRequest);

        exchange.getMessage().setBody(requestJson);

        LOG.infov(
                "Comando preparado: commandId={0}, eventId={1}, integration={2}",
                commandId,
                eventId,
                integrationType
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

    private String buildDescription(
            String eventId,
            String eventKey,
            String tenant,
            String resource,
            String summary,
            int severity
    ) {

        return """
                Event Management Platform

                Event ID : %s
                Event Key: %s
                Tenant   : %s
                Resource : %s
                Severity : %d
                Summary  : %s
                """.formatted(
                eventId,
                eventKey,
                tenant,
                resource,
                severity,
                summary
        );
    }

    private int mapServiceNowImpact(int severity) {

        if (severity >= 5) {
            return 1;
        }

        if (severity >= 3) {
            return 2;
        }

        return 3;
    }

    private int mapServiceNowUrgency(int severity) {

        if (severity >= 4) {
            return 1;
        }

        if (severity >= 2) {
            return 2;
        }

        return 3;
    }
}
