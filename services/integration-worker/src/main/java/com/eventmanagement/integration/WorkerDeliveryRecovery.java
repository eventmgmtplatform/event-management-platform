package com.eventmanagement.integration;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import javax.sql.DataSource;
import org.apache.camel.ProducerTemplate;

import org.apache.camel.component.kafka.KafkaConstants;
import org.eclipse.microprofile.config.inject.ConfigProperty;
/** Ledger-backed result delivery and stale command replay; never invokes a provider directly. */
@ApplicationScoped
public class WorkerDeliveryRecovery {
    @Inject DataSource ds;
    @Inject ProducerTemplate producer;
    @ConfigProperty(name="kafka.bootstrap.servers") String brokers;
    @ConfigProperty(name="kafka.topic.integration.commands") String commands;
    @ConfigProperty(name="kafka.topic.integration.results") String results;
    private java.util.concurrent.ScheduledExecutorService executor;
    void start(@jakarta.enterprise.event.Observes io.quarkus.runtime.StartupEvent event) {
        executor=java.util.concurrent.Executors.newSingleThreadScheduledExecutor(r->{var t=new Thread(r,"worker-delivery-recovery");t.setDaemon(true);return t;});
        executor.scheduleWithFixedDelay(()->{try{dispatch();}catch(Exception failure){org.jboss.logging.Logger.getLogger(getClass()).warn("WORKER_DELIVERY_RETRY_PENDING");}},5,5,java.util.concurrent.TimeUnit.SECONDS);
    }
    @jakarta.annotation.PreDestroy void stop(){if(executor!=null)executor.shutdownNow();}
    public void dispatch()throws Exception {
        for(int i=0;i<20;i++)try(var c=ds.getConnection()) {
            c.setAutoCommit(false);
            try {
                String id,key,payload;boolean complete;
                try(var s=c.prepareStatement("""
                    SELECT command_id,event_key,execution_status,result_payload::text,command_payload::text
                    FROM event_management.integration_command_execution
                    WHERE (execution_status='COMPLETED' AND result_published_at IS NULL)
                       OR (execution_status='IN_PROGRESS' AND lease_expires_at < now()
                           AND (recovery_published_at IS NULL OR recovery_published_at < now()-interval '60 seconds'))
                    ORDER BY updated_at LIMIT 1 FOR UPDATE SKIP LOCKED
                    """)) {
                    try(var r=s.executeQuery()){if(!r.next()){c.commit();return;}id=r.getString(1);key=r.getString(2);complete="COMPLETED".equals(r.getString(3));payload=r.getString(complete?4:5);}
                }
                producer.sendBodyAndHeader("kafka:"+(complete?results:commands)+"?brokers="+brokers+"&requestRequiredAcks=all&maxBlockMs=10000&deliveryTimeoutMs=10000&requestTimeoutMs=5000",payload,KafkaConstants.KEY,key);
                try(var s=c.prepareStatement("UPDATE event_management.integration_command_execution SET "+(complete?"result_published_at":"recovery_published_at")+"=now() WHERE command_id=?")){s.setString(1,id);s.executeUpdate();}
                c.commit();
            }catch(Exception failure){c.rollback();throw failure;}
        }
    }
}
