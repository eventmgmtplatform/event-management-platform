package com.eventmanagement.integration;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.jboss.logging.Logger;

@Named("integrationCommandClaimProcessor")
@ApplicationScoped
public class IntegrationCommandClaimProcessor
        implements Processor {

    public static final String DECISION_PROPERTY =
            "integrationIdempotencyDecision";

    public static final String CLAIM_OWNER_PROPERTY =
            "integrationIdempotencyClaimOwner";

    private static final Logger LOG =
            Logger.getLogger(
                    IntegrationCommandClaimProcessor.class
            );

    private final IntegrationCommandLedger ledger;

    @Inject
    public IntegrationCommandClaimProcessor(
            IntegrationCommandLedger ledger
    ) {
        this.ledger = ledger;
    }

    @Override
    public void process(Exchange exchange)
            throws Exception {

        /*
         * A sentinel prevents completion when claiming fails, for
         * example because the commandId collides with another payload.
         */
        exchange.setProperty(
                DECISION_PROPERTY,
                "UNRESOLVED"
        );
        exchange.removeProperty(CLAIM_OWNER_PROPERTY);
        exchange.removeProperty("integrationProviderCheckpoint");

        JsonNode command =
                exchange.getProperty(
                        "originalIntegrationCommand",
                        JsonNode.class
                );

        if (command == null || !command.isObject()) {
            throw new IllegalStateException(
                    "Validated integration command is unavailable"
            );
        }

        IntegrationCommandLedger.Claim claim =
                ledger.claim(command);

        String decision =
                claim.decision().name();

        exchange.setProperty(
                DECISION_PROPERTY,
                decision
        );

        String commandId =
                exchange.getProperty(
                        "commandId",
                        String.class
                );

        if (claim.decision() == IntegrationCommandLedger.Decision.EXECUTE ||
                claim.decision() == IntegrationCommandLedger.Decision.RECONCILE) {
            String claimOwner = claim.claimOwner();
            if (claimOwner == null || claimOwner.isBlank()) {
                throw new IllegalStateException(
                        "Owned idempotency decision has no claim owner: " + commandId
                );
            }
            exchange.setProperty(CLAIM_OWNER_PROPERTY, claimOwner);

            if (claim.decision() ==
                    IntegrationCommandLedger.Decision.RECONCILE &&
                    claim.providerCheckpoint() != null &&
                    !claim.providerCheckpoint().isBlank()) {

                exchange.setProperty(
                        "integrationProviderCheckpoint",
                        claim.providerCheckpoint()
                );
            }
        }

        switch (claim.decision()) {
            case EXECUTE -> LOG.infov(
                    "Idempotency claim acquired: " +
                    "commandId={0}, decision=EXECUTE",
                    commandId
            );

            case RECONCILE -> LOG.warnv(
                    "Expired idempotency claim acquired for reconciliation: " +
                    "commandId={0}, decision=RECONCILE",
                    commandId
            );

            case REPLAY -> {
                String resultPayload =
                        claim.resultPayload();

                if (resultPayload == null ||
                        resultPayload.isBlank()) {

                    throw new IllegalStateException(
                            "Replay decision has no terminal result: " +
                            commandId
                    );
                }

                exchange.getMessage().setBody(
                        resultPayload
                );

                LOG.infov(
                        "Idempotent result replay: " +
                        "commandId={0}, decision=REPLAY",
                        commandId
                );
            }

            case IN_PROGRESS -> LOG.infov(
                    "Concurrent duplicate suppressed: " +
                    "commandId={0}, decision=IN_PROGRESS",
                    commandId
            );
        }
    }
}
