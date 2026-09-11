package com.eventmanagement.integration.cacf;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.UUID;

/** Validated canonical input. Provider vocabulary stays outside this model. */
public record AutomationRequest(UUID executionId, String commandId, String eventId,
        String eventKey, String customerCode, int timeoutSeconds, JsonNode payload) {
    public static AutomationRequest parse(JsonNode payload, String commandId, String eventKey, int defaultTimeout) {
        if (payload == null || !payload.isObject()) throw new IllegalArgumentException("Automation request must be an object");
        UUID id = UUID.fromString(text(payload, "executionId", 36));
        if (!"1.0".equals(text(payload, "schemaVersion", 16))) throw new IllegalArgumentException("Unsupported automation schemaVersion");
        String eventId = text(payload, "eventId", 100);
        String customer = text(payload.path("customer"), "code", 64);
        JsonNode event = payload.path("event");
        text(event, "sourceSystem", 200);
        text(event, "sourceSerial", 200);
        text(event, "summary", 10000);
        text(event, "resourceId", 255);
        if (!event.path("severity").isIntegralNumber() || !event.path("severity").canConvertToInt()
                || event.path("severity").asInt() < 0) throw new IllegalArgumentException("Invalid severity");
        JsonNode ticket = payload.path("ticket");
        text(ticket, "originalAssignmentGroup", 255);
        text(ticket, "holdingAssignmentGroup", 255);
        String ticketProvider=ticket.path("provider").asText("SERVICENOW").toUpperCase(java.util.Locale.ROOT);
        if(!java.util.Set.of("SERVICENOW","GLPI").contains(ticketProvider)) throw new IllegalArgumentException("Unsupported ticket provider");
        if("GLPI".equals(ticketProvider)) {
            JsonNode nativeId=ticket.has("id")?ticket.get("id"):ticket.get("sysId");
            if(nativeId==null || !nativeId.isIntegralNumber() || !nativeId.canConvertToLong() || nativeId.asLong()<1) throw new IllegalArgumentException("GLPI ticket id required");
        }
        if (!"NEXT".equals(text(payload.path("automation"), "provider", 32))) throw new IllegalArgumentException("Unsupported automation provider");
        JsonNode timeout = payload.path("automation").get("resultTimeoutSeconds");
        if (timeout != null && (!timeout.isIntegralNumber() || !timeout.canConvertToInt())) throw new IllegalArgumentException("Invalid result timeout");
        int seconds = timeout == null ? defaultTimeout : timeout.asInt();
        if (seconds <= 0 || seconds > 604800) throw new IllegalArgumentException("Result timeout must be between 1 and 604800 seconds");
        for (String value : new String[] {customer, event.path("sourceSystem").asText(), event.path("sourceSerial").asText()}) {
            if (value.contains(":")) throw new IllegalArgumentException("Correlation components cannot contain colon");
        }
        String key = eventKey == null || eventKey.isBlank() ? eventId : eventKey;
        if (key.length() > 128) throw new IllegalArgumentException("eventKey exceeds projection limit");
        String command = commandId == null ? id.toString() : commandId;
        if (command.isBlank() || command.length() > 128) throw new IllegalArgumentException("Invalid commandId");
        return new AutomationRequest(id, command, eventId, key, customer, seconds, payload.deepCopy());
    }

    public static String text(JsonNode node, String field, int max) {
        JsonNode value = node.get(field);
        if (value == null || !value.isTextual() || value.asText().isBlank() || value.asText().length() > max)
            throw new IllegalArgumentException("Missing or invalid field: " + field);
        return value.asText().trim();
    }
}
