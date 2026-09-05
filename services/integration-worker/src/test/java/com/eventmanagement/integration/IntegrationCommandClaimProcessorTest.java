package com.eventmanagement.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.camel.Exchange;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.support.DefaultExchange;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class IntegrationCommandClaimProcessorTest {

    private final ObjectMapper objectMapper =
            new ObjectMapper();

    @Test
    void shouldAcquireNewCommandForExecution()
            throws Exception {

        StubLedger ledger = new StubLedger(
                new IntegrationCommandLedger.Claim(
                        IntegrationCommandLedger.Decision.EXECUTE,
                        null,
                        "owner-execute"
                )
        );

        Exchange exchange = exchangeWithCommand();

        new IntegrationCommandClaimProcessor(ledger)
                .process(exchange);

        assertEquals(
                "EXECUTE",
                exchange.getProperty(
                        IntegrationCommandClaimProcessor
                                .DECISION_PROPERTY,
                        String.class
                )
        );

        assertEquals(
                "service-now-request",
                exchange.getMessage().getBody(String.class)
        );
    }

    @Test
    void shouldReplayStoredTerminalResult()
            throws Exception {

        String result =
                """
                {
                  "commandId": "cmd-idempotency-001",
                  "status": "SUCCESS"
                }
                """;

        StubLedger ledger = new StubLedger(
                new IntegrationCommandLedger.Claim(
                        IntegrationCommandLedger.Decision.REPLAY,
                        result,
                        null
                )
        );

        Exchange exchange = exchangeWithCommand();

        new IntegrationCommandClaimProcessor(ledger)
                .process(exchange);

        assertEquals(
                "REPLAY",
                exchange.getProperty(
                        IntegrationCommandClaimProcessor
                                .DECISION_PROPERTY,
                        String.class
                )
        );

        assertEquals(
                "SUCCESS",
                objectMapper.readTree(
                        exchange.getMessage()
                                .getBody(String.class)
                ).path("status").asText()
        );
    }

    @Test
    void shouldMarkConcurrentDuplicateInProgress()
            throws Exception {

        StubLedger ledger = new StubLedger(
                new IntegrationCommandLedger.Claim(
                        IntegrationCommandLedger.Decision.IN_PROGRESS,
                        null,
                        null
                )
        );

        Exchange exchange = exchangeWithCommand();

        new IntegrationCommandClaimProcessor(ledger)
                .process(exchange);

        assertEquals(
                "IN_PROGRESS",
                exchange.getProperty(
                        IntegrationCommandClaimProcessor
                                .DECISION_PROPERTY,
                        String.class
                )
        );
    }

    @Test
    void shouldRemainUnresolvedWhenClaimFails()
            throws Exception {

        IntegrationCommandLedger ledger =
                new IntegrationCommandLedger() {
                    @Override
                    public Claim claim(JsonNode command) {
                        throw new IllegalStateException(
                                "commandId collision"
                        );
                    }

                    @Override
                    public void complete(
                            String commandId,
                            String claimOwner,
                            String resultPayload
                    ) {
                    }
                };

        Exchange exchange = exchangeWithCommand();

        assertThrows(
                IllegalStateException.class,
                () -> new IntegrationCommandClaimProcessor(
                        ledger
                ).process(exchange)
        );

        assertEquals(
                "UNRESOLVED",
                exchange.getProperty(
                        IntegrationCommandClaimProcessor
                                .DECISION_PROPERTY,
                        String.class
                )
        );
    }

    private Exchange exchangeWithCommand()
            throws Exception {

        JsonNode command =
                objectMapper.readTree("""
                        {
                          "commandId": "cmd-idempotency-001",
                          "eventId": "evt-idempotency-001",
                          "eventKey": "lab|idempotency|001",
                          "tenant": "LAB",
                          "integrationType": "SERVICENOW",
                          "operation": "CREATE_TICKET",
                          "payload": {
                            "resource": "ubuntu-local",
                            "summary": "Idempotency test",
                            "severity": 4
                          }
                        }
                        """);

        Exchange exchange =
                new DefaultExchange(
                        new DefaultCamelContext()
                );

        exchange.setProperty(
                "originalIntegrationCommand",
                command
        );

        exchange.setProperty(
                "commandId",
                "cmd-idempotency-001"
        );

        exchange.getMessage().setBody(
                "service-now-request"
        );

        return exchange;
    }

    private static final class StubLedger
            implements IntegrationCommandLedger {

        private final Claim claim;

        private StubLedger(Claim claim) {
            this.claim = claim;
        }

        @Override
        public Claim claim(JsonNode command) {
            return claim;
        }

        @Override
        public void complete(
                String commandId,
                String claimOwner,
                String resultPayload
        ) {
        }
    }
}
