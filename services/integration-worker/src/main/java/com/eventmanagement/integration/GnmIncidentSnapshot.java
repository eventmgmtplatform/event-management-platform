package com.eventmanagement.integration;

import java.util.Objects;

/**
 * Provider-state snapshot obtained from authoritative Everbridge GET.
 *
 * <p>This model represents observed provider state. It does not itself
 * decide whether a mutation should be retried or whether a command should
 * be marked complete.</p>
 */
public record GnmIncidentSnapshot(
        String incidentId,
        String organizationId,
        String incidentStatus,
        int phaseId,
        String phaseName,
        String phaseNodeType,
        String notificationId
) {
    public GnmIncidentSnapshot {
        Objects.requireNonNull(incidentId, "incidentId");
        Objects.requireNonNull(organizationId, "organizationId");
        Objects.requireNonNull(incidentStatus, "incidentStatus");
        Objects.requireNonNull(phaseName, "phaseName");
        Objects.requireNonNull(phaseNodeType, "phaseNodeType");
        Objects.requireNonNull(notificationId, "notificationId");
    }

    public boolean openConfirmed() {
        return "Open".equalsIgnoreCase(incidentStatus)
                && "New".equalsIgnoreCase(phaseName)
                && "Begin".equalsIgnoreCase(phaseNodeType);
    }

    public boolean closedConfirmed() {
        return "Closed".equalsIgnoreCase(incidentStatus)
                && "Close".equalsIgnoreCase(phaseName)
                && "End".equalsIgnoreCase(phaseNodeType);
    }
}
