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
 * Confirms authoritative Everbridge OPEN state after a successful Launch.
 *
 * <p>This processor performs no provider mutation. It enriches the
 * LAUNCH_ACCEPTED result only after GET confirms the provider lifecycle
 * state as Open/New/Begin.</p>
 */
@Named("gnmOpenConfirmationProcessor")
@ApplicationScoped
public class GnmOpenConfirmationProcessor
        implements Processor {

    private final GnmIncidentLookupClient lookupClient;
    private final ObjectMapper objectMapper;

    @Inject
    public GnmOpenConfirmationProcessor(
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

        if (currentBody == null ||
                currentBody.isBlank()) {

            throw new IllegalStateException(
                    "GNM Launch result unavailable for OPEN confirmation"
            );
        }

        JsonNode parsed =
                objectMapper.readTree(currentBody);

        if (parsed == null ||
                !parsed.isObject()) {

            throw new IllegalStateException(
                    "GNM Launch result is invalid"
            );
        }

        ObjectNode result =
                ((ObjectNode) parsed).deepCopy();

        JsonNode identityNode =
                result.get(
                        "providerNotificationIdentity"
                );

        if (identityNode == null ||
                !identityNode.isObject()) {

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
                    "GNM Launch result incidentId does not match exchange identity"
            );
        }

        String lifecycleState =
                identity.path("lifecycleState")
                        .asText("")
                        .trim();

        if (!"LAUNCH_ACCEPTED".equals(
                lifecycleState)) {

            throw new IllegalStateException(
                    "GNM OPEN confirmation requires LAUNCH_ACCEPTED state"
            );
        }

        GnmIncidentSnapshot snapshot =
                lookupClient.getIncident(
                        organizationId,
                        incidentId
                );

        if (!snapshot.openConfirmed()) {

            throw new IllegalStateException(
                    "GNM provider did not confirm OPEN lifecycle state"
            );
        }

        identity.put(
                "openNotificationId",
                snapshot.notificationId()
        );

        identity.put(
                "incidentStatus",
                snapshot.incidentStatus()
        );

        identity.put(
                "lifecycleState",
                "OPEN_CONFIRMED"
        );

        exchange.setProperty(
                "gnmOpenNotificationId",
                snapshot.notificationId()
        );

        exchange.setProperty(
                "gnmIncidentStatus",
                snapshot.incidentStatus()
        );

        exchange.setProperty(
                "gnmLifecycleState",
                "OPEN_CONFIRMED"
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

        if (value == null ||
                value.isBlank()) {

            throw new IllegalStateException(
                    "Required GNM exchange property missing: "
                            + name
            );
        }

        return value.trim();
    }
}
