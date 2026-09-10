package com.eventmanagement.integration;

import com.fasterxml.jackson.databind.JsonNode;
import org.apache.camel.Exchange;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.support.DefaultExchange;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class IntegrationCommandCompletionProcessorTest {

    @Test
    void shouldPersistTerminalResultForOwnedExecution()
            throws Exception {

        RecordingLedger ledger =
                new RecordingLedger();

        Exchange exchange = terminalExchange(
                IntegrationCommandLedger.Decision.EXECUTE
        );

        new IntegrationCommandCompletionProcessor(
                ledger
        ).process(exchange);

        assertEquals(
                "cmd-idempotency-001",
                ledger.completedCommandId
        );

        assertEquals(
                """
                {"commandId":"cmd-idempotency-001","status":"SUCCESS"}""",
                ledger.completedResult
        );
    }

    @Test
    void shouldNotCompleteReplayedExecution()
            throws Exception {

        RecordingLedger ledger =
                new RecordingLedger();

        Exchange exchange = terminalExchange(
                IntegrationCommandLedger.Decision.REPLAY
        );

        new IntegrationCommandCompletionProcessor(
                ledger
        ).process(exchange);

        assertNull(ledger.completedCommandId);
        assertNull(ledger.completedResult);
    }

    private Exchange terminalExchange(
            IntegrationCommandLedger.Decision decision
    ) {

        Exchange exchange =
                new DefaultExchange(
                        new DefaultCamelContext()
                );

        exchange.setProperty(
                "commandId",
                "cmd-idempotency-001"
        );

        exchange.setProperty(
                IntegrationCommandClaimProcessor
                        .DECISION_PROPERTY,
                decision.name()
        );

        exchange.setProperty(
                IntegrationCommandClaimProcessor.CLAIM_OWNER_PROPERTY,
                "owner-completion"
        );

        exchange.getMessage().setBody(
                """
                {"commandId":"cmd-idempotency-001","status":"SUCCESS"}"""
        );

        return exchange;
    }

    private static final class RecordingLedger
            implements IntegrationCommandLedger {

        private String completedCommandId;
        private String completedOwner;
        private String completedResult;

        @Override
        public Claim claim(JsonNode command) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void complete(
                String commandId,
                String claimOwner,
                String resultPayload
        ) {

            completedCommandId = commandId;
            completedOwner = claimOwner;
            completedResult = resultPayload;
        }
    }
}
