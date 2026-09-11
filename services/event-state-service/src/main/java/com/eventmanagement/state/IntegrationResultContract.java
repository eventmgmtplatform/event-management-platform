package com.eventmanagement.state;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** Existing Worker contract (1.0), distinct from the proposed ESS V1 envelope. */
public final class IntegrationResultContract {
    private IntegrationResultContract() {}

    public static void validate(JsonNode result) {
        if (result == null || !result.isObject()) {
            throw new RejectedIntegrationResult("INTEGRATION_RESULT_OBJECT_REQUIRED");
        }
        for (var field : Map.of("resultId", 128, "eventKey", 128, "eventId", 100,
                "tenant", 100, "integrationType", 50, "status", 50).entrySet()) {
            JsonNode value = result.path(field.getKey());
            if (!value.isTextual() || value.asText().isBlank()
                    || value.asText().length() > field.getValue()
                    || !value.asText().equals(value.asText().trim())) {
                throw new RejectedIntegrationResult("INVALID_FIELD_" + field.getKey());
            }
        }
        if (Set.of("UNKNOWN", "N/A", "UNDEFINED").contains(
                result.path("eventKey").asText().toUpperCase(Locale.ROOT))) {
            throw new RejectedIntegrationResult("INVALID_EVENT_IDENTITY");
        }
        if (!Set.of("SERVICENOW", "GLPI", "GNM", "CACF").contains(
                result.path("integrationType").asText().toUpperCase(Locale.ROOT))) {
            throw new RejectedIntegrationResult("UNSUPPORTED_INTEGRATION_TYPE");
        }
        JsonNode external = result.path("externalId");
        if (!external.isMissingNode() && !external.isNull()
                && (!external.isTextual() || external.asText().length() > 100)) {
            throw new RejectedIntegrationResult("INVALID_FIELD_externalId");
        }
    }
}
