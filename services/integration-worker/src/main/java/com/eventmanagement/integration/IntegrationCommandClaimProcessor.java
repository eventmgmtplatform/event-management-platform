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

        switch (claim.decision()) {
            case EXECUTE -> LOG.infov(
                    "Idempotency claim acquired: " +
                    "commandId={0}, decision=EXECUTE",
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
