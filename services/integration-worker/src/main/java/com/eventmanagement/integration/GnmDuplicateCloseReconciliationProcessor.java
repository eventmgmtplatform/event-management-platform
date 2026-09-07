package com.eventmanagement.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;

/**
 * Resolves an ambiguous/duplicate CloseWithNotification response
 * through authoritative GET only.
 *
 * <p>This processor MUST NOT issue another PUT or POST. It is used
 * when the single CLOSE mutation returned
 * RECONCILIATION_REQUIRED.</p>
 */
@Named("gnmDuplicateCloseReconciliationProcessor")
@ApplicationScoped
public class GnmDuplicateCloseReconciliationProcessor
        implements Processor {

    private final GnmIncidentLookupClient lookupClient;
    private final ObjectMapper objectMapper;

    @Inject
    public GnmDuplicateCloseReconciliationProcessor(
            GnmIncidentLookupClient lookupClient,
            ObjectMapper objectMapper
    ) {
        this.lookupClient = lookupClient;
        this.objectMapper = objectMapper;
    }

    @Override
    public void process(Exchange exchange) throws Exception {

        GnmRetryExecutor.ExecutionResult execution =
                exchange.getProperty(
                        "gnmCloseExecutionResult",
                        GnmRetryExecutor.ExecutionResult.class
                );

        if (execution == null) {
            throw new IllegalStateException(
                    "GNM Close execution result unavailable"
            );
        }

        if (execution.outcome()
                != GnmRetryExecutor.Outcome.RECONCILIATION_REQUIRED) {

            throw new IllegalStateException(
                    "GNM duplicate Close reconciliation requires "
                            + "RECONCILIATION_REQUIRED outcome"
            );
        }

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

        GnmIncidentSnapshot snapshot =
                lookupClient.getIncident(
                        organizationId,
                        incidentId
                );

        if (!organizationId.equals(snapshot.organizationId())) {
            throw new IllegalStateException(
                    "GNM reconciliation organizationId mismatch"
            );
        }

        if (!incidentId.equals(snapshot.incidentId())) {
            throw new IllegalStateException(
                    "GNM reconciliation incidentId mismatch"
            );
        }

        if (!snapshot.closedConfirmed()) {
            throw new IllegalStateException(
                    "GNM duplicate Close reconciliation "
                            + "did not confirm CLOSED state"
            );
        }

        ObjectNode result =
                objectMapper.createObjectNode();

        result.put(
                "commandId",
                requiredProperty(exchange, "commandId")
        );

        result.put(
                "eventId",
                requiredProperty(exchange, "eventId")
        );

        result.put(
                "integrationType",
                "GNM"
        );

        result.put(
                "operation",
                "CLOSE_NOTIFICATION"
        );

        result.put(
                "status",
                "SUCCESS"
        );

        result.put(
                "provider",
                "EVERBRIDGE"
        );

        ObjectNode identity =
                result.putObject(
                        "providerNotificationIdentity"
                );

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

        if (snapshot.notificationId() != null
                && !snapshot.notificationId().isBlank()) {

            identity.put(
                    "closeNotificationId",
                    snapshot.notificationId()
            );
        }

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
