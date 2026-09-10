package com.eventmanagement.state;

import com.fasterxml.jackson.databind.node.ObjectNode;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.common.QuarkusTestResource;
import jakarta.inject.Inject;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import java.util.*;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;

@QuarkusTest
@QuarkusTestResource(value = StateTestEnvironment.class, restrictToAnnotatedClass = true)
class EventStateRepositoryIT {
    @Inject EventStateRepository repository;
    @Inject DataSource dataSource;
    private ObjectNode fixture() throws Exception {
        var result = IntegrationResultContractTest.fixture();
        String id = UUID.randomUUID().toString();
        result.put("eventKey", "ess-test:" + id).put("resultId", id).put("eventId", id); return result;
    }
    private long count(String table, String key) throws Exception {
        try (var c = dataSource.getConnection(); var s = c.prepareStatement(
                "SELECT count(*) FROM event_management." + table + " WHERE event_key=?")) {
            s.setString(1, key); try (var r = s.executeQuery()) { r.next(); return r.getLong(1); }
        }
    }
    @Test void lateProviderOpenAndTicketNoteCannotRevertConfirmedTerminal()throws Exception {
        var ticket=fixture().put("ticketLifecycleState","RESOLVED_CONFIRMED");
        assertEquals("RESOLVED",repository.consolidate(ticket).servicenowStatus);
        var note=ticket.deepCopy().put("resultId",UUID.randomUUID().toString()).put("operation","APPLY_AUTOMATION_RESULT");note.remove("ticketLifecycleState");
        assertEquals("RESOLVED",repository.consolidate(note).servicenowStatus);
        var close=fixture().put("integrationType","GNM");close.putObject("providerNotificationIdentity").put("incidentId","incident").put("lifecycleState","CLOSED_CONFIRMED");
        assertEquals("CLOSED",repository.consolidate(close).gnmStatus);
        var open=close.deepCopy().put("resultId",UUID.randomUUID().toString());((ObjectNode)open.path("providerNotificationIdentity")).put("lifecycleState","OPEN_CONFIRMED");
        assertEquals("CLOSED",repository.consolidate(open).gnmStatus);
    }
    @Test void identicalReplayPreservesVersionTimestampAndClaim() throws Exception {
        var result = fixture(); var first = repository.consolidate(result); var second = repository.consolidate(result);
        assertEquals(1, second.version); assertEquals(first.lastUpdatedAt.toInstant().toEpochMilli(), second.lastUpdatedAt.toInstant().toEpochMilli());
        assertEquals("INC-ESS-TEST", second.ticketNumber);
        assertEquals(1, count("processed_integration_result", first.eventKey));
    }
    @Test void changedPayloadCollisionPreservesOriginal() throws Exception {
        var result = fixture(); repository.consolidate(result);
        var bad = result.deepCopy().put("externalId", "different");
        assertThrows(RejectedIntegrationResult.class, () -> repository.consolidate(bad));
        assertEquals("INC-ESS-TEST", repository.consolidate(result).ticketNumber);
        assertEquals(1, count("processed_integration_result", result.path("eventKey").asText()));
    }
    @Test void concurrentFirstResultsMergeWithoutLosingProviderOrVersion() throws Exception {
        var first = fixture(); var second = first.deepCopy().put("resultId", UUID.randomUUID().toString())
                .put("integrationType", "GNM").put("externalId", "GNM-ESS-TEST");
        try (var pool = Executors.newFixedThreadPool(2)) {
            var start = new CountDownLatch(1);
            var a = pool.submit(() -> { start.await(); return repository.consolidate(first); });
            var b = pool.submit(() -> { start.await(); return repository.consolidate(second); }); start.countDown();
            a.get(15, TimeUnit.SECONDS); b.get(15, TimeUnit.SECONDS);
        }
        var state = repository.consolidate(first);
        assertEquals(2, state.version); assertEquals(2, state.integrations.size());
        assertEquals("INC-ESS-TEST", state.ticketNumber); assertEquals("GNM-ESS-TEST", state.notificationId);
    }
    @Test void concurrentIdenticalResultHasOneBusinessEffect() throws Exception {
        var result = fixture();
        try (var pool = Executors.newFixedThreadPool(2)) {
            var a = pool.submit(() -> repository.consolidate(result)); var b = pool.submit(() -> repository.consolidate(result));
            assertEquals(1, a.get(15, TimeUnit.SECONDS).version); assertEquals(1, b.get(15, TimeUnit.SECONDS).version);
        }
        assertEquals(1, count("processed_integration_result", result.path("eventKey").asText()));
    }
    @Test void tenantCollisionRollsBackNewClaim() throws Exception {
        var result = fixture(); repository.consolidate(result);
        var bad = result.deepCopy().put("tenant", "other-tenant").put("resultId", UUID.randomUUID().toString());
        assertThrows(IllegalArgumentException.class, () -> repository.consolidate(bad));
        assertEquals(1, count("processed_integration_result", result.path("eventKey").asText()));
        assertEquals("ess-test", repository.consolidate(result).tenant);
    }
    @Test void sqlFailureRollsBackClaimThroughRealJtaInterceptor() throws Exception {
        var result = fixture();
        // An injected DB constraint fails only after the claim insert, exercising checked SQLException rollback.
        String constraint = "ess_test_" + UUID.randomUUID().toString().replace("-", "");
        try (var c = dataSource.getConnection(); var s = c.createStatement()) {
            s.execute("ALTER TABLE event_management.event_state ADD CONSTRAINT " + constraint
                    + " CHECK (event_key <> '" + result.path("eventKey").asText() + "')");
        }
        try {
            assertThrows(Exception.class, () -> repository.consolidate(result));
            assertEquals(0, count("processed_integration_result", result.path("eventKey").asText()));
            assertEquals(0, count("event_state", result.path("eventKey").asText()));
        } finally {
            try (var c = dataSource.getConnection(); var s = c.createStatement()) {
                s.execute("ALTER TABLE event_management.event_state DROP CONSTRAINT " + constraint);
            }
        }
        assertEquals(1, repository.consolidate(result).version);
    }
    @Test void quarantineReplayHasOneDurableEntryAndNoStateMutation() throws Exception {
        String topic = "ess-test-" + UUID.randomUUID();
        repository.quarantine(topic, 0, 1, "invalid-json", "INVALID_JSON");
        repository.quarantine(topic, 0, 1, "invalid-json", "INVALID_JSON");
        try (var c = dataSource.getConnection(); var statement = c.prepareStatement(
                "SELECT count(*), min(payload), min(payload_hash) FROM event_management.ess_quarantine WHERE topic=?")) {
            statement.setString(1, topic);
            try (var rows = statement.executeQuery()) {
                rows.next(); assertEquals(1, rows.getLong(1)); assertEquals("invalid-json", rows.getString(2));
                assertEquals(64, rows.getString(3).length());
            }
        }
    }
}
