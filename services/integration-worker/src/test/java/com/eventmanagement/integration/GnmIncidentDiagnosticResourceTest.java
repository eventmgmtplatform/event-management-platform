package com.eventmanagement.integration;

import jakarta.ws.rs.core.Response;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class GnmIncidentDiagnosticResourceTest {

    private static final String ORG =
            "453003085618991";

    private static final String INCIDENT =
            "2713046552385248";

    @Test
    void disabledSeamMustReturn404WithoutProviderCall()
            throws Exception {

        CountingLookup lookup =
                new CountingLookup();

        GnmIncidentDiagnosticResource resource =
                new GnmIncidentDiagnosticResource(
                        lookup,
                        false
                );

        try (Response response =
                     resource.getIncident(
                             ORG,
                             INCIDENT
                     )) {

            assertEquals(
                    404,
                    response.getStatus()
            );
        }

        assertEquals(
                0,
                lookup.calls
        );
    }

    @Test
    @SuppressWarnings("unchecked")
    void enabledSeamMustExposeSnapshot()
            throws Exception {

        CountingLookup lookup =
                new CountingLookup();

        GnmIncidentDiagnosticResource resource =
                new GnmIncidentDiagnosticResource(
                        lookup,
                        true
                );

        try (Response response =
                     resource.getIncident(
                             ORG,
                             INCIDENT
                     )) {

            assertEquals(
                    200,
                    response.getStatus()
            );

            Map<String, Object> body =
                    (Map<String, Object>)
                            response.getEntity();

            assertEquals(
                    INCIDENT,
                    body.get("incidentId")
            );

            assertEquals(
                    ORG,
                    body.get("organizationId")
            );

            assertEquals(
                    "Open",
                    body.get("incidentStatus")
            );

            assertEquals(
                    "2713046552442022",
                    body.get("notificationId")
            );

            assertEquals(
                    Boolean.TRUE,
                    body.get("openConfirmed")
            );

            assertEquals(
                    Boolean.FALSE,
                    body.get("closedConfirmed")
            );
        }

        assertEquals(
                1,
                lookup.calls
        );
    }

    private static final class CountingLookup
            implements GnmIncidentLookupClient {

        private int calls;

        @Override
        public GnmIncidentSnapshot getIncident(
                String organizationId,
                String incidentId
        ) {

            calls++;

            return new GnmIncidentSnapshot(
                    incidentId,
                    organizationId,
                    "Open",
                    10011,
                    "New",
                    "Begin",
                    "2713046552442022"
            );
        }
    }
}
