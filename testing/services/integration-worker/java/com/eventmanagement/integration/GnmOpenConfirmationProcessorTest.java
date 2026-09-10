package com.eventmanagement.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.camel.Exchange;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.support.DefaultExchange;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class GnmOpenConfirmationProcessorTest {

    private final ObjectMapper mapper =
            new ObjectMapper();

    @Test
    void shouldPromoteLaunchAcceptedToOpenConfirmed()
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

        GnmOpenConfirmationProcessor processor =
                new GnmOpenConfirmationProcessor(
                        lookup,
                        mapper
                );

        Exchange exchange = baseExchange();

        processor.process(exchange);

        assertEquals(
                1,
                lookup.calls
        );

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
                "Open",
                identity.path(
                        "incidentStatus"
                ).asText()
        );

        assertEquals(
                "OPEN_CONFIRMED",
                identity.path(
                        "lifecycleState"
                ).asText()
        );

        /*
         * The Launch transport evidence must survive
         * OPEN confirmation unchanged.
         */
        assertEquals(
                200,
                result.path(
                        "providerTransportResult"
                ).path("httpStatus").asInt()
        );

        assertTrue(
                result.path(
                        "providerTransportResult"
                ).path("accepted").asBoolean()
        );

        assertEquals(
                "OPEN_CONFIRMED",
                exchange.getProperty(
                        "gnmLifecycleState",
                        String.class
                )
        );

        assertEquals(
                "2713046552442022",
                exchange.getProperty(
                        "gnmOpenNotificationId",
                        String.class
                )
        );
    }

    @Test
    void shouldRejectProviderStateThatIsNotOpen()
            throws Exception {

        CountingLookupClient lookup =
                new CountingLookupClient(
                        new GnmIncidentSnapshot(
                                "2713046552385248",
                                "453003085618991",
                                "Closed",
                                10012,
                                "Close",
                                "End",
                                "2713733747119088"
                        )
                );

        GnmOpenConfirmationProcessor processor =
                new GnmOpenConfirmationProcessor(
                        lookup,
                        mapper
                );

        Exchange exchange = baseExchange();

        assertThrows(
                IllegalStateException.class,
                () -> processor.process(exchange)
        );

        assertEquals(
                1,
                lookup.calls
        );

        JsonNode result =
                mapper.readTree(
                        exchange.getMessage()
                                .getBody(String.class)
                );

        assertEquals(
                "LAUNCH_ACCEPTED",
                result.path(
                        "providerNotificationIdentity"
                ).path("lifecycleState").asText()
        );

        assertFalse(
                result.path(
                        "providerNotificationIdentity"
                ).has("openNotificationId")
        );
    }

    @Test
    void shouldRejectIncidentIdentityMismatch()
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

        GnmOpenConfirmationProcessor processor =
                new GnmOpenConfirmationProcessor(
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

        /*
         * Identity mismatch must fail before provider GET.
         */
        assertEquals(
                0,
                lookup.calls
        );
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
                  "eventId":"evt-gnm-open-001",
                  "provider":"EVERBRIDGE",
                  "commandId":"cmd-gnm-open-001",
                  "operation":"SEND_NOTIFICATION",
                  "integrationType":"GNM",
                  "providerTransportResult":{
                    "accepted":true,
                    "httpStatus":200
                  },
                  "providerNotificationIdentity":{
                    "incidentId":"2713046552385248",
                    "lifecycleState":"LAUNCH_ACCEPTED"
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
