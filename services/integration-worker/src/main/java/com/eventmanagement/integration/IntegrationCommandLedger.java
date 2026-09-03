package com.eventmanagement.integration;

import com.fasterxml.jackson.databind.JsonNode;

public interface IntegrationCommandLedger {

    enum Decision {
        EXECUTE,
        REPLAY,
        IN_PROGRESS
    }

    record Claim(
            Decision decision,
            String resultPayload
    ) {
    }

    Claim claim(JsonNode command) throws Exception;

    void complete(
            String commandId,
            String resultPayload
    ) throws Exception;
}
