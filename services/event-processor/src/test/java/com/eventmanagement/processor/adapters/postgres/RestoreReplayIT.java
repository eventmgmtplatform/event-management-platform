package com.eventmanagement.processor.adapters.postgres;

import org.apache.camel.ProducerTemplate;
import org.junit.jupiter.api.Test;
import org.postgresql.ds.PGSimpleDataSource;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import static org.junit.jupiter.api.Assertions.*;

/** Invoked explicitly by event-processor-recovery.py against its fresh restored database. */
class RestoreReplayIT {
    @Test void prepareVersionsForBackup() throws Exception {
        String url=System.getProperty("processor.restore.jdbc.url");
        assertNotNull(url);
        assertTrue(url.matches("jdbc:postgresql://127\\.0\\.0\\.1:15439/ep_recovery_[a-f0-9]{12}_source"));
        var ds=new PGSimpleDataSource();ds.setURL(url);ds.setUser("cacf_test");ds.setPassword("cacf-test-only");
        var compiler=new com.eventmanagement.processor.adapters.rules.RuleCompiler();
        var registry=new PostgresRuleStore(ds,compiler);
        for(int version=1;version<=2;version++) {
            String json=com.eventmanagement.processor.adapters.rules.RuleCompilerTest.definition("restore-policy",version,10,
                    com.eventmanagement.processor.adapters.rules.RuleCompilerTest.leaf("event.severity","GTE","2"),
                    version==1?"STATE_ONLY":"SUPPRESS_INTEGRATIONS");
            long revision=registry.createVersion("restore-tenant",json,"restore-test","prepare backup fixture");
            registry.changeStatus("restore-tenant","restore-policy",version,PostgresRuleStore.Status.ENABLED,
                    revision,"restore-test","activate backup fixture");
        }
        assertEquals(2,registry.snapshot("restore-tenant").rules().getFirst().version());
    }

    @Test void restoredPendingOutputAndReplayUseProductionAdapters() throws Exception {
        String url = System.getProperty("processor.restore.jdbc.url");
        assertNotNull(url, "A fresh isolated restore database is required");
        assertTrue(url.matches("jdbc:postgresql://127\\.0\\.0\\.1:15439/ep_recovery_[a-f0-9]{12}_restored"));
        var ds = new PGSimpleDataSource();
        ds.setURL(url); ds.setUser("cacf_test"); ds.setPassword("cacf-test-only");
        try (var c = ds.getConnection(); var s = c.createStatement(); var r = s.executeQuery("SELECT current_database()")) {
            r.next(); assertEquals(url.substring(url.lastIndexOf('/') + 1), r.getString(1));
        }
        var registry=new PostgresRuleStore(ds,new com.eventmanagement.processor.adapters.rules.RuleCompiler());
        var snapshot=registry.snapshot("restore-tenant");
        assertEquals(1,snapshot.rules().size());assertEquals(2,snapshot.rules().getFirst().version());
        assertEquals("restore-policy",snapshot.rules().getFirst().id());
        var event=new com.eventmanagement.processor.domain.Event("restored-event","key","restore-tenant",
                com.eventmanagement.processor.domain.Event.Status.PROBLEM,3,java.time.Instant.EPOCH,"{}");
        assertEquals(com.eventmanagement.processor.domain.StageResult.Directive.SUPPRESS_INTEGRATIONS,snapshot.evaluate(event).directive());
        assertFalse(new PostgresProcessingStore(ds).accept("restore-pending", "restore-hash",
                "restore-event", "restore-tenant", "{}", "events.normalized", "restore-key", "{}"));
        var sent = new ArrayList<String>();
        var dispatcher = new OutboxDispatcher();
        dispatcher.dataSource = ds; dispatcher.brokers = "unused:9092";
        dispatcher.batchSize = 10; dispatcher.initialRetryMs = 1000; dispatcher.maxRetryMs = 30000;
        dispatcher.producer = (ProducerTemplate) Proxy.newProxyInstance(getClass().getClassLoader(),
                new Class<?>[]{ProducerTemplate.class}, (proxy, method, args) -> {
                    if (method.getName().equals("sendBodyAndHeader")) {
                        sent.add(args[1] + "|" + args[3]); return null;
                    }
                    throw new UnsupportedOperationException(method.getName());
                });
        dispatcher.dispatch(); dispatcher.dispatch();
        assertEquals(1, sent.size());
        assertTrue(sent.get(0).contains("recovery-test"));
        assertTrue(sent.get(0).endsWith("|restore-key"));
        try (var c = ds.getConnection(); var s = c.createStatement(); var r = s.executeQuery("""
                SELECT attempts, published_at IS NOT NULL, last_error_code
                FROM event_processor.output_outbox WHERE message_id='restore-pending'
                """)) {
            assertTrue(r.next()); assertEquals(2, r.getLong(1));
            assertTrue(r.getBoolean(2)); assertNull(r.getString(3));
        }
    }
}
