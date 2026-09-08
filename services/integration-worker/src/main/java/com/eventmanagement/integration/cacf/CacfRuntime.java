package com.eventmanagement.integration.cacf;

import io.quarkus.runtime.StartupEvent;
import jakarta.annotation.PreDestroy;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.concurrent.*;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.component.kafka.KafkaConstants;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

@ApplicationScoped
public class CacfRuntime {
    private static final Logger LOG=Logger.getLogger(CacfRuntime.class);
    private final AutomationRepository repository;private final NextHttpClient client;private final CacfSettings settings;private final ProducerTemplate producer;private final String topic,commands,brokers;
    private ScheduledExecutorService executor;
    @Inject public CacfRuntime(AutomationRepository repository,NextHttpClient client,CacfSettings settings,ProducerTemplate producer,
            @ConfigProperty(name="kafka.topic.integration.results") String topic,@ConfigProperty(name="kafka.topic.integration.commands") String commands,@ConfigProperty(name="kafka.bootstrap.servers") String brokers){
        this.repository=repository;this.client=client;this.settings=settings;this.producer=producer;this.topic=topic;this.commands=commands;this.brokers=brokers;
    }
    void start(@Observes StartupEvent event){
        if(!settings.enabled)return;
        executor=Executors.newScheduledThreadPool(3,r->{Thread t=new Thread(r,"cacf-worker");t.setDaemon(true);return t;});
        executor.scheduleWithFixedDelay(()->guard(this::dispatch),1,1,TimeUnit.SECONDS);
        executor.scheduleWithFixedDelay(()->guard(()->repository.expire(settings.acknowledgementTimeout+settings.httpTimeout)),1,1,TimeUnit.SECONDS);
        executor.scheduleWithFixedDelay(()->guard(this::publish),1,1,TimeUnit.SECONDS);
    }
    private void dispatch() throws Exception {
        var job=repository.claimDispatch();if(job==null)return;
        var e=job.execution();String xml;
        try {
            xml=job.operation().equals("CREATE")?NextAdapter.create(e.path("request"),e.path("transaction_number").asText(),Instant.now())
                    :NextAdapter.ticketUpdate(e.path("request"),e.path("provider_execution_id").asText(),e.path("itsm_ticket_number").asText(),e.path("transaction_number").asText()+"-ticket",Instant.now());
            repository.recordRequest(job,xml.getBytes(StandardCharsets.UTF_8));
            var response=client.send(job.operation(),xml);
            boolean valid=response.status()>=200 && response.status()<300;
            if(valid){try{SecureXml.parse(response.body(),settings.maxXmlBytes);}catch(IllegalArgumentException failure){valid=false;}}
            repository.recordDispatch(job,response.body(),response.status(),valid);
        }catch(Exception failure){
            // Preserve uncertainty, never replay a mutating HTTP call after restart.
            LOG.warnf("CACF dispatch requires review: executionId=%s operation=%s errorType=%s",job.executionId(),job.operation(),failure.getClass().getSimpleName());
            repository.recordDispatch(job,new byte[0],0,false);
        }
    }
    private void publish() throws Exception {
        for(int i=0;i<100;i++)if(!repository.publishOne((type,payload)->{
            String destination=type.startsWith("SERVICENOW_")?commands:topic;
            producer.sendBodyAndHeader("kafka:"+destination+"?brokers="+brokers+"&requestRequiredAcks=all",payload.toString(),KafkaConstants.KEY,payload.path("eventKey").asText());
        }))break;
    }
    @FunctionalInterface private interface Task {void run()throws Exception;}
    private void guard(Task task){try{task.run();}catch(Exception e){LOG.warnf("CACF background operation deferred: errorType=%s",e.getClass().getSimpleName());}}
    @PreDestroy void stop(){if(executor!=null)executor.shutdownNow();}
}
