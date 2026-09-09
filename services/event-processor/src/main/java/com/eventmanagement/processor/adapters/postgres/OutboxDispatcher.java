package com.eventmanagement.processor.adapters.postgres;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import javax.sql.DataSource;
import java.sql.Connection;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.component.kafka.KafkaConstants;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

@ApplicationScoped
public class OutboxDispatcher {
    private static final Logger LOG = Logger.getLogger(OutboxDispatcher.class);
    @Inject DataSource dataSource;
    @Inject ProducerTemplate producer;
    @ConfigProperty(name = "processor.kafka.brokers") String brokers;
    @ConfigProperty(name = "processor.outbox.batch-size") int batchSize;
    @ConfigProperty(name = "processor.outbox.poll-ms") long pollMs;
    @ConfigProperty(name = "processor.outbox.retry-initial-ms") long initialRetryMs;
    @ConfigProperty(name = "processor.outbox.retry-max-ms") long maxRetryMs;
    private java.util.concurrent.ScheduledExecutorService executor;
    private volatile boolean stopping;

    void start(@jakarta.enterprise.event.Observes io.quarkus.runtime.StartupEvent event) {
        if (pollMs < 1 || batchSize < 1 || initialRetryMs < 1 || maxRetryMs < initialRetryMs)
            throw new IllegalArgumentException("INVALID_OUTBOX_BOUNDS");
        executor = java.util.concurrent.Executors.newSingleThreadScheduledExecutor(r -> {
            Thread thread = new Thread(r, "processor-outbox");
            thread.setDaemon(true);
            return thread;
        });
        executor.scheduleWithFixedDelay(() -> {
            try { dispatch(); }
            catch (Exception failure) { LOG.warn("OUTBOX_DEPENDENCY_UNAVAILABLE"); }
        }, pollMs, pollMs, java.util.concurrent.TimeUnit.MILLISECONDS);
    }

    void stop(@jakarta.enterprise.event.Observes io.quarkus.runtime.ShutdownEvent event) {
        stopping = true;
        if (executor != null) {
            executor.shutdown();
            try {
                if (!executor.awaitTermination(30, java.util.concurrent.TimeUnit.SECONDS))
                    executor.shutdownNow();
            } catch (InterruptedException interrupted) {
                executor.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }
    }

    /** Ordering covers committed pending rows, not source-event timestamp ordering. */
    public void dispatch() throws Exception {
        for (int i = 0; i < batchSize && !stopping && !Thread.currentThread().isInterrupted(); i++) {
            try (var c = dataSource.getConnection()) {
                c.setAutoCommit(false);
                try {
                    String id, topic, key, payload;
                    long attempts;
                    try (var select = c.prepareStatement("""
                            SELECT o.message_id, o.topic, o.message_key, o.payload::text, o.attempts
                            FROM event_processor.output_outbox o
                            WHERE o.published_at IS NULL AND o.next_attempt_at <= now()
                              AND NOT EXISTS (
                                SELECT 1 FROM event_processor.output_outbox earlier
                                WHERE earlier.topic = o.topic AND earlier.message_key = o.message_key
                                  AND earlier.published_at IS NULL
                                  AND earlier.dispatch_sequence < o.dispatch_sequence
                              )
                            ORDER BY o.dispatch_sequence LIMIT 1 FOR UPDATE OF o SKIP LOCKED
                            """)) {
                        select.setQueryTimeout(5);
                        try (var r = select.executeQuery()) {
                            if (!r.next()) { c.commit(); return; }
                            id = r.getString(1);
                            topic = r.getString(2);
                            key = r.getString(3);
                            payload = r.getString(4);
                            attempts = r.getLong(5);
                        }
                    }
                    // Keep the claim lock while rolling back a failed post-publish SQL operation.
                    var beforePublish = c.setSavepoint();
                    try {
                        producer.sendBodyAndHeader("kafka:" + topic + "?brokers=" + brokers
                                + "&requestRequiredAcks=all&maxBlockMs=10000"
                                + "&deliveryTimeoutMs=10000&requestTimeoutMs=5000",
                                payload, KafkaConstants.KEY, key);
                        try (var update = c.prepareStatement("""
                                UPDATE event_processor.output_outbox
                                SET published_at=now(), attempts=attempts+1,
                                    last_attempt_at=now(), last_error_code=NULL
                                WHERE message_id=?
                                """)) {
                            update.setQueryTimeout(5);
                            update.setString(1, id);
                            update.executeUpdate();
                        }
                    } catch (Exception failure) {
                        c.rollback(beforePublish);
                        defer(c, id, retryDelayMs(attempts, initialRetryMs, maxRetryMs));
                        LOG.warn("OUTBOX_PUBLICATION_DEFERRED");
                    }
                    c.commit();
                } catch (Exception failure) {
                    c.rollback();
                    throw failure;
                }
            }
        }
    }

    private static void defer(Connection c, String id, long delayMs) throws Exception {
        try (var update = c.prepareStatement("""
                UPDATE event_processor.output_outbox
                SET attempts=attempts+1, last_attempt_at=now(),
                    next_attempt_at=now() + (? * interval '1 millisecond'),
                    last_error_code='PUBLICATION_UNCONFIRMED'
                WHERE message_id=?
                """)) {
            update.setQueryTimeout(5);
            update.setLong(1, delayMs);
            update.setString(2, id);
            update.executeUpdate();
        }
    }

    static long retryDelayMs(long previousAttempts, long initial, long maximum) {
        if (previousAttempts < 0 || initial < 1 || maximum < initial)
            throw new IllegalArgumentException("INVALID_RETRY_BOUNDS");
        long delay = initial;
        for (long i = 0; i < previousAttempts && delay < maximum; i++) {
            if (delay > maximum / 2) return maximum;
            delay *= 2;
        }
        return Math.min(delay, maximum);
    }
}
