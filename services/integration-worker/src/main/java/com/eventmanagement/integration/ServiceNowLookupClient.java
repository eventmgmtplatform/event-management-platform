package com.eventmanagement.integration;

import com.fasterxml.jackson.databind.JsonNode;

public interface ServiceNowLookupClient {

    enum Status {
        FOUND,
        NOT_FOUND
    }

    record LookupResult(
            Status status,
            JsonNode ticket
    ) {
    }

    LookupResult findByEventId(String eventId) throws Exception;
}
