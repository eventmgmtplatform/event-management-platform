package com.eventmanagement.integration;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.jboss.logging.Logger;

@Named("integrationCommandCompletionProcessor")
@ApplicationScoped
public class IntegrationCommandCompletionProcessor
        implements Processor {

    private static final Logger LOG =
            Logger.getLogger(
                    IntegrationCommandCompletionProcessor.class
            );

    private final IntegrationCommandLedger ledger;

    @Inject
    public IntegrationCommandCompletionProcessor(
            IntegrationCommandLedger ledger
    ) {
        this.ledger = ledger;
    }

    @Override
    public void process(Exchange exchange)
            throws Exception {

        String decision =
                exchange.getProperty(
                        IntegrationCommandClaimProcessor
                                .DECISION_PROPERTY,
                        String.class
                );

        boolean ownedDecision =
                IntegrationCommandLedger.Decision.EXECUTE.name().equals(decision) ||
                IntegrationCommandLedger.Decision.RECONCILE.name().equals(decision);

        if (!ownedDecision) {
            return;
        }

        String commandId =
                exchange.getProperty(
                        "commandId",
                        String.class
                );

        String claimOwner =
                exchange.getProperty(
                        IntegrationCommandClaimProcessor.CLAIM_OWNER_PROPERTY,
                        String.class
                );

        String resultPayload =
                exchange.getMessage()
                        .getBody(String.class);

        if (commandId == null ||
                commandId.isBlank()) {

            throw new IllegalStateException(
                    "commandId unavailable during completion"
            );
        }

        if (claimOwner == null || claimOwner.isBlank()) {
            throw new IllegalStateException(
                    "claimOwner unavailable during completion: " + commandId
            );
        }

        if (resultPayload == null ||
                resultPayload.isBlank()) {

            throw new IllegalStateException(
                    "Terminal integration result is empty: " +
                    commandId
            );
        }

        ledger.complete(
                commandId,
                claimOwner,
                resultPayload
        );

        LOG.infov(
                "Terminal integration result persisted: " +
                "commandId={0}",
                commandId
        );
    }
}
