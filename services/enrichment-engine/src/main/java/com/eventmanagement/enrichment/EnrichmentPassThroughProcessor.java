package com.eventmanagement.enrichment;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.component.kafka.KafkaConstants;
import org.apache.camel.component.kafka.consumer.KafkaManualCommit;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;

@Named("enrichmentPassThroughProcessor")
@ApplicationScoped
public class EnrichmentPassThroughProcessor implements Processor {

    private final ObjectMapper objectMapper;

    @Inject
    public EnrichmentPassThroughProcessor(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public void process(Exchange exchange) throws Exception {
        String body = exchange.getMessage().getBody(String.class);
        if (body == null || body.isBlank()) {
            throw new IllegalArgumentException("El evento de events.raw está vacío");
        }

        KafkaManualCommit manualCommit = exchange.getMessage().getHeader(
                KafkaConstants.MANUAL_COMMIT,
                KafkaManualCommit.class
        );
        if (manualCommit == null) {
            throw new IllegalStateException("Kafka manual commit no está disponible");
        }

        JsonNode parsed;
        try {
            parsed = objectMapper.readTree(body);
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException("events.raw no contiene JSON válido", exception);
        }
        if (!parsed.isObject()) {
            throw new IllegalArgumentException("events.raw debe contener un objeto JSON");
        }

        ObjectNode event = ((ObjectNode) parsed).deepCopy();
        String eventId = requiredText(event, "eventId");
        String eventKey = nullableText(event, "eventKey");
        if (eventKey == null) {
            eventKey = eventId;
        }

        JsonNode currentProcessing = event.get("processing");
        if (currentProcessing != null && !currentProcessing.isObject()) {
            throw new IllegalArgumentException("processing debe ser un objeto JSON");
        }

        ObjectNode processing = currentProcessing == null
                ? event.putObject("processing")
                : (ObjectNode) currentProcessing;

        ObjectNode enrichment = processing.putObject("enrichment");
        enrichment.put("status", "PENDING_RULES");
        enrichment.put("engine", "enrichment-engine");
        enrichment.put(
                "processedAt",
                OffsetDateTime.now(ZoneOffset.UTC).toString()
        );

        exchange.setProperty("enrichmentManualCommit", manualCommit);
        exchange.setProperty("enrichmentEventId", eventId);
        exchange.setProperty("enrichmentEventKey", eventKey);
        exchange.getMessage().setHeader(KafkaConstants.KEY, eventKey);
        exchange.getMessage().setBody(objectMapper.writeValueAsString(event));
    }

    private String requiredText(JsonNode node, String field) {
        String value = nullableText(node, field);
        if (value == null) {
            throw new IllegalArgumentException("Campo obligatorio ausente: " + field);
        }
        return value;
    }

    private String nullableText(JsonNode node, String field) {
        JsonNode value = node.get(field);
        if (value == null || value.isNull() || value.asText().isBlank()) {
            return null;
        }
        return value.asText().trim();
    }
}
