package com.eventmanagement.processor.adapters.postgres;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import javax.sql.DataSource;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.component.kafka.KafkaConstants;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

@ApplicationScoped
public class OutboxDispatcher {
    private static final Logger LOG=Logger.getLogger(OutboxDispatcher.class);
    @Inject DataSource dataSource;
    @Inject ProducerTemplate producer;
    @ConfigProperty(name="processor.kafka.brokers") String brokers;
    @ConfigProperty(name="processor.outbox.batch-size") int batchSize;
    private java.util.concurrent.ScheduledExecutorService executor;
    @ConfigProperty(name="processor.outbox.poll-ms") long pollMs;
    void start(@jakarta.enterprise.event.Observes io.quarkus.runtime.StartupEvent event) {
        if (pollMs < 1 || batchSize < 1) throw new IllegalArgumentException("INVALID_OUTBOX_BOUNDS");
        executor=java.util.concurrent.Executors.newSingleThreadScheduledExecutor(r -> {
            Thread thread=new Thread(r,"processor-outbox"); thread.setDaemon(true); return thread;
        });
        executor.scheduleWithFixedDelay(() -> {
            try { dispatch(); } catch(Exception failure) { LOG.warn("OUTBOX_DEPENDENCY_UNAVAILABLE"); }
        },pollMs,pollMs,java.util.concurrent.TimeUnit.MILLISECONDS);
    }
    void stop(@jakarta.enterprise.event.Observes io.quarkus.runtime.ShutdownEvent event) {
        if(executor!=null) {
            executor.shutdown();
            try { if(!executor.awaitTermination(15,java.util.concurrent.TimeUnit.SECONDS)) executor.shutdownNow(); }
            catch(InterruptedException interrupted) { executor.shutdownNow(); Thread.currentThread().interrupt(); }
        }
    }
    /** Row locks span the bounded publish; crash releases locks and preserves pending intent. */
    public void dispatch() throws Exception {
        for(int i=0;i<batchSize;i++) {
            try(var c=dataSource.getConnection()) {
                c.setAutoCommit(false);
                try(var s=c.prepareStatement("""
                        SELECT message_id,topic,message_key,payload::text
                        FROM event_processor.output_outbox WHERE published_at IS NULL
                        ORDER BY created_at,message_id LIMIT 1 FOR UPDATE SKIP LOCKED
                        """)) {
                    String id,topic,key,payload;
                    try(var r=s.executeQuery()) {
                        if(!r.next()) { c.commit(); return; }
                        id=r.getString(1);topic=r.getString(2);key=r.getString(3);payload=r.getString(4);
                    }
                    producer.sendBodyAndHeader("kafka:"+topic+"?brokers="+brokers
                            +"&requestRequiredAcks=all&maxBlockMs=10000&deliveryTimeoutMs=10000&requestTimeoutMs=5000",
                            payload,KafkaConstants.KEY,key);
                    try(var update=c.prepareStatement("UPDATE event_processor.output_outbox SET published_at=now() WHERE message_id=?")) {
                        update.setString(1,id);update.executeUpdate();
                    }
                    c.commit();
                } catch(Exception failure) {
                    c.rollback(); LOG.warn("OUTBOX_PUBLICATION_DEFERRED");
                    // No payload, broker exception detail or SQL/credential data in logs.
                    return;
                }
            }
        }
    }
}
