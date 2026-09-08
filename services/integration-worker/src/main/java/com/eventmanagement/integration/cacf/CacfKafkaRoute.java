package com.eventmanagement.integration.cacf;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.eventmanagement.integration.IntegrationCommandProcessor;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.kafka.KafkaConstants;
import org.apache.camel.component.kafka.consumer.KafkaManualCommit;
import org.eclipse.microprofile.config.inject.ConfigProperty;

/** Separate group: CACF admission commits only after its database transaction. */
@ApplicationScoped
public class CacfKafkaRoute extends RouteBuilder {
    @Inject CacfSettings settings;
    @Inject ObjectMapper mapper;
    @Inject IntegrationCommandProcessor envelope;
    @Inject CacfCommandProcessor admission;
    @Inject ProducerTemplate producer;
    @ConfigProperty(name="kafka.topic.events.dlq") String dlq;
    @ConfigProperty(name="kafka.bootstrap.servers") String brokers;
    @Override public void configure() {
        if(!settings.enabled)return;
        from("kafka:{{kafka.topic.integration.commands}}?brokers={{kafka.bootstrap.servers}}&groupId=cacf-admission&autoOffsetReset=earliest&autoCommitEnable=false&allowManualCommit=true&breakOnFirstError=true")
            .routeId("cacf-command-admission")
            .process(exchange -> {
                KafkaManualCommit commit=exchange.getMessage().getHeader(KafkaConstants.MANUAL_COMMIT,KafkaManualCommit.class);
                if(commit==null)throw new IllegalStateException("CACF manual Kafka commit unavailable");
                String body=exchange.getMessage().getBody(String.class);
                com.fasterxml.jackson.databind.JsonNode command;
                try {command=mapper.readTree(body);}catch(com.fasterxml.jackson.core.JsonProcessingException e){commit.commit();return;}
                if(command==null || !"CACF".equalsIgnoreCase(command.path("integrationType").asText())){commit.commit();return;}
                try {envelope.process(exchange);admission.process(exchange);}
                catch(IllegalArgumentException e){
                    var failure=mapper.createObjectNode().put("integrationType","CACF").put("errorCode","INVALID_AUTOMATION_COMMAND")
                        .put("commandId",command.path("commandId").asText("")).put("message",e.getMessage());
                    producer.sendBodyAndHeader("kafka:"+dlq+"?brokers="+brokers+"&requestRequiredAcks=all",failure.toString(),KafkaConstants.KEY,command.path("eventKey").asText("CACF"));
                }
                commit.commit();
            });
    }
}
