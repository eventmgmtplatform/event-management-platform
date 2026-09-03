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

        if (!IntegrationCommandLedger.Decision.EXECUTE
                .name()
                .equals(decision)) {

            return;
        }

        String commandId =
                exchange.getProperty(
                        "commandId",
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

        if (resultPayload == null ||
                resultPayload.isBlank()) {

            throw new IllegalStateException(
                    "Terminal integration result is empty: " +
                    commandId
            );
        }

        ledger.complete(
                commandId,
                resultPayload
        );

        LOG.infov(
                "Terminal integration result persisted: " +
                "commandId={0}",
                commandId
        );
    }
}
