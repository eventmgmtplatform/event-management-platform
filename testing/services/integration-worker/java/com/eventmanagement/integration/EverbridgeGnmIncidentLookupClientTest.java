package com.eventmanagement.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class EverbridgeGnmIncidentLookupClientTest {

    private static final String ORG =
            "453003085618991";

    private static final String INCIDENT =
            "2713046552385248";

    @Test
    void shouldParseOpenIncident() throws Exception {

        GnmHttpInvoker invoker =
                new StubInvoker(
                        200,
                        """
                        {
                          "status": 200,
                          "message": "OK",
                          "result": {
                            "id": "2713046552385248",
                            "organizationId": "453003085618991",
                            "incidentStatus": "Open",
                            "incidentAction": "Launch",
                            "phaseStatus": {
                              "id": 1001,
                              "incidentStatus": "Open",
                              "name": "New",
                              "phaseNodeType": "Begin",
                              "seq": 100
                            },
                            "incidentPhases": [
                              {
                                "id": 10011,
                                "name": "New",
                                "notificationId": "2713046552442022"
                              }
                            ]
                          }
                        }
                        """
                );

        GnmIncidentSnapshot snapshot =
                new EverbridgeGnmIncidentLookupClient(
                        invoker,
                        new ObjectMapper()
                ).getIncident(ORG, INCIDENT);

        assertEquals(INCIDENT, snapshot.incidentId());
        assertEquals(ORG, snapshot.organizationId());
        assertEquals("Open", snapshot.incidentStatus());

        assertEquals(
                10011,
                snapshot.phaseId()
        );

        assertEquals(
                "New",
                snapshot.phaseName()
        );

        assertEquals(
                "Begin",
                snapshot.phaseNodeType()
        );

        assertEquals(
                "2713046552442022",
                snapshot.notificationId()
        );

        assertTrue(snapshot.openConfirmed());
        assertFalse(snapshot.closedConfirmed());
    }

    @Test
    void shouldRejectUnexpectedIncidentId() {

        GnmHttpInvoker invoker =
                new StubInvoker(
                        200,
                        """
                        {
                          "result": {
                            "id": "WRONG",
                            "organizationId": "453003085618991",
                            "incidentStatus": "Open"
                          }
                        }
                        """
                );

        assertThrows(
                IllegalStateException.class,
                () -> new EverbridgeGnmIncidentLookupClient(
                        invoker,
                        new ObjectMapper()
                ).getIncident(ORG, INCIDENT)
        );
    }

    @Test
    void shouldRejectNonSuccessfulHttpStatus() {

        GnmHttpInvoker invoker =
                new StubInvoker(
                        500,
                        "{\"status\":500}"
                );

        assertThrows(
                IllegalStateException.class,
                () -> new EverbridgeGnmIncidentLookupClient(
                        invoker,
                        new ObjectMapper()
                ).getIncident(ORG, INCIDENT)
        );
    }

    private static final class StubInvoker
            implements GnmHttpInvoker {

        private final int status;
        private final String body;

        private StubInvoker(
                int status,
                String body
        ) {
            this.status = status;
            this.body = body;
        }

        @Override
        public HttpResult launch(
                String organizationId,
                String requestBody
        ) {
            throw new AssertionError(
                    "Launch must not be called by GET lookup"
            );
        }

        @Override
        public HttpResult getIncident(
                String organizationId,
                String incidentId
        ) {
            return new HttpResult(
                    status,
                    body
            );
        }
    }
}
