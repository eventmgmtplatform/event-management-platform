package com.eventmanagement.processor.adapters.kafka;

import com.eventmanagement.processor.domain.Event;
import com.fasterxml.jackson.databind.*;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.time.Instant;

@ApplicationScoped
public class GatewayEventAdapter {
    private final ObjectMapper mapper;
    @Inject public GatewayEventAdapter(ObjectMapper mapper) { this.mapper = mapper; }
    public Event decode(String body) {
        try {
            JsonNode json = mapper.readTree(body);
            if (json == null || !json.isObject()) throw new IllegalArgumentException();
            String version = text(json, "schemaVersion"), id = text(json, "eventId");
            String key, tenant; Event.Status status; JsonNode severity;
            if (version.equals("1.1")) {
                key = text(json, "eventKey"); tenant = text(json.path("tenant"), "code");
                status = switch(text(json, "lifecycleAction")) {
                    case "OPEN" -> Event.Status.PROBLEM;
                    case "CLOSE" -> Event.Status.OK;
                    default -> throw new IllegalArgumentException();
                };
                severity = json.path("effectiveSeverity");
            } else if (version.equals("1.0")) {
                key = json.hasNonNull("eventKey") ? text(json, "eventKey") : id;
                // Legacy gateway has no required tenant. Do not fabricate a customer.
                tenant = json.path("originalEvent").path("tenant").asText("");
                status = Event.Status.valueOf(text(json.path("alert"), "status"));
                severity = json.path("alert").path("severity");
            } else throw new IllegalArgumentException();
            if (!severity.isIntegralNumber() || !severity.canConvertToInt()) throw new IllegalArgumentException();
            if (json.has("processing") && !json.get("processing").isObject()) throw new IllegalArgumentException();
            return new Event(id, key, tenant, status, severity.intValue(),
                    Instant.parse(text(json.path("timestamps"), "receivedAt")), body, selectors(json));
        } catch (Exception e) {
            // Payload/parser messages may contain sensitive input. Never propagate them.
            throw new IllegalArgumentException("INVALID_GATEWAY_CONTRACT");
        }
    }
    private static java.util.Map<String,String> selectors(JsonNode json) {
        var result=new java.util.HashMap<String,String>();
        String[][] fields={{"summary","/summary"},{"node","/resource/name"},{"nodeAlias","/resource/address"},{"component","/resource/component"},
                {"instanceId","/condition/instanceId"},{"monitoringSolution","/source/system"}};
        for(var field:fields) {
            var value=json.at(field[1]);
            if(value.isTextual() && !value.textValue().isBlank())result.put(field[0],value.textValue());
        }
        return result;
    }
    private static String text(JsonNode node, String name) {
        JsonNode value = node.get(name);
        if (value == null || !value.isTextual() || value.textValue().isBlank())
            throw new IllegalArgumentException();
        return value.textValue();
    }
}
