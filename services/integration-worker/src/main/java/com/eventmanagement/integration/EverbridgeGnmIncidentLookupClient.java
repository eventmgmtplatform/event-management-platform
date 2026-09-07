package com.eventmanagement.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

@ApplicationScoped
public class EverbridgeGnmIncidentLookupClient
        implements GnmIncidentLookupClient {

    private final GnmHttpInvoker httpInvoker;
    private final ObjectMapper objectMapper;

    @Inject
    public EverbridgeGnmIncidentLookupClient(
            GnmHttpInvoker httpInvoker,
            ObjectMapper objectMapper
    ) {
        this.httpInvoker = httpInvoker;
        this.objectMapper = objectMapper;
    }

    @Override
    public GnmIncidentSnapshot getIncident(
            String organizationId,
            String incidentId
    ) throws Exception {

        GnmHttpInvoker.HttpResult response =
                httpInvoker.getIncident(
                        organizationId,
                        incidentId
                );

        if (response.httpStatus() < 200 ||
                response.httpStatus() >= 300) {
            throw new IllegalStateException(
                    "GNM incident GET returned HTTP "
                            + response.httpStatus()
            );
        }

        JsonNode root =
                objectMapper.readTree(
                        response.responseBody()
                );

        if (root == null || !root.isObject()) {
            throw new IllegalStateException(
                    "GNM incident GET response is invalid"
            );
        }

        JsonNode result = root.get("result");

        if (result == null || !result.isObject()) {
            throw new IllegalStateException(
                    "GNM incident GET response has no result object"
            );
        }

        String returnedIncidentId =
                requiredText(result, "id");

        if (!incidentId.equals(returnedIncidentId)) {
            throw new IllegalStateException(
                    "GNM incident GET returned unexpected incidentId"
            );
        }

        String returnedOrganizationId =
                requiredText(
                        result,
                        "organizationId"
                );

        if (!organizationId.equals(returnedOrganizationId)) {
            throw new IllegalStateException(
                    "GNM incident GET returned unexpected organizationId"
            );
        }

        String incidentStatus =
                requiredText(
                        result,
                        "incidentStatus"
                );

        JsonNode phaseStatus =
                requiredObject(
                        result,
                        "phaseStatus"
                );

        String phaseName =
                requiredText(
                        phaseStatus,
                        "name"
                );

        String phaseNodeType =
                requiredText(
                        phaseStatus,
                        "phaseNodeType"
                );

        JsonNode phases =
                result.get("incidentPhases");

        if (phases == null ||
                !phases.isArray() ||
                phases.isEmpty()) {
            throw new IllegalStateException(
                    "GNM incident GET response has no incidentPhases"
            );
        }

        JsonNode matchingPhase = null;

        for (JsonNode phase : phases) {
            if (phaseName.equalsIgnoreCase(
                    phase.path("name").asText(""))) {

                if (matchingPhase != null) {
                    throw new IllegalStateException(
                            "GNM incident GET contains ambiguous phase: "
                                    + phaseName
                    );
                }

                matchingPhase = phase;
            }
        }

        if (matchingPhase == null) {
            throw new IllegalStateException(
                    "GNM incident GET has no phase matching phaseStatus: "
                            + phaseName
            );
        }

        int phaseId =
                requiredInteger(
                        matchingPhase,
                        "id"
                );

        String notificationId =
                requiredText(
                        matchingPhase,
                        "notificationId"
                );

        return new GnmIncidentSnapshot(
                returnedIncidentId,
                returnedOrganizationId,
                incidentStatus,
                phaseId,
                phaseName,
                phaseNodeType,
                notificationId
        );
    }

    private static JsonNode requiredObject(
            JsonNode node,
            String field
    ) {
        JsonNode value = node.get(field);

        if (value == null ||
                !value.isObject()) {
            throw new IllegalStateException(
                    "Required GNM provider field missing or invalid: "
                            + field
            );
        }

        return value;
    }

    private static String requiredText(
            JsonNode node,
            String field
    ) {
        JsonNode value = node.get(field);

        if (value == null ||
                value.isNull() ||
                value.asText().isBlank()) {
            throw new IllegalStateException(
                    "Required GNM provider field missing: "
                            + field
            );
        }

        return value.asText().trim();
    }

    private static int requiredInteger(
            JsonNode node,
            String field
    ) {
        JsonNode value = node.get(field);

        if (value == null ||
                !value.isIntegralNumber()) {
            throw new IllegalStateException(
                    "Required GNM provider integer missing: "
                            + field
            );
        }

        return value.asInt();
    }
}
