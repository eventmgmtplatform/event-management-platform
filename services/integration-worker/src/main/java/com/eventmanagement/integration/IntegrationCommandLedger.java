package com.eventmanagement.integration;

import com.fasterxml.jackson.databind.JsonNode;

public interface IntegrationCommandLedger {

    enum Decision {
        EXECUTE,
        REPLAY,
        IN_PROGRESS,
        RECONCILE
    }

    record Claim(
            Decision decision,
            String resultPayload,
            String claimOwner,
            String providerCheckpoint
    ) {
        Claim(
                Decision decision,
                String resultPayload,
                String claimOwner
        ) {
            this(
                    decision,
                    resultPayload,
                    claimOwner,
                    null
            );
        }
    }

    Claim claim(JsonNode command) throws Exception;

    default void checkpointProvider(
            String commandId,
            String claimOwner,
            String providerCheckpoint
    ) throws Exception {
        throw new UnsupportedOperationException(
                "Provider checkpoint is not supported by this ledger implementation"
        );
    }

    void complete(
            String commandId,
            String claimOwner,
            String resultPayload
    ) throws Exception;
}
