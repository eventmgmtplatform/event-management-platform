package com.eventmanagement.state;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.component.kafka.KafkaConstants;
import org.apache.camel.component.kafka.consumer.KafkaManualCommit;

@Named("stateRequestProcessor")
@ApplicationScoped
public class StateRequestProcessor implements Processor {
    @Inject ObjectMapper mapper;
    @Inject StateTransitionRepository transitions;
    @Inject EventStateRepository repository;
    @Inject StateProjectionService projections;

    public void process(Exchange exchange) throws Exception {
        var commit=exchange.getMessage().getHeader(KafkaConstants.MANUAL_COMMIT,KafkaManualCommit.class);
        if (commit==null) throw new IllegalStateException("MANUAL_COMMIT_REQUIRED");
        String body=exchange.getMessage().getBody(String.class);
        StateTransitionRepository.Applied applied;
        try {
            StateRequest request;
            try { request=StateRequest.parse(body==null ? null : mapper.readTree(body)); }
            catch (JsonProcessingException error) { throw new RejectedIntegrationResult("INVALID_STATE_JSON"); }
            applied=transitions.apply(request);
        } catch (RejectedIntegrationResult invalid) {
            String topic=exchange.getMessage().getHeader(KafkaConstants.TOPIC,String.class);
            Integer partition=exchange.getMessage().getHeader(KafkaConstants.PARTITION,Integer.class);
            Long offset=exchange.getMessage().getHeader(KafkaConstants.OFFSET,Long.class);
            if (topic==null || partition==null || offset==null) throw new IllegalStateException("SOURCE_COORDINATES_REQUIRED");
            repository.quarantine(topic,partition,offset,body==null ? "null" : body,invalid.getMessage());
            commit.commit();exchange.setProperty("disposition","QUARANTINED");return;
        }
        projections.project(applied.state().eventKey);
        commit.commit();
        exchange.setProperty("disposition",applied.disposition());
    }
}
