package com.eventmanagement.state;

import com.fasterxml.jackson.databind.JsonNode;

import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

public class ConsolidatedEventState {

    public String eventKey;
    public String eventId;
    public String tenant;
    public String lifecycleStatus;

    public String ticketNumber;
    public String notificationId;
    public String automationId;

    public String servicenowStatus;
    public String gnmStatus;
    public String cacfStatus;

    public Map<String, JsonNode> integrations =
            new LinkedHashMap<>();

    public OffsetDateTime firstSeenAt;
    public OffsetDateTime lastUpdatedAt;

    public long version;
}
