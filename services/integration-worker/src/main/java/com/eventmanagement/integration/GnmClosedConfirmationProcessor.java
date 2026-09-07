package com.eventmanagement.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;

/**
 * Confirms authoritative Everbridge CLOSED state after CloseWithNotification.
 *
 * <p>This processor performs GET only. Provider incidentStatus plus
 * Close/End phase semantics are lifecycle authority; incidentAction is not.</p>
 */
@Named("gnmClosedConfirmationProcessor")
@ApplicationScoped
public class GnmClosedConfirmationProcessor
        implements Processor {

    private final GnmIncidentLookupClient lookupClient;
    private final ObjectMapper objectMapper;

    @Inject
    public GnmClosedConfirmationProcessor(
            GnmIncidentLookupClient lookupClient,
            ObjectMapper objectMapper
    ) {
        this.lookupClient = lookupClient;
        this.objectMapper = objectMapper;
    }

    @Override
    public void process(Exchange exchange)
            throws Exception {

        String organizationId =
                requiredProperty(
                        exchange,
                        "gnmOrganizationId"
                );

        String incidentId =
                requiredProperty(
                        exchange,
                        "gnmIncidentId"
                );

        String currentBody =
                exchange.getMessage()
                        .getBody(String.class);

        if (currentBody == null
                || currentBody.isBlank()) {

            throw new IllegalStateException(
                    "GNM Close result unavailable "
                            + "for CLOSED confirmation"
            );
        }

        JsonNode parsed =
                objectMapper.readTree(currentBody);

        if (parsed == null
                || !parsed.isObject()) {

            throw new IllegalStateException(
                    "GNM Close result is invalid"
            );
        }

        ObjectNode result =
                ((ObjectNode) parsed).deepCopy();

        JsonNode identityNode =
                result.get(
                        "providerNotificationIdentity"
                );

        if (identityNode == null
                || !identityNode.isObject()) {

            throw new IllegalStateException(
                    "GNM provider notification identity unavailable"
            );
        }

        ObjectNode identity =
                (ObjectNode) identityNode;

        String bodyIncidentId =
                identity.path("incidentId")
                        .asText("")
                        .trim();

        if (!incidentId.equals(bodyIncidentId)) {
            throw new IllegalStateException(
                    "GNM Close result incidentId "
                            + "does not match exchange identity"
            );
        }

        String lifecycleState =
                identity.path("lifecycleState")
                        .asText("")
                        .trim();

        if (!"CLOSE_ACCEPTED".equals(
                lifecycleState)) {

            throw new IllegalStateException(
                    "GNM CLOSED confirmation "
                            + "requires CLOSE_ACCEPTED state"
            );
        }

        GnmIncidentSnapshot snapshot =
                lookupClient.getIncident(
                        organizationId,
                        incidentId
                );

        if (!incidentId.equals(
                snapshot.incidentId())) {

            throw new IllegalStateException(
                    "GNM provider snapshot incidentId mismatch"
            );
        }

        if (!organizationId.equals(
                snapshot.organizationId())) {

            throw new IllegalStateException(
                    "GNM provider snapshot organizationId mismatch"
            );
        }

        if (!snapshot.closedConfirmed()) {
            throw new IllegalStateException(
                    "GNM provider did not confirm CLOSED lifecycle state"
            );
        }

        identity.put(
                "closeNotificationId",
                snapshot.notificationId()
        );

        identity.put(
                "incidentStatus",
                snapshot.incidentStatus()
        );

        identity.put(
                "lifecycleState",
                "CLOSED_CONFIRMED"
        );

        exchange.setProperty(
                "gnmCloseNotificationId",
                snapshot.notificationId()
        );

        exchange.setProperty(
                "gnmIncidentStatus",
                snapshot.incidentStatus()
        );

        exchange.setProperty(
                "gnmLifecycleState",
                "CLOSED_CONFIRMED"
        );

        exchange.getMessage().setBody(
                objectMapper.writeValueAsString(result)
        );
    }

    private static String requiredProperty(
            Exchange exchange,
            String name
    ) {

        String value =
                exchange.getProperty(
                        name,
                        String.class
                );

        if (value == null || value.isBlank()) {
            throw new IllegalStateException(
                    "Required GNM exchange property missing: "
                            + name
            );
        }

        return value.trim();
    }
}
