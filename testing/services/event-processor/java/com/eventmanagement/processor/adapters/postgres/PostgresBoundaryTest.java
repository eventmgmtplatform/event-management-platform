package com.eventmanagement.processor.adapters.postgres;

import org.junit.jupiter.api.*;
import org.postgresql.ds.PGSimpleDataSource;
import org.apache.camel.ProducerTemplate;
import java.lang.reflect.Proxy;
import java.util.*;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;

class PostgresBoundaryTest {
    private PGSimpleDataSource ds;
    private String id;
    @BeforeEach void setup() throws Exception {
        String url=System.getProperty("processor.test.jdbc.url");
        Assumptions.assumeTrue(url!=null,"Isolated PostgreSQL URL required");
        ds=new PGSimpleDataSource();ds.setURL(url);ds.setUser("cacf_test");ds.setPassword("cacf-test-only");
        try(var c=ds.getConnection();var s=c.createStatement();var r=s.executeQuery("SELECT current_database()")) {
            r.next();assertEquals("cacf_test",r.getString(1));
        }
        id=UUID.randomUUID().toString();
    }
    @AfterEach void cleanup() throws Exception {
        if(ds==null)return;
        try(var c=ds.getConnection()) {
            for(String table:List.of("output_outbox","processing_record"))
                try(var s=c.prepareStatement("DELETE FROM event_processor."+table+" WHERE processing_id=?")) {
                    s.setString(1,id);s.executeUpdate();
                }
        }
    }
    boolean accept(String hash,String output) throws Exception {
        return new PostgresProcessingStore(ds).accept(id,hash,"event","tenant","{}","events.normalized","key",output);
    }
    int count(String table) throws Exception {
        try(var c=ds.getConnection();var s=c.prepareStatement("SELECT count(*) FROM event_processor."+table+" WHERE processing_id=?")) {
            s.setString(1,id);try(var r=s.executeQuery()){r.next();return r.getInt(1);}
        }
    }
    @Test void concurrentReplayInsertsExactlyOneRecordAndIntent() throws Exception {
        try(var pool=Executors.newFixedThreadPool(2)) {
            var a=pool.submit(()->accept("hash","{}"));var b=pool.submit(()->accept("hash","{}"));
            assertNotEquals(a.get(10,TimeUnit.SECONDS),b.get(10,TimeUnit.SECONDS));
        }
        assertEquals(1,count("processing_record"));assertEquals(1,count("output_outbox"));
    }
    @Test void collisionDoesNotOverwriteHistoricalOutput() throws Exception {
        assertTrue(accept("first","{}"));
        assertThrows(IllegalArgumentException.class,()->accept("different","{}"));
        assertFalse(accept("first","{}"));assertEquals(1,count("output_outbox"));
    }
    @Test void invalidOutputRollsBackEvidenceAndIntentTogether() {
        assertThrows(Exception.class,()->accept("hash","invalid-json"));
        assertDoesNotThrow(()->{assertEquals(0,count("processing_record"));assertEquals(0,count("output_outbox"));});
    }
    @Test void uncertainPublicationRetriesSamePayloadAfterNewDispatcher() throws Exception {
        accept("hash","{\"stable\":true}");
        var deliveries=new ArrayList<String>();
        var first=dispatcher(deliveries,true);first.dispatch();
        assertEquals(1,deliveries.size());assertEquals(0,published());
        var restarted=dispatcher(deliveries,false);
        restarted.dispatch();assertEquals(1,deliveries.size(),"Retry must honor the durable deadline");
        try(var c=ds.getConnection();var s=c.prepareStatement("UPDATE event_processor.output_outbox SET next_attempt_at=now() WHERE message_id=?")) {
            s.setString(1,id);s.executeUpdate();
        }
        restarted.dispatch();
        assertEquals(2,deliveries.size());assertEquals(deliveries.get(0),deliveries.get(1));assertEquals(1,published());
        restarted.dispatch();assertEquals(2,deliveries.size());
    }
    private int published() throws Exception {
        try(var c=ds.getConnection();var s=c.prepareStatement("SELECT count(*) FROM event_processor.output_outbox WHERE message_id=? AND published_at IS NOT NULL")) {
            s.setString(1,id);try(var r=s.executeQuery()){r.next();return r.getInt(1);}
        }
    }
    private OutboxDispatcher dispatcher(List<String> deliveries,boolean fail) {
        var dispatcher=new OutboxDispatcher();dispatcher.dataSource=ds;dispatcher.brokers="unused:9092";dispatcher.batchSize=1;dispatcher.initialRetryMs=60000;dispatcher.maxRetryMs=60000;
        dispatcher.producer=(ProducerTemplate)Proxy.newProxyInstance(getClass().getClassLoader(),new Class<?>[]{ProducerTemplate.class},(proxy,method,args)->{
            if(method.getName().equals("sendBodyAndHeader")) {
                deliveries.add(args[1]+"|"+args[3]);if(fail)throw new RuntimeException("delivery outcome unknown");return null;
            }
            throw new UnsupportedOperationException(method.getName());
        });
        return dispatcher;
    }
}
