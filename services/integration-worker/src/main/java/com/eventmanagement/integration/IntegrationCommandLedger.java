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
            String claimOwner
    ) {
    }

    Claim claim(JsonNode command) throws Exception;

    void complete(
            String commandId,
            String claimOwner,
            String resultPayload
    ) throws Exception;
}
