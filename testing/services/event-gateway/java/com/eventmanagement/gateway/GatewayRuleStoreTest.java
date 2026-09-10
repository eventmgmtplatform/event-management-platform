package com.eventmanagement.gateway;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.postgresql.ds.PGSimpleDataSource;
import java.nio.file.*;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;

/** Must target an isolated disposable PostgreSQL instance, never the application database. */
@EnabledIfEnvironmentVariable(named="GATEWAY_RULE_TEST_JDBC_URL",matches=".+")
class GatewayRuleStoreTest {
    @Test void migrationPersistenceHistoryAndConcurrentRevisionGuard() throws Exception {
        var ds=new PGSimpleDataSource();ds.setURL(System.getenv("GATEWAY_RULE_TEST_JDBC_URL"));ds.setUser("postgres");
        String migration=Files.readString(Path.of("../../infrastructure/postgres/init/026-gateway-rules.sql"))
                .replace("\\set ON_ERROR_STOP on","");
        try(var c=ds.getConnection();var s=c.createStatement()) {
            s.execute("CREATE SCHEMA IF NOT EXISTS event_management");
            s.execute("DO $$ BEGIN IF NOT EXISTS(SELECT FROM pg_roles WHERE rolname='oem_gateway_collector') THEN CREATE ROLE oem_gateway_collector; END IF; END $$");
            s.execute(migration);s.execute(migration);
        }
        var store=new GatewayRuleStore();store.dataSource=ds;store.mapper=new ObjectMapper();
        String id="test_"+java.util.UUID.randomUUID().toString().replace("-","");
        var rule=store.mapper.readTree("{\"id\":\""+id+"\",\"stage\":\"ENRICHMENT\",\"priority\":1,\"enabled\":true,\"match\":{},\"set\":{\"site\":\"MX\"}}");
        assertEquals(1,store.save(rule,0).revision());
        assertThrows(GatewayRuleStore.Conflict.class,()->store.save(rule,0));
        try(var executor=Executors.newFixedThreadPool(2)) {
            var start=new CountDownLatch(1);
            Callable<Boolean> update=()-> { start.await();try { store.save(rule,1);return true; }catch(GatewayRuleStore.Conflict e) {return false;} };
            var a=executor.submit(update);var b=executor.submit(update);start.countDown();
            assertNotEquals(a.get(10,TimeUnit.SECONDS),b.get(10,TimeUnit.SECONDS));
        }
        var reloaded=new GatewayRuleStore();reloaded.dataSource=ds;reloaded.mapper=store.mapper;
        assertEquals(2,reloaded.list().stream().filter(e->e.rule().path("id").asText().equals(id)).findFirst().orElseThrow().revision());
        var history=reloaded.history(id);assertEquals(2,history.size());assertEquals(2,history.get(0).revision());
        // If the history insert fails, the current rule update must roll back too.
        try(var c=ds.getConnection();var s=c.prepareStatement("INSERT INTO event_management.gateway_rule_history(id,revision,definition) VALUES (?,3,'{}')")) {
            s.setString(1,id);s.executeUpdate();
        }
        assertThrows(java.sql.SQLException.class,()->store.save(rule,2));
        assertEquals(2,reloaded.list().stream().filter(e->e.rule().path("id").asText().equals(id)).findFirst().orElseThrow().revision());
    }
}
