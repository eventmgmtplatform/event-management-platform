package com.eventmanagement.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.camel.Exchange;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.support.DefaultExchange;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class GnmReconciliationProcessorTest {

    private final ObjectMapper objectMapper =
            new ObjectMapper();

    @Test
    void durableCheckpointConfirmsOpen()
            throws Exception {

        RecordingLookup lookup =
                new RecordingLookup(
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

        GnmReconciliationProcessor processor =
                new GnmReconciliationProcessor(
                        lookup,
                        objectMapper
                );

        Exchange exchange =
                exchange(
                        """
                        {
                          "provider":"EVERBRIDGE",
                          "organizationId":"453003085618991",
                          "incidentId":"2713046552385248",
                          "lifecycleState":"LAUNCH_ACCEPTED"
                        }
                        """
                );

        processor.process(exchange);

        assertEquals(1, lookup.calls);

        assertEquals(
                "453003085618991",
                lookup.organizationId
        );

        assertEquals(
                "2713046552385248",
                lookup.incidentId
        );

        assertEquals(
                "OPEN_CONFIRMED",
                exchange.getProperty(
                        GnmReconciliationProcessor.OUTCOME_PROPERTY
                )
        );

        var result =
                objectMapper.readTree(
                        exchange.getMessage()
                                .getBody(String.class)
                );

        assertEquals(
                "SUCCESS",
                result.path("status").asText()
        );

        assertEquals(
                "OPEN_CONFIRMED",
                result
                        .path("providerNotificationIdentity")
                        .path("lifecycleState")
                        .asText()
        );

        assertEquals(
                "Open",
                result
                        .path("providerNotificationIdentity")
                        .path("incidentStatus")
                        .asText()
        );

        assertEquals(
                "2713046552442022",
                result
                        .path("providerNotificationIdentity")
                        .path("openNotificationId")
                        .asText()
        );

        /*
         * Reconciliation must not invent POST transport evidence.
         */
        assertTrue(
                result.path("providerTransportResult")
                        .isMissingNode()
        );
    }

    @Test
    void missingCheckpointFailsBeforeLookup() {

        RecordingLookup lookup =
                new RecordingLookup((GnmIncidentSnapshot) null);

        GnmReconciliationProcessor processor =
                new GnmReconciliationProcessor(
                        lookup,
                        objectMapper
                );

        Exchange exchange =
                new DefaultExchange(
                        new DefaultCamelContext()
                );

        exchange.setProperty(
                "commandId",
                "cmd-reconcile-missing"
        );

        exchange.setProperty(
                "eventId",
                "evt-reconcile-missing"
        );

        assertThrows(
                IllegalStateException.class,
                () -> processor.process(exchange)
        );

        assertEquals(0, lookup.calls);
    }

    @Test
    void wrongLifecycleFailsBeforeLookup() {

        RecordingLookup lookup =
                new RecordingLookup((GnmIncidentSnapshot) null);

        GnmReconciliationProcessor processor =
                new GnmReconciliationProcessor(
                        lookup,
                        objectMapper
                );

        Exchange exchange =
                exchange(
                        """
                        {
                          "provider":"EVERBRIDGE",
                          "organizationId":"453003085618991",
                          "incidentId":"2713046552385248",
                          "lifecycleState":"OPEN_CONFIRMED"
                        }
                        """
                );

        assertThrows(
                IllegalStateException.class,
                () -> processor.process(exchange)
        );

        assertEquals(0, lookup.calls);
    }

    @Test
    void getFailureDefersWithoutSuccess()
            throws Exception {

        RecordingLookup lookup =
                new RecordingLookup(
                        new RuntimeException(
                                "temporary"
                        )
                );

        GnmReconciliationProcessor processor =
                new GnmReconciliationProcessor(
                        lookup,
                        objectMapper
                );

        Exchange exchange =
                exchange(
                        """
                        {
                          "provider":"EVERBRIDGE",
                          "organizationId":"453003085618991",
                          "incidentId":"2713046552385248",
                          "lifecycleState":"LAUNCH_ACCEPTED"
                        }
                        """
                );

        processor.process(exchange);

        assertEquals(1, lookup.calls);

        assertEquals(
                "RETRY_LATER",
                exchange.getProperty(
                        GnmReconciliationProcessor.OUTCOME_PROPERTY
                )
        );
    }

    private Exchange exchange(
            String checkpoint
    ) {
        Exchange exchange =
                new DefaultExchange(
                        new DefaultCamelContext()
                );

        exchange.setProperty(
                "commandId",
                "cmd-reconcile-001"
        );

        exchange.setProperty(
                "eventId",
                "evt-reconcile-001"
        );

        exchange.setProperty(
                "integrationProviderCheckpoint",
                checkpoint
        );

        return exchange;
    }

    private static final class RecordingLookup
            implements GnmIncidentLookupClient {

        private final GnmIncidentSnapshot snapshot;
        private final RuntimeException failure;

        int calls;
        String organizationId;
        String incidentId;

        RecordingLookup(
                GnmIncidentSnapshot snapshot
        ) {
            this.snapshot = snapshot;
            this.failure = null;
        }

        RecordingLookup(
                RuntimeException failure
        ) {
            this.snapshot = null;
            this.failure = failure;
        }

        @Override
        public GnmIncidentSnapshot getIncident(
                String organizationId,
                String incidentId
        ) {
            calls++;

            this.organizationId =
                    organizationId;

            this.incidentId =
                    incidentId;

            if (failure != null) {
                throw failure;
            }

            return snapshot;
        }
    }

    @org.junit.jupiter.api.Test
    void reconcilesCloseAcceptedCheckpointUsingGetOnly() throws Exception {

        java.util.concurrent.atomic.AtomicInteger lookups =
                new java.util.concurrent.atomic.AtomicInteger();

        GnmIncidentLookupClient lookup =
                (organizationId, incidentId) -> {
                    lookups.incrementAndGet();

                    org.junit.jupiter.api.Assertions.assertEquals(
                            "453003085618991",
                            organizationId
                    );

                    org.junit.jupiter.api.Assertions.assertEquals(
                            "2713046552385248",
                            incidentId
                    );

                    return new GnmIncidentSnapshot(
                            "2713046552385248",
                            "453003085618991",
                            "Closed",
                            1003,
                            "Close",
                            "End",
                            "2713733747119088"
                    );
                };

        GnmReconciliationProcessor processor =
                new GnmReconciliationProcessor(
                        lookup,
                        new com.fasterxml.jackson.databind.ObjectMapper()
                );

        org.apache.camel.impl.DefaultCamelContext context =
                new org.apache.camel.impl.DefaultCamelContext();

        org.apache.camel.Exchange exchange =
                new org.apache.camel.support.DefaultExchange(context);

        exchange.setProperty(
                "commandId",
                "cmd-close-reconcile-001"
        );

        exchange.setProperty(
                "eventId",
                "evt-close-reconcile-001"
        );

        exchange.setProperty(
                "integrationProviderCheckpoint",
                """
                {
                  "provider":"EVERBRIDGE",
                  "organizationId":"453003085618991",
                  "incidentId":"2713046552385248",
                  "lifecycleState":"CLOSE_ACCEPTED"
                }
                """
        );

        processor.process(exchange);

        com.fasterxml.jackson.databind.JsonNode result =
                exchange.getMessage().getBody(
                        com.fasterxml.jackson.databind.JsonNode.class
                );

        org.junit.jupiter.api.Assertions.assertEquals(
                1,
                lookups.get()
        );

        org.junit.jupiter.api.Assertions.assertEquals(
                "SUCCESS",
                result.path("status").asText()
        );

        org.junit.jupiter.api.Assertions.assertEquals(
                "GNM",
                result.path("integrationType").asText()
        );

        org.junit.jupiter.api.Assertions.assertEquals(
                "CLOSE_NOTIFICATION",
                result.path("operation").asText()
        );

        org.junit.jupiter.api.Assertions.assertEquals(
                "EVERBRIDGE",
                result.path("provider").asText()
        );

        org.junit.jupiter.api.Assertions.assertEquals(
                "2713046552385248",
                result.path("providerNotificationIdentity")
                        .path("incidentId")
                        .asText()
        );

        org.junit.jupiter.api.Assertions.assertEquals(
                "Closed",
                result.path("providerNotificationIdentity")
                        .path("incidentStatus")
                        .asText()
        );

        org.junit.jupiter.api.Assertions.assertEquals(
                "CLOSED_CONFIRMED",
                result.path("providerNotificationIdentity")
                        .path("lifecycleState")
                        .asText()
        );

        org.junit.jupiter.api.Assertions.assertEquals(
                "2713733747119088",
                result.path("providerNotificationIdentity")
                        .path("closeNotificationId")
                        .asText()
        );

        org.junit.jupiter.api.Assertions.assertEquals(
                "CLOSED_CONFIRMED",
                exchange.getProperty(
                        GnmReconciliationProcessor.OUTCOME_PROPERTY,
                        String.class
                )
        );
    }

    @org.junit.jupiter.api.Test
    void closeAcceptedRequiresAuthoritativeClosedState() {

        GnmIncidentLookupClient lookup =
                (organizationId, incidentId) ->
                        new GnmIncidentSnapshot(
                                incidentId,
                                organizationId,
                                "Open",
                                10011,
                                "New",
                                "Begin",
                                "2713046552442022"
                        );

        GnmReconciliationProcessor processor =
                new GnmReconciliationProcessor(
                        lookup,
                        new com.fasterxml.jackson.databind.ObjectMapper()
                );

        org.apache.camel.impl.DefaultCamelContext context =
                new org.apache.camel.impl.DefaultCamelContext();

        org.apache.camel.Exchange exchange =
                new org.apache.camel.support.DefaultExchange(context);

        exchange.setProperty(
                "commandId",
                "cmd-close-reconcile-open-state"
        );

        exchange.setProperty(
                "eventId",
                "evt-close-reconcile-open-state"
        );

        exchange.setProperty(
                "integrationProviderCheckpoint",
                """
                {
                  "provider":"EVERBRIDGE",
                  "organizationId":"453003085618991",
                  "incidentId":"2713046552385248",
                  "lifecycleState":"CLOSE_ACCEPTED"
                }
                """
        );

        org.junit.jupiter.api.Assertions.assertDoesNotThrow(
                () -> processor.process(exchange)
        );

        org.junit.jupiter.api.Assertions.assertEquals(
                "NOT_CLOSED",
                exchange.getProperty(
                        GnmReconciliationProcessor.OUTCOME_PROPERTY,
                        String.class
                )
        );
    }

    @org.junit.jupiter.api.Test
    void rejectsUnsupportedCheckpointLifecycleBeforeLookup() {

        java.util.concurrent.atomic.AtomicInteger lookups =
                new java.util.concurrent.atomic.AtomicInteger();

        GnmIncidentLookupClient lookup =
                (organizationId, incidentId) -> {
                    lookups.incrementAndGet();
                    throw new AssertionError(
                            "Lookup must not execute"
                    );
                };

        GnmReconciliationProcessor processor =
                new GnmReconciliationProcessor(
                        lookup,
                        new com.fasterxml.jackson.databind.ObjectMapper()
                );

        org.apache.camel.impl.DefaultCamelContext context =
                new org.apache.camel.impl.DefaultCamelContext();

        org.apache.camel.Exchange exchange =
                new org.apache.camel.support.DefaultExchange(context);

        exchange.setProperty(
                "commandId",
                "cmd-invalid-lifecycle"
        );

        exchange.setProperty(
                "eventId",
                "evt-invalid-lifecycle"
        );

        exchange.setProperty(
                "integrationProviderCheckpoint",
                """
                {
                  "provider":"EVERBRIDGE",
                  "organizationId":"453003085618991",
                  "incidentId":"2713046552385248",
                  "lifecycleState":"UNKNOWN"
                }
                """
        );

        org.junit.jupiter.api.Assertions.assertThrows(
                IllegalStateException.class,
                () -> processor.process(exchange)
        );

        org.junit.jupiter.api.Assertions.assertEquals(
                0,
                lookups.get()
        );
    }


    @org.junit.jupiter.api.Test
    void closeGetFailureDefersWithoutProviderMutation()
            throws Exception {

        java.util.concurrent.atomic.AtomicInteger lookups =
                new java.util.concurrent.atomic.AtomicInteger();

        GnmIncidentLookupClient lookup =
                (organizationId, incidentId) -> {
                    lookups.incrementAndGet();
                    throw new RuntimeException("temporary-close");
                };

        GnmReconciliationProcessor processor =
                new GnmReconciliationProcessor(
                        lookup,
                        new com.fasterxml.jackson.databind.ObjectMapper()
                );

        org.apache.camel.Exchange exchange =
                new org.apache.camel.support.DefaultExchange(
                        new org.apache.camel.impl.DefaultCamelContext()
                );

        exchange.setProperty(
                "commandId",
                "cmd-close-get-failure"
        );

        exchange.setProperty(
                "eventId",
                "evt-close-get-failure"
        );

        exchange.setProperty(
                "integrationProviderCheckpoint",
                """
                {
                  "provider":"EVERBRIDGE",
                  "organizationId":"453003085618991",
                  "incidentId":"2713046552385248",
                  "lifecycleState":"CLOSE_ACCEPTED"
                }
                """
        );

        processor.process(exchange);

        org.junit.jupiter.api.Assertions.assertEquals(
                1,
                lookups.get()
        );

        org.junit.jupiter.api.Assertions.assertEquals(
                "RETRY_LATER",
                exchange.getProperty(
                        GnmReconciliationProcessor.OUTCOME_PROPERTY
                )
        );
    }

}
