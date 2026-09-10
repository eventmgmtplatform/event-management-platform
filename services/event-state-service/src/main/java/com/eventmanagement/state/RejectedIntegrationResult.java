package com.eventmanagement.state;

/** Permanent input rejection; persistence/network errors must never use this type. */
public final class RejectedIntegrationResult extends IllegalArgumentException {
    public RejectedIntegrationResult(String reason) { super(reason); }
}
