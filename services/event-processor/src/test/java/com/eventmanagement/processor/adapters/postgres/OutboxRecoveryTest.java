package com.eventmanagement.processor.adapters.postgres;

import org.apache.camel.ProducerTemplate;
import org.junit.jupiter.api.*;
import org.postgresql.ds.PGSimpleDataSource;
import java.lang.reflect.Proxy;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Consumer;
import static org.junit.jupiter.api.Assertions.*;

class OutboxRecoveryTest {
    private PGSimpleDataSource ds;
    private final List<String> ids = new ArrayList<>();
    private String scope;

    @BeforeEach void setup() throws Exception {
        String url = System.getProperty("processor.test.jdbc.url");
        Assumptions.assumeTrue(url != null, "Isolated PostgreSQL URL required");
        ds = new PGSimpleDataSource();
        ds.setURL(url); ds.setUser("cacf_test"); ds.setPassword("cacf-test-only");
        try (var c = ds.getConnection(); var s = c.createStatement(); var r = s.executeQuery("SELECT current_database()")) {
            r.next(); assertEquals("cacf_test", r.getString(1));
        }
        scope = UUID.randomUUID().toString();
    }

    @AfterEach void cleanup() throws Exception {
        if (ds == null) return;
        try (var c = ds.getConnection()) {
            for (String id : ids) {
                for (String table : List.of("output_outbox", "processing_record")) {
                    try (var s = c.prepareStatement("DELETE FROM event_processor." + table + " WHERE processing_id=?")) {
                        s.setString(1, id); s.executeUpdate();
                    }
                }
            }
        }
    }

    private String accept(String key, String label) throws Exception {
        String id = UUID.randomUUID().toString(); ids.add(id);
        new PostgresProcessingStore(ds).accept(id, id, id, scope, "{}", "events.normalized",
                scope + key, "{\"label\":\"" + label + "\"}");
        return id;
    }

    private OutboxDispatcher dispatcher(Consumer<String> action) {
        var dispatcher = new OutboxDispatcher();
        dispatcher.dataSource = ds; dispatcher.brokers = "unused:9092";
        dispatcher.batchSize = 10; dispatcher.initialRetryMs = 60000; dispatcher.maxRetryMs = 60000;
        dispatcher.producer = (ProducerTemplate) Proxy.newProxyInstance(getClass().getClassLoader(),
                new Class<?>[]{ProducerTemplate.class}, (proxy, method, args) -> {
                    if (method.getName().equals("sendBodyAndHeader")) {
                        action.accept((String) args[1]); return null;
                    }
                    throw new UnsupportedOperationException(method.getName());
                });
        return dispatcher;
    }

    @Test void persistedRetryBlocksLaterSameKeyButNotOtherKeys() throws Exception {
        String first = accept("a", "first");
        accept("a", "second"); accept("b", "independent");
        var delivered = new ArrayList<String>();
        dispatcher(payload -> {
            delivered.add(payload);
            if (payload.contains("first")) throw new RuntimeException("do-not-persist-provider-detail");
        }).dispatch();
        assertEquals(2, delivered.size());
        assertTrue(delivered.get(0).contains("first"));
        assertTrue(delivered.get(1).contains("independent"));
        try (var c = ds.getConnection(); var s = c.prepareStatement("""
                SELECT attempts, last_error_code, next_attempt_at > last_attempt_at, published_at
                FROM event_processor.output_outbox WHERE message_id=?
                """)) {
            s.setString(1, first);
            try (var r = s.executeQuery()) {
                assertTrue(r.next()); assertEquals(1, r.getLong(1));
                assertEquals("PUBLICATION_UNCONFIRMED", r.getString(2));
                assertTrue(r.getBoolean(3)); assertNull(r.getObject(4));
            }
        }
        // New dispatcher, same database: restart cannot erase the retry deadline.
        dispatcher(delivered::add).dispatch(); assertEquals(2, delivered.size());
        try (var c = ds.getConnection(); var s = c.prepareStatement(
                "UPDATE event_processor.output_outbox SET next_attempt_at=now() WHERE message_id=?")) {
            s.setString(1, first); s.executeUpdate();
        }
        dispatcher(delivered::add).dispatch();
        assertEquals(4, delivered.size());
        assertTrue(delivered.get(2).contains("first")); assertTrue(delivered.get(3).contains("second"));
        try (var c = ds.getConnection(); var s = c.prepareStatement(
                "SELECT attempts,last_error_code FROM event_processor.output_outbox WHERE message_id=?")) {
            s.setString(1, first);
            try (var r = s.executeQuery()) {
                r.next(); assertEquals(2, r.getLong(1)); assertNull(r.getString(2));
            }
        }
    }

    @Test void concurrentDispatchersCannotOvertakeLockedEarlierMessage() throws Exception {
        accept("a", "first"); accept("a", "second"); accept("b", "independent");
        var entered = new CountDownLatch(1); var release = new CountDownLatch(1);
        var delivered = new ConcurrentLinkedQueue<String>();
        var first = dispatcher(payload -> {
            entered.countDown();
            try { assertTrue(release.await(10, TimeUnit.SECONDS)); }
            catch (InterruptedException e) { Thread.currentThread().interrupt(); throw new RuntimeException(e); }
            delivered.add(payload);
        });
        first.batchSize = 1;
        try (var pool = Executors.newFixedThreadPool(2)) {
            var claim = pool.submit(() -> { first.dispatch(); return true; });
            try {
                assertTrue(entered.await(5, TimeUnit.SECONDS));
                var competitor = pool.submit(() -> { dispatcher(delivered::add).dispatch(); return true; });
                assertTrue(competitor.get(5, TimeUnit.SECONDS));
                assertEquals(1, delivered.size()); assertTrue(delivered.peek().contains("independent"));
            } finally { release.countDown(); }
            assertTrue(claim.get(5, TimeUnit.SECONDS));
        }
        dispatcher(delivered::add).dispatch();
        assertEquals(3, delivered.size());
        assertTrue(new ArrayList<>(delivered).get(2).contains("second"));
    }

    @Test void shutdownDoesNotClaimNewOutput() throws Exception {
        accept("a", "first");
        var delivered = new ArrayList<String>(); var dispatcher = dispatcher(delivered::add);
        dispatcher.stop(null); dispatcher.dispatch();
        assertTrue(delivered.isEmpty());
    }
}
