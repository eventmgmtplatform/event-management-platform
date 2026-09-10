package com.eventmanagement.state;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.Set;

/** Version 1.0.0 explicit OPEN/CLOSE contract. Reopen is derived from stored state. */
public record StateRequest(String messageId, String eventId, String eventKey, String tenant,
                           String transition, OffsetDateTime occurredAt, int sourceSeverity,
                           int effectiveSeverity, JsonNode payload) {
    public static StateRequest parse(JsonNode node) {
        if (node == null || !node.isObject()) throw reject("STATE_OBJECT_REQUIRED");
        for (var field : Map.of("messageId",128,"eventId",100,"eventKey",128,"tenantId",100,
                "correlationId",128,"causationId",128).entrySet()) {
            var value=node.path(field.getKey());
            if (!value.isTextual() || value.asText().isBlank() || value.asText().length()>field.getValue()
                    || !value.asText().equals(value.asText().trim())) throw reject("INVALID_"+field.getKey());
        }
        if (!"1.0.0".equals(node.path("schemaVersion").asText())) throw reject("STATE_SCHEMA_UNSUPPORTED");
        if (Set.of("UNKNOWN","N/A","UNDEFINED").contains(node.path("eventKey").asText().toUpperCase(java.util.Locale.ROOT)))
            throw reject("INVALID_EVENT_IDENTITY");
        String transition=node.path("transition").asText();
        if (!Set.of("OPEN","CLOSE").contains(transition)) throw reject("STATE_TRANSITION_UNSUPPORTED");
        for (String field : new String[]{"sourceSeverity","effectiveSeverity"}) {
            var value=node.path(field);
            if (!value.isIntegralNumber() || !value.canConvertToInt() || value.intValue()<0 || value.intValue()>5)
                throw reject("INVALID_"+field);
        }
        if (transition.equals("CLOSE") && node.path("effectiveSeverity").intValue()!=0)
            throw reject("CLOSE_SEVERITY_MUST_BE_ZERO");
        if (!node.path("decisions").isObject() || !node.path("event").isObject()) throw reject("STATE_CONTEXT_REQUIRED");
        OffsetDateTime time;
        try { time=OffsetDateTime.parse(node.path("occurredAt").asText()).truncatedTo(ChronoUnit.MICROS); }
        catch (RuntimeException error) { throw reject("STATE_TIMESTAMP_INVALID"); }
        return new StateRequest(node.path("messageId").asText(),node.path("eventId").asText(),
                node.path("eventKey").asText(),node.path("tenantId").asText(),transition,time,
                node.path("sourceSeverity").intValue(),node.path("effectiveSeverity").intValue(),node.deepCopy());
    }
    private static RejectedIntegrationResult reject(String code) { return new RejectedIntegrationResult(code); }
}
