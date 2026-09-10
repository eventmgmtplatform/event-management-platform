package com.eventmanagement.processor.adapters.kafka;

import com.eventmanagement.processor.domain.Event;
import com.eventmanagement.processor.domain.StableIdentity;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.time.temporal.ChronoUnit;

/** Explicit decision-plane output. Encoded once inside the processing transaction. */
public final class StateRequestAdapter {
    private final ObjectMapper mapper;
    public StateRequestAdapter(ObjectMapper mapper) { this.mapper = mapper; }

    public ObjectNode encode(Event event, String processingId, JsonNode evidence) throws Exception {
        ObjectNode request = mapper.createObjectNode();
        request.put("schemaVersion", "1.0.0");
        request.put("messageId", StableIdentity.of("state-request-v1", event.tenant(), event.eventId()));
        request.put("eventId", event.eventId());
        request.put("eventKey", event.eventKey());
        request.put("tenantId", event.tenant());
        request.put("correlationId", processingId);
        request.put("causationId", event.eventId());
        request.put("transition", event.recovery() ? "CLOSE" : "OPEN");
        request.put("occurredAt", event.receivedAt().truncatedTo(ChronoUnit.MICROS).toString());
        JsonNode original = mapper.readTree(event.originalJson());
        int sourceSeverity = original.path("sourceSeverity").isIntegralNumber()
                ? original.path("sourceSeverity").intValue() : event.severity();
        request.put("sourceSeverity", sourceSeverity);
        request.put("effectiveSeverity", event.recovery() ? 0 : event.severity());
        ObjectNode decisions = request.putObject("decisions");
        decisions.put("directive", evidence.path("directive").asText());
        decisions.set("correlation", evidence.path("correlation"));
        decisions.set("routing", evidence.path("routing"));
        request.set("event", original);
        return request;
    }
}
