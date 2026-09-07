package com.eventmanagement.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.camel.Exchange;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.support.DefaultExchange;
import org.junit.jupiter.api.Test;

class GnmProviderCheckpointProcessorTest {

    private final ObjectMapper objectMapper =
            new ObjectMapper();

    @Test
    void persistsProviderIdentityBeforeReconciliation()
            throws Exception {

        RecordingLedger ledger =
                new RecordingLedger();

        GnmProviderCheckpointProcessor processor =
                new GnmProviderCheckpointProcessor(
                        ledger,
                        objectMapper
                );

        Exchange exchange =
                new DefaultExchange(
                        new DefaultCamelContext()
                );

        exchange.setProperty(
                "commandId",
                "cmd-gnm-checkpoint-001"
        );

        exchange.setProperty(
                "integrationIdempotencyClaimOwner",
                "owner-001"
        );

        exchange.setProperty(
                "gnmOrganizationId",
                "453003085618991"
        );

        exchange.setProperty(
                "gnmIncidentId",
                "2713046552385248"
        );

        processor.process(exchange);

        assertEquals(
                "cmd-gnm-checkpoint-001",
                ledger.commandId
        );

        assertEquals(
                "owner-001",
                ledger.claimOwner
        );

        JsonNode checkpoint =
                objectMapper.readTree(
                        ledger.providerCheckpoint
                );

        assertEquals(
                "EVERBRIDGE",
                checkpoint.path("provider").asText()
        );

        assertEquals(
                "453003085618991",
                checkpoint.path("organizationId").asText()
        );

        assertEquals(
                "2713046552385248",
                checkpoint.path("incidentId").asText()
        );

        assertEquals(
                "LAUNCH_ACCEPTED",
                checkpoint.path("lifecycleState").asText()
        );

        assertEquals(
                ledger.providerCheckpoint,
                exchange.getProperty(
                        "integrationProviderCheckpoint"
                )
        );
    }

    @Test
    void persistsCloseAcceptedProviderCheckpoint()
            throws Exception {

        RecordingLedger ledger =
                new RecordingLedger();

        GnmProviderCheckpointProcessor processor =
                new GnmProviderCheckpointProcessor(
                        ledger,
                        objectMapper
                );

        Exchange exchange =
                new DefaultExchange(
                        new DefaultCamelContext()
                );

        exchange.setProperty(
                "commandId",
                "cmd-gnm-close-checkpoint-001"
        );

        exchange.setProperty(
                "integrationIdempotencyClaimOwner",
                "owner-close-001"
        );

        exchange.setProperty(
                "operation",
                "CLOSE_NOTIFICATION"
        );

        exchange.setProperty(
                "gnmOrganizationId",
                "453003085618991"
        );

        exchange.setProperty(
                "gnmIncidentId",
                "2713046552385248"
        );

        exchange.setProperty(
                "gnmCloseLifecycleState",
                "CLOSE_ACCEPTED"
        );

        processor.process(exchange);

        JsonNode checkpoint =
                objectMapper.readTree(
                        ledger.providerCheckpoint
                );

        assertEquals(
                "CLOSE_ACCEPTED",
                checkpoint.path(
                        "lifecycleState"
                ).asText()
        );

        assertEquals(
                "2713046552385248",
                checkpoint.path(
                        "incidentId"
                ).asText()
        );

        assertEquals(
                "453003085618991",
                checkpoint.path(
                        "organizationId"
                ).asText()
        );
    }


    @Test
    void rejectsMissingIncidentIdBeforeLedgerMutation() {

        RecordingLedger ledger =
                new RecordingLedger();

        GnmProviderCheckpointProcessor processor =
                new GnmProviderCheckpointProcessor(
                        ledger,
                        objectMapper
                );

        Exchange exchange =
                new DefaultExchange(
                        new DefaultCamelContext()
                );

        exchange.setProperty(
                "commandId",
                "cmd-gnm-checkpoint-002"
        );

        exchange.setProperty(
                "integrationIdempotencyClaimOwner",
                "owner-002"
        );

        exchange.setProperty(
                "gnmOrganizationId",
                "453003085618991"
        );

        assertThrows(
                IllegalArgumentException.class,
                () -> processor.process(exchange)
        );

        assertEquals(
                0,
                ledger.checkpointCalls
        );
    }

    private static final class RecordingLedger
            implements IntegrationCommandLedger {

        private int checkpointCalls;
        private String commandId;
        private String claimOwner;
        private String providerCheckpoint;

        @Override
        public Claim claim(JsonNode command) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void checkpointProvider(
                String commandId,
                String claimOwner,
                String providerCheckpoint
        ) {
            checkpointCalls++;
            this.commandId = commandId;
            this.claimOwner = claimOwner;
            this.providerCheckpoint =
                    providerCheckpoint;
        }

        @Override
        public void complete(
                String commandId,
                String claimOwner,
                String resultPayload
        ) {
            throw new UnsupportedOperationException();
        }
    }
}
