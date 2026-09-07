package com.eventmanagement.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Named;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;

@Named("gnmProviderCheckpointProcessor")
@ApplicationScoped
public class GnmProviderCheckpointProcessor implements Processor {

    private static final String CLAIM_OWNER_PROPERTY =
            "integrationIdempotencyClaimOwner";

    private final IntegrationCommandLedger ledger;
    private final ObjectMapper objectMapper;

    public GnmProviderCheckpointProcessor(
            IntegrationCommandLedger ledger,
            ObjectMapper objectMapper
    ) {
        this.ledger = ledger;
        this.objectMapper = objectMapper;
    }

    @Override
    public void process(Exchange exchange) throws Exception {

        String commandId =
                requiredProperty(
                        exchange,
                        "commandId"
                );

        String claimOwner =
                requiredProperty(
                        exchange,
                        CLAIM_OWNER_PROPERTY
                );

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

        ObjectNode checkpoint =
                objectMapper.createObjectNode();

        checkpoint.put(
                "provider",
                "EVERBRIDGE"
        );

        checkpoint.put(
                "organizationId",
                organizationId
        );

        checkpoint.put(
                "incidentId",
                incidentId
        );

        String lifecycleState =
                resolveLifecycleState(exchange);

        checkpoint.put(
                "lifecycleState",
                lifecycleState
        );

        String serialized =
                objectMapper.writeValueAsString(
                        checkpoint
                );

        ledger.checkpointProvider(
                commandId,
                claimOwner,
                serialized
        );

        exchange.setProperty(
                "integrationProviderCheckpoint",
                serialized
        );
    }

    private String resolveLifecycleState(
            Exchange exchange
    ) {

        String operation =
                exchange.getProperty(
                        "operation",
                        String.class
                );

        if ("CLOSE_NOTIFICATION".equals(operation)) {

            String state =
                    exchange.getProperty(
                            "gnmCloseLifecycleState",
                            String.class
                    );

            if (!"CLOSE_ACCEPTED".equals(state)) {
                throw new IllegalStateException(
                        "GNM CLOSE provider checkpoint "
                                + "requires CLOSE_ACCEPTED state"
                );
            }

            return state;
        }

        String state =
                exchange.getProperty(
                        "gnmLaunchLifecycleState",
                        String.class
                );

        /*
         * Backward compatibility for the already-certified OPEN route:
         * historical callers may not explicitly materialize operation
         * before this processor.
         */
        if (state == null || state.isBlank()) {
            state = "LAUNCH_ACCEPTED";
        }

        if (!"LAUNCH_ACCEPTED".equals(state)) {
            throw new IllegalStateException(
                    "GNM OPEN provider checkpoint "
                            + "requires LAUNCH_ACCEPTED state"
            );
        }

        return state;
    }

    private String requiredProperty(
            Exchange exchange,
            String name
    ) {
        Object value =
                exchange.getProperty(name);

        if (value == null ||
                value.toString().isBlank()) {
            throw new IllegalArgumentException(
                    "Required exchange property missing: "
                            + name
            );
        }

        return value.toString();
    }
}
