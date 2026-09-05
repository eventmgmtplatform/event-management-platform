package com.eventmanagement.integration;

import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class IntegrationOperationalState {

    private volatile IntegrationOperatingMode mode =
            IntegrationOperatingMode.STANDBY;
    private volatile boolean admissionOpen;
    private volatile boolean recoveryComplete;
    private volatile String errorType = "none";

    public IntegrationOperatingMode mode() {
        return mode;
    }

    public boolean admissionOpen() {
        return admissionOpen;
    }

    public boolean recoveryComplete() {
        return recoveryComplete;
    }

    public String errorType() {
        return errorType;
    }

    public synchronized void active() {
        mode = IntegrationOperatingMode.ACTIVE;
        recoveryComplete = true;
        admissionOpen = true;
        errorType = "none";
    }

    public synchronized void standby() {
        mode = IntegrationOperatingMode.STANDBY;
        admissionOpen = false;
        recoveryComplete = false;
        errorType = "none";
    }

    public synchronized void pullRestartPending() {
        mode = IntegrationOperatingMode.PULL_RESTART;
        admissionOpen = false;
        recoveryComplete = false;
        errorType = "none";
    }

    public synchronized void failure(Throwable exception) {
        admissionOpen = false;
        recoveryComplete = false;
        errorType = exception == null
                ? "Unknown"
                : exception.getClass().getSimpleName();
    }
}
