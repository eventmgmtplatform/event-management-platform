package com.eventmanagement.gateway;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.jboss.logging.Logger;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

@Named("eventValidationProcessor")
@ApplicationScoped
public class EventValidationProcessor implements Processor {

    private static final Logger LOG =
            Logger.getLogger(EventValidationProcessor.class);

    private final ObjectMapper objectMapper;

    @Inject
    public EventValidationProcessor(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public void process(Exchange exchange) throws Exception {

        String originalBody =
                exchange.getMessage().getBody(String.class);

        if (originalBody == null || originalBody.isBlank()) {
            throw new IllegalArgumentException(
                    "El cuerpo de la solicitud está vacío"
            );
        }

        JsonNode inputEvent;

        try {
            inputEvent = objectMapper.readTree(originalBody);
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException(
                    "El cuerpo recibido no contiene JSON válido",
                    exception
            );
        }

        if (!inputEvent.isObject()) {
            throw new IllegalArgumentException(
                    "El evento debe ser un objeto JSON"
            );
        }

        validateRequiredField(inputEvent, "resource");
        validateRequiredField(inputEvent, "summary");
        validateRequiredField(inputEvent, "severity");
        validateRequiredField(inputEvent, "status");

        int severity = inputEvent.path("severity").asInt(-1);

        if (severity < 0 || severity > 5) {
            throw new IllegalArgumentException(
                    "severity debe encontrarse entre 0 y 5"
            );
        }

        String status = inputEvent.path("status")
                .asText()
                .trim()
                .toUpperCase();

        if (!status.equals("PROBLEM")
                && !status.equals("OK")
                && !status.equals("RESOLVED")) {

            throw new IllegalArgumentException(
                    "status debe ser PROBLEM, OK o RESOLVED"
            );
        }

        String receivedAt =
                OffsetDateTime.now(ZoneOffset.UTC).toString();

        String eventId =
                UUID.randomUUID().toString();

        String source = inputEvent.path("source")
                .asText("zabbix")
                .trim()
                .toLowerCase();

        String resourceName = inputEvent.path("resource")
                .asText()
                .trim()
                .toLowerCase();

        ObjectNode normalizedEvent =
                objectMapper.createObjectNode();

        normalizedEvent.put("schemaVersion", "1.0");
        normalizedEvent.put("eventId", eventId);
        normalizedEvent.put("source", source);

        normalizedEvent.put(
                "sourceEventId",
                inputEvent.path("sourceEventId")
                        .asText(eventId)
        );

        ObjectNode resource =
                normalizedEvent.putObject("resource");

        resource.put("name", resourceName);

        ObjectNode alert =
                normalizedEvent.putObject("alert");

        alert.put(
                "summary",
                inputEvent.path("summary").asText()
        );

        alert.put("severity", severity);
        alert.put("status", status);

        ObjectNode timestamps =
                normalizedEvent.putObject("timestamps");

        timestamps.put("receivedAt", receivedAt);
        timestamps.put("lastUpdatedAt", receivedAt);

        normalizedEvent.set(
                "originalEvent",
                inputEvent.deepCopy()
        );

        exchange.getMessage().setHeader(
                "eventId",
                eventId
        );

        exchange.getMessage().setHeader(
                "eventSource",
                source
        );

        exchange.getMessage().setHeader(
                "eventStatus",
                status
        );

        exchange.getMessage().setBody(
                objectMapper.writeValueAsString(normalizedEvent)
        );

        LOG.infov(
                "Evento validado y normalizado. eventId={0}, resource={1}",
                eventId,
                resourceName
        );
    }

    private void validateRequiredField(
            JsonNode event,
            String fieldName
    ) {

        JsonNode value = event.get(fieldName);

        if (value == null
                || value.isNull()
                || value.asText("").isBlank()) {

            throw new IllegalArgumentException(
                    "Campo obligatorio ausente o vacío: " + fieldName
            );
        }
    }
}
