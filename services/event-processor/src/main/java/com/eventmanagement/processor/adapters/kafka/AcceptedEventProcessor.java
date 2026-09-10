package com.eventmanagement.processor.adapters.kafka;

import com.eventmanagement.processor.ports.in.ProcessEventUseCase;
import com.eventmanagement.processor.domain.*;
import com.eventmanagement.processor.ports.out.ProcessingStore;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.component.kafka.KafkaConstants;
import org.apache.camel.component.kafka.consumer.KafkaManualCommit;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import java.time.Instant;

@ApplicationScoped
public class AcceptedEventProcessor implements Processor {
    private final GatewayEventAdapter adapter;
    private final ProcessingStore store;
    private final ObjectMapper mapper;
    private final ProcessEventUseCase pipeline;
    @ConfigProperty(name="processor.kafka.output-topic") String outputTopic;
    @ConfigProperty(name="processor.kafka.dlq-topic") String dlqTopic;
    @Inject public AcceptedEventProcessor(GatewayEventAdapter adapter, ProcessingStore store, ObjectMapper mapper, ProcessEventUseCase pipeline) {
        this.adapter=adapter; this.store=store; this.mapper=mapper; this.pipeline=pipeline;
    }
    @Override public void process(Exchange exchange) throws Exception {
        KafkaManualCommit commit=exchange.getMessage().getHeader(KafkaConstants.MANUAL_COMMIT,KafkaManualCommit.class);
        if (commit==null) throw new IllegalStateException("MANUAL_COMMIT_REQUIRED");
        String body=exchange.getMessage().getBody(String.class);
        Event event;
        try { event=adapter.decode(body); }
        catch (IllegalArgumentException invalid) { reject(exchange,body,"INVALID_GATEWAY_CONTRACT"); commit.commit(); return; }
        var context=pipeline.process(event);
        if(context.directive()==StageResult.Directive.DEAD_LETTER) {
            ObjectNode failure=mapper.createObjectNode();
            failure.put("schemaVersion","1.0");failure.put("dlqId",context.processingId());failure.put("processingId",context.processingId());
            var failedStage=context.stages().stream().filter(s->s.status()==StageResult.Status.FAILED).findFirst().orElseThrow();
            failure.put("eventId",event.eventId());failure.put("failedAt",Instant.now().toString());failure.put("stage",failedStage.stage().name());
            failure.putObject("error").put("code",failedStage.reason()).put("message","Processing input rejected").put("retryable",false);
            String hash=StableIdentity.of("payload-v1",body);
            failure.putObject("originalEvent").put("redacted",true).put("payloadHash",hash);
            failure.set("source",source(exchange));failure.set("stages",mapper.valueToTree(context.stages()));
            failure.set("enrichment",mapper.valueToTree(context.enrichment()));
            String json=mapper.writeValueAsString(failure);
            try { store.accept(context.processingId(),hash,event.eventId(),event.tenant(),json,dlqTopic,event.eventKey(),json); }
            catch(IllegalArgumentException collision) {
                if(!"EVENT_ID_COLLISION".equals(collision.getMessage()))throw collision;
                reject(exchange,body,"EVENT_ID_COLLISION");
            }
            commit.commit();return;
        }
        ObjectNode output=(ObjectNode)mapper.readTree(event.originalJson());
        ObjectNode processing=output.has("processing") ? (ObjectNode)output.get("processing") : output.putObject("processing");
        // Keep compatibility metadata and append the canonical DA-05 result.
        var enrichment=processing.putObject("enrichment");
        enrichment.put("status",context.enrichment().status().name());enrichment.put("engine","event-processor");
        enrichment.put("processedAt",Instant.now().toString());enrichment.set("result",mapper.valueToTree(context.enrichment()));
        var details=processing.putObject("processor"); details.put("processingId",context.processingId());
        details.put("status","INCREMENTAL");
        details.put("ruleSnapshotChecksum",context.ruleSnapshot().checksum()); details.put("canonicalStatus",event.status().name());
        details.put("canonicalSeverity",event.severity()); details.put("directive",context.directive().name());
        ObjectNode evidence=mapper.createObjectNode(); evidence.put("processingId",context.processingId());
        evidence.put("eventId",event.eventId()); evidence.put("directive",context.directive().name());
        evidence.set("stages",mapper.valueToTree(context.stages()));
        evidence.set("enrichment",mapper.valueToTree(context.enrichment()));
        evidence.set("source",source(exchange));
        try {
            store.accept(context.processingId(),StableIdentity.of("payload-v1",body),event.eventId(),event.tenant(),
                    mapper.writeValueAsString(evidence),outputTopic,event.eventKey(),mapper.writeValueAsString(output));
        } catch (IllegalArgumentException collision) {
            if (!"EVENT_ID_COLLISION".equals(collision.getMessage())) throw collision;
            reject(exchange,body,"EVENT_ID_COLLISION");
        }
        // PostgreSQL record + output intent committed. Kafka publication can resume after restart.
        commit.commit();
    }
    private ObjectNode source(Exchange exchange) {
        ObjectNode source=mapper.createObjectNode();
        source.put("topic",exchange.getMessage().getHeader(KafkaConstants.TOPIC,String.class));
        source.put("partition",exchange.getMessage().getHeader(KafkaConstants.PARTITION,String.class));
        source.put("offset",exchange.getMessage().getHeader(KafkaConstants.OFFSET,String.class));
        return source;
    }
    private void reject(Exchange exchange,String body,String reason) throws Exception {
        ObjectNode source=source(exchange);
        String hash=StableIdentity.of("payload-v1",body==null?"":body);
        String id=StableIdentity.of("dlq-v1",source.toString(),hash);
        ObjectNode failure=mapper.createObjectNode(); failure.put("schemaVersion","1.0");
        failure.put("service","event-processor"); failure.put("processingId",id);
        failure.put("dlqId",id); failure.put("eventId","");
        failure.put("failedAt",Instant.now().toString()); failure.put("stage","ContractValidation");
        failure.putObject("error").put("code",reason).put("message","Input rejected").put("retryable",false);
        failure.putObject("originalEvent").put("redacted",true).put("payloadHash",hash);
        failure.put("errorCode",reason); failure.put("payloadHash",hash); failure.set("source",source);
        // Invalid raw input is deliberately not copied into DLQ/logs/audit.
        String json=mapper.writeValueAsString(failure);
        store.accept(id,hash,null,"",json,dlqTopic,id,json);
    }
}
