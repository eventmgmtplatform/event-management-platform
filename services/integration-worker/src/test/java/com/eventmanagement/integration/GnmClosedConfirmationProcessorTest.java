package com.eventmanagement.integration;

import static org.junit.jupiter.api.Assertions.*;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.camel.Exchange;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.support.DefaultExchange;
import org.junit.jupiter.api.Test;

class GnmClosedConfirmationProcessorTest {

    private final ObjectMapper mapper =
            new ObjectMapper();

    @Test
    void shouldPromoteCloseAcceptedToClosedConfirmed()
            throws Exception {

        CountingLookupClient lookup =
                new CountingLookupClient(
                        new GnmIncidentSnapshot(
                                "2713046552385248",
                                "453003085618991",
                                "Closed",
                                1003,
                                "Close",
                                "End",
                                "2713733747119088"
                        )
                );

        GnmClosedConfirmationProcessor processor =
                new GnmClosedConfirmationProcessor(
                        lookup,
                        mapper
                );

        Exchange exchange = baseExchange();

        processor.process(exchange);

        assertEquals(1, lookup.calls);

        JsonNode result =
                mapper.readTree(
                        exchange.getMessage()
                                .getBody(String.class)
                );

        JsonNode identity =
                result.path(
                        "providerNotificationIdentity"
                );

        assertEquals(
                "2713046552385248",
                identity.path("incidentId").asText()
        );

        assertEquals(
                "2713046552442022",
                identity.path(
                        "openNotificationId"
                ).asText()
        );

        assertEquals(
                "2713733747119088",
                identity.path(
                        "closeNotificationId"
                ).asText()
        );

        assertEquals(
                "Closed",
                identity.path(
                        "incidentStatus"
                ).asText()
        );

        assertEquals(
                "CLOSED_CONFIRMED",
                identity.path(
                        "lifecycleState"
                ).asText()
        );

        assertEquals(
                "2713733747119088",
                exchange.getProperty(
                        "gnmCloseNotificationId",
                        String.class
                )
        );

        assertEquals(
                "CLOSED_CONFIRMED",
                exchange.getProperty(
                        "gnmLifecycleState",
                        String.class
                )
        );

        assertFalse(
                result.has("organizationId")
        );
    }

    @Test
    void shouldRejectProviderStateThatIsNotClosed()
            throws Exception {

        CountingLookupClient lookup =
                new CountingLookupClient(
                        new GnmIncidentSnapshot(
                                "2713046552385248",
                                "453003085618991",
                                "Open",
                                10011,
                                "New",
                                "Begin",
                                "2713046552442022"
                        )
                );

        GnmClosedConfirmationProcessor processor =
                new GnmClosedConfirmationProcessor(
                        lookup,
                        mapper
                );

        Exchange exchange = baseExchange();

        assertThrows(
                IllegalStateException.class,
                () -> processor.process(exchange)
        );

        assertEquals(1, lookup.calls);

        JsonNode result =
                mapper.readTree(
                        exchange.getMessage()
                                .getBody(String.class)
                );

        assertEquals(
                "CLOSE_ACCEPTED",
                result.path(
                        "providerNotificationIdentity"
                ).path("lifecycleState").asText()
        );

        assertFalse(
                result.path(
                        "providerNotificationIdentity"
                ).has("closeNotificationId")
        );
    }

    @Test
    void shouldRejectIdentityMismatchBeforeGet()
            throws Exception {

        CountingLookupClient lookup =
                new CountingLookupClient(
                        new GnmIncidentSnapshot(
                                "2713046552385248",
                                "453003085618991",
                                "Closed",
                                1003,
                                "Close",
                                "End",
                                "2713733747119088"
                        )
                );

        GnmClosedConfirmationProcessor processor =
                new GnmClosedConfirmationProcessor(
                        lookup,
                        mapper
                );

        Exchange exchange = baseExchange();

        exchange.setProperty(
                "gnmIncidentId",
                "different-incident"
        );

        assertThrows(
                IllegalStateException.class,
                () -> processor.process(exchange)
        );

        assertEquals(0, lookup.calls);
    }

    private Exchange baseExchange() {

        Exchange exchange =
                new DefaultExchange(
                        new DefaultCamelContext()
                );

        exchange.setProperty(
                "gnmOrganizationId",
                "453003085618991"
        );

        exchange.setProperty(
                "gnmIncidentId",
                "2713046552385248"
        );

        exchange.getMessage().setBody(
                """
                {
                  "status":"SUCCESS",
                  "eventId":"evt-gnm-close-001",
                  "provider":"EVERBRIDGE",
                  "commandId":"cmd-gnm-close-001",
                  "operation":"CLOSE_NOTIFICATION",
                  "integrationType":"GNM",
                  "providerTransportResult":{
                    "accepted":true,
                    "httpStatus":200
                  },
                  "providerNotificationIdentity":{
                    "incidentId":"2713046552385248",
                    "openNotificationId":"2713046552442022",
                    "lifecycleState":"CLOSE_ACCEPTED"
                  }
                }
                """
        );

        return exchange;
    }

    private static final class CountingLookupClient
            implements GnmIncidentLookupClient {

        private final GnmIncidentSnapshot snapshot;
        private int calls;

        private CountingLookupClient(
                GnmIncidentSnapshot snapshot
        ) {
            this.snapshot = snapshot;
        }

        @Override
        public GnmIncidentSnapshot getIncident(
                String organizationId,
                String incidentId
        ) {
            calls++;

            assertEquals(
                    "453003085618991",
                    organizationId
            );

            assertEquals(
                    "2713046552385248",
                    incidentId
            );

            return snapshot;
        }
    }
}
