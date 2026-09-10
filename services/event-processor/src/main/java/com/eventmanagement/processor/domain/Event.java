package com.eventmanagement.processor.domain;

import java.time.Instant;
import java.util.Objects;

/** Internal canonical view; the public gateway envelope is preserved verbatim. */
public record Event(String eventId, String eventKey, String tenant, Status status,
                    int severity, Instant receivedAt, String originalJson, java.util.Map<String,String> selectors) {
    public Event(String eventId,String eventKey,String tenant,Status status,int severity,Instant receivedAt,String originalJson) {
        this(eventId,eventKey,tenant,status,severity,receivedAt,originalJson,java.util.Map.of());
    }
    public enum Status { PROBLEM, OK, RESOLVED }
    public Event {
        selectors=java.util.Map.copyOf(selectors);
        if (eventId == null || eventId.isBlank() || eventKey == null || eventKey.isBlank())
            throw new IllegalArgumentException("EVENT_IDENTITY_REQUIRED");
        if (severity < 0 || severity > 5) throw new IllegalArgumentException("INVALID_SEVERITY");
        Objects.requireNonNull(status); Objects.requireNonNull(receivedAt);
        Objects.requireNonNull(originalJson); Objects.requireNonNull(tenant);
    }
    public boolean recovery() { return status == Status.OK || status == Status.RESOLVED; }
}
