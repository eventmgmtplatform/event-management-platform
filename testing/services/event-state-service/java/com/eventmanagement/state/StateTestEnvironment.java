package com.eventmanagement.state;

import io.quarkus.test.common.QuarkusTestResourceLifecycleManager;
import org.postgresql.ds.PGSimpleDataSource;
import java.nio.file.*;
import java.util.*;

/** Disposable database on the dedicated ESS test server; never the platform database. */
public class StateTestEnvironment implements QuarkusTestResourceLifecycleManager {
    private String database;
    private Path adminCredentials;
    private PGSimpleDataSource admin;
    public Map<String, String> start() {
        try {
            if (!"jdbc:postgresql://127.0.0.1:15440/ess_test".equals(System.getProperty("ess.test.jdbc.url")))
                throw new IllegalStateException("ESS_ISOLATED_DATABASE_REQUIRED");
            admin = source("ess_test");
            database = "ess_test_" + UUID.randomUUID().toString().replace("-", "");
            try (var c = admin.getConnection(); var s = c.createStatement()) { s.execute("CREATE DATABASE " + database); }
            try (var c = source(database).getConnection(); var s = c.createStatement()) {
                for (String file : List.of("002-event-state.sql", "003-integration-result-idempotency.sql", "016-ess-quarantine.sql", "017-ess-lifecycle.sql"))
                    s.execute(Files.readString(Path.of("../../infrastructure/postgres/init", file)).replace("\\set ON_ERROR_STOP on", ""));
            }
            adminCredentials=Files.createTempFile("ess-admin-test-", ".json");
            Files.writeString(adminCredentials, "{\"tenants\":{\"test-a\":\"token-a\",\"test-b\":\"token-b\"},\"operatorToken\":\"operator-only\"}");
            return Map.of("ess.admin.credentials-file", adminCredentials.toString(),"quarkus.datasource.jdbc.url", "jdbc:postgresql://127.0.0.1:15440/" + database,
                    "quarkus.datasource.username", "ess_test", "quarkus.datasource.password", "ess-test-only",
                    "quarkus.http.test-port", "0", "camel.main.routes-include-pattern", "classpath:no-test-routes/*.xml",
                    "quarkus.datasource.devservices.enabled", "false");
        } catch (Exception error) { stop(); throw new IllegalStateException("ESS_TEST_SETUP_FAILED", error); }
    }
    public void stop() {
        if(adminCredentials!=null)try { Files.deleteIfExists(adminCredentials); }catch(Exception e){throw new IllegalStateException(e);}

        if (database != null) {
            try (var c = admin.getConnection(); var s = c.createStatement()) {
                s.execute("DROP DATABASE IF EXISTS " + database + " WITH (FORCE)"); database = null;
            } catch (Exception error) { throw new IllegalStateException("ESS_TEST_CLEANUP_FAILED", error); }
        }
    }
    private static PGSimpleDataSource source(String database) {
        var ds = new PGSimpleDataSource(); ds.setURL("jdbc:postgresql://127.0.0.1:15440/" + database);
        ds.setUser("ess_test"); ds.setPassword("ess-test-only"); return ds;
    }
}
