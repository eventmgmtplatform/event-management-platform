package com.eventmanagement.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.jboss.logging.Logger;

@Named("gnmReconciliationProcessor")
@ApplicationScoped
public class GnmReconciliationProcessor implements Processor {

    public static final String OUTCOME_PROPERTY =
            "gnmReconciliationOutcome";

    private static final Logger LOG =
            Logger.getLogger(GnmReconciliationProcessor.class);

    private final GnmIncidentLookupClient lookupClient;
    private final ObjectMapper objectMapper;

    @Inject
    public GnmReconciliationProcessor(
            GnmIncidentLookupClient lookupClient,
            ObjectMapper objectMapper
    ) {
        this.lookupClient = lookupClient;
        this.objectMapper = objectMapper;
    }

    @Override
    public void process(Exchange exchange) throws Exception {

        exchange.removeProperty(OUTCOME_PROPERTY);

        String commandId =
                requiredProperty(exchange, "commandId");

        String eventId =
                requiredProperty(exchange, "eventId");

        String checkpointText =
                requiredProperty(
                        exchange,
                        "integrationProviderCheckpoint"
                );

        JsonNode checkpoint =
                objectMapper.readTree(checkpointText);

        if (checkpoint == null || !checkpoint.isObject()) {
            throw new IllegalStateException(
                    "GNM provider checkpoint must be a JSON object"
            );
        }

        String provider =
                requiredText(checkpoint, "provider");

        if (!"EVERBRIDGE".equals(provider)) {
            throw new IllegalStateException(
                    "Unsupported GNM checkpoint provider: "
                            + provider
            );
        }

        String organizationId =
                requiredText(
                        checkpoint,
                        "organizationId"
                );

        String incidentId =
                requiredText(
                        checkpoint,
                        "incidentId"
                );

        String lifecycleState =
                requiredText(
                        checkpoint,
                        "lifecycleState"
                );

        if (!"LAUNCH_ACCEPTED".equals(lifecycleState)
                && !"CLOSE_ACCEPTED".equals(lifecycleState)) {

            throw new IllegalStateException(
                    "GNM reconciliation requires "
                            + "LAUNCH_ACCEPTED or CLOSE_ACCEPTED checkpoint"
            );
        }

        /*
         * Reconciliation is intentionally GET-only.
         *
         * No provider mutation is permitted from this processor.
         * This protects both Launch and Close from duplicate mutations
         * after a crash, timeout, ambiguous provider response, or
         * expired durable claim takeover.
         */
        GnmIncidentSnapshot snapshot;

        try {
            snapshot =
                    lookupClient.getIncident(
                            organizationId,
                            incidentId
                    );
        } catch (Exception exception) {

            exchange.setProperty(
                    OUTCOME_PROPERTY,
                    "RETRY_LATER"
            );

            LOG.warnv(
                    "GNM reconciliation GET deferred: "
                            + "commandId={0}, incidentId={1}, "
                            + "lifecycleState={2}, error={3}",
                    commandId,
                    incidentId,
                    lifecycleState,
                    exception.getClass().getSimpleName()
            );

            return;
        }

        if ("LAUNCH_ACCEPTED".equals(lifecycleState)) {

            reconcileLaunch(
                    exchange,
                    commandId,
                    eventId,
                    incidentId,
                    snapshot
            );

            return;
        }

        reconcileClose(
                exchange,
                commandId,
                eventId,
                incidentId,
                snapshot
        );
    }

    private void reconcileLaunch(
            Exchange exchange,
            String commandId,
            String eventId,
            String incidentId,
            GnmIncidentSnapshot snapshot
    ) {

        if (!snapshot.openConfirmed()) {

            exchange.setProperty(
                    OUTCOME_PROPERTY,
                    "NOT_OPEN"
            );

            LOG.warnv(
                    "GNM reconciliation did not confirm OPEN: "
                            + "commandId={0}, incidentId={1}, status={2}",
                    commandId,
                    incidentId,
                    snapshot.incidentStatus()
            );

            return;
        }

        ObjectNode identity =
                objectMapper.createObjectNode();

        identity.put(
                "incidentId",
                incidentId
        );

        identity.put(
                "incidentStatus",
                snapshot.incidentStatus()
        );

        identity.put(
                "lifecycleState",
                "OPEN_CONFIRMED"
        );

        if (snapshot.notificationId() != null
                && !snapshot.notificationId().isBlank()) {

            identity.put(
                    "openNotificationId",
                    snapshot.notificationId()
            );
        }

        ObjectNode result =
                baseResult(
                        commandId,
                        eventId,
                        "SEND_NOTIFICATION"
                );

        result.set(
                "providerNotificationIdentity",
                identity
        );

        exchange.getMessage().setBody(result);

        exchange.setProperty(
                OUTCOME_PROPERTY,
                "OPEN_CONFIRMED"
        );

        LOG.infov(
                "GNM durable reconciliation confirmed OPEN "
                        + "without POST: commandId={0}, incidentId={1}",
                commandId,
                incidentId
        );
    }

    private void reconcileClose(
            Exchange exchange,
            String commandId,
            String eventId,
            String incidentId,
            GnmIncidentSnapshot snapshot
    ) {

        if (!snapshot.closedConfirmed()) {

            exchange.setProperty(
                    OUTCOME_PROPERTY,
                    "NOT_CLOSED"
            );

            LOG.warnv(
                    "GNM reconciliation did not confirm CLOSED: "
                            + "commandId={0}, incidentId={1}, status={2}",
                    commandId,
                    incidentId,
                    snapshot.incidentStatus()
            );

            return;
        }

        ObjectNode identity =
                objectMapper.createObjectNode();

        identity.put(
                "incidentId",
                incidentId
        );

        identity.put(
                "incidentStatus",
                snapshot.incidentStatus()
        );

        identity.put(
                "lifecycleState",
                "CLOSED_CONFIRMED"
        );

        /*
         * notificationId returned by the authoritative Closed snapshot
         * belongs to the Close phase. It must never be inferred from PUT.
         */
        if (snapshot.notificationId() != null
                && !snapshot.notificationId().isBlank()) {

            identity.put(
                    "closeNotificationId",
                    snapshot.notificationId()
            );
        }

        ObjectNode result =
                baseResult(
                        commandId,
                        eventId,
                        "CLOSE_NOTIFICATION"
                );

        result.set(
                "providerNotificationIdentity",
                identity
        );

        exchange.getMessage().setBody(result);

        exchange.setProperty(
                OUTCOME_PROPERTY,
                "CLOSED_CONFIRMED"
        );

        LOG.infov(
                "GNM durable reconciliation confirmed CLOSED "
                        + "without PUT: commandId={0}, incidentId={1}",
                commandId,
                incidentId
        );
    }

    private ObjectNode baseResult(
            String commandId,
            String eventId,
            String operation
    ) {

        ObjectNode result =
                objectMapper.createObjectNode();

        result.put(
                "commandId",
                commandId
        );

        result.put(
                "eventId",
                eventId
        );

        result.put(
                "integrationType",
                "GNM"
        );

        result.put(
                "operation",
                operation
        );

        result.put(
                "status",
                "SUCCESS"
        );

        result.put(
                "provider",
                "EVERBRIDGE"
        );

        return result;
    }

    private static String requiredProperty(
            Exchange exchange,
            String propertyName
    ) {

        String value =
                exchange.getProperty(
                        propertyName,
                        String.class
                );

        if (value == null || value.isBlank()) {
            throw new IllegalStateException(
                    "Missing required exchange property: "
                            + propertyName
            );
        }

        return value;
    }

    private static String requiredText(
            JsonNode node,
            String fieldName
    ) {

        JsonNode value =
                node.get(fieldName);

        if (value == null
                || value.isNull()
                || !value.isTextual()
                || value.asText().isBlank()) {

            throw new IllegalStateException(
                    "Missing required GNM checkpoint field: "
                            + fieldName
            );
        }

        return value.asText();
    }
}
