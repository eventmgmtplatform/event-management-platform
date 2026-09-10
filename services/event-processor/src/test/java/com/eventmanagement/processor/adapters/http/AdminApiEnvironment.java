package com.eventmanagement.processor.adapters.http;

import io.quarkus.test.common.QuarkusTestResourceLifecycleManager;
import org.postgresql.ds.PGSimpleDataSource;
import java.nio.file.*;
import java.util.*;

/** Isolated database for the unauthenticated functional administration API. */
public class AdminApiEnvironment implements QuarkusTestResourceLifecycleManager {
    private String database;
    private PGSimpleDataSource admin;
    public Map<String,String> start() {
        try {
            String url=System.getProperty("processor.test.jdbc.url");
            if(url==null || !url.equals("jdbc:postgresql://127.0.0.1:15439/cacf_test"))throw new IllegalStateException("ISOLATED_DATABASE_REQUIRED");
            admin=source(url);
            try(var c=admin.getConnection();var s=c.createStatement();var r=s.executeQuery("SELECT current_database()")){
                r.next();if(!"cacf_test".equals(r.getString(1)))throw new IllegalStateException("WRONG_DATABASE");
            }
            database="ep_admin_"+UUID.randomUUID().toString().replace("-","");
            try(var c=admin.getConnection();var s=c.createStatement()){s.execute("CREATE DATABASE "+database);}
            String testUrl=url.substring(0,url.lastIndexOf('/')+1)+database;
            try(var c=source(testUrl).getConnection();var s=c.createStatement()) {
                for(String file:List.of("009-event-processor.sql","010-processor-outbox-recovery.sql","011-processor-rule-registry.sql","012-processor-administration.sql","012-processor-administration.sql"))
                    s.execute(Files.readString(Path.of("../../infrastructure/postgres/init",file)).replace("\\set ON_ERROR_STOP on",""));
            }
            return Map.ofEntries(Map.entry("quarkus.datasource.jdbc.url",testUrl),Map.entry("quarkus.datasource.username","cacf_test"),
                    Map.entry("quarkus.datasource.password","cacf-test-only"),Map.entry("quarkus.http.test-port","0"),
                    Map.entry("processor.admin.enabled","true"),
                    Map.entry("processor.kafka.enabled","false"),Map.entry("processor.outbox.poll-ms","3600000"));
        }catch(Exception e){stop();throw new IllegalStateException("ADMIN_TEST_ENVIRONMENT_FAILED",e);}
    }
    public void stop() {
        if(database!=null) {
            try(var c=admin.getConnection();var s=c.createStatement()){s.execute("DROP DATABASE "+database);database=null;}
            catch(Exception e){throw new IllegalStateException("ADMIN_TEST_CLEANUP_FAILED",e);}
        }
    }
    private static PGSimpleDataSource source(String url){var ds=new PGSimpleDataSource();ds.setURL(url);ds.setUser("cacf_test");ds.setPassword("cacf-test-only");return ds;}
}
