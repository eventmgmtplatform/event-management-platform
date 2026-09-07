package com.eventmanagement.integration;

public interface GnmIncidentLookupClient {

    GnmIncidentSnapshot getIncident(
            String organizationId,
            String incidentId
    ) throws Exception;
}
