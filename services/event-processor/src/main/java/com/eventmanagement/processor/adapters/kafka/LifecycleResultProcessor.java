package com.eventmanagement.processor.adapters.kafka;
import com.eventmanagement.processor.adapters.postgres.PostgresLifecycleSession;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import javax.sql.DataSource;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.component.kafka.KafkaConstants;
import org.apache.camel.component.kafka.consumer.KafkaManualCommit;
@ApplicationScoped
public class LifecycleResultProcessor implements Processor {
    @Inject DataSource dataSource;
    @Inject ObjectMapper mapper;
    public void process(Exchange exchange)throws Exception {
        var commit=exchange.getMessage().getHeader(KafkaConstants.MANUAL_COMMIT,KafkaManualCommit.class);
        if(commit==null)throw new IllegalStateException("MANUAL_COMMIT_REQUIRED");
        String body=exchange.getMessage().getBody(String.class);
        com.fasterxml.jackson.databind.JsonNode r=null;
        String rejection=null;
        try {
            r=mapper.readTree(body);
            if(r==null || !r.isObject())throw new IllegalArgumentException();
            for(String field:java.util.List.of("tenant","eventId","eventKey","commandId","resultId","integrationType","operation","status"))
                if(!r.path(field).isTextual() || r.path(field).asText().isBlank() || r.path(field).asText().length()>128)throw new IllegalArgumentException();
        }catch(com.fasterxml.jackson.core.JsonProcessingException | IllegalArgumentException invalid){rejection="INVALID_LIFECYCLE_RESULT_ENVELOPE";}
        String tenant=rejection==null?r.path("tenant").asText():"";
        try(var c=dataSource.getConnection()) {
            c.setAutoCommit(false);
            try {
                try(var s=c.prepareStatement("SELECT pg_advisory_xact_lock(hashtextextended(?,13))")){s.setString(1,tenant);s.setQueryTimeout(5);s.execute();}
                if(rejection==null)new PostgresLifecycleSession(c,mapper,tenant).result(r);
                else try(var s=c.prepareStatement("INSERT INTO event_processor.lifecycle_rejection(topic,partition_id,offset_id,reason,payload_hash) VALUES (?,?,?,?,?) ON CONFLICT DO NOTHING")) {
                    s.setString(1,exchange.getMessage().getHeader(KafkaConstants.TOPIC,String.class));
                    s.setInt(2,exchange.getMessage().getHeader(KafkaConstants.PARTITION,Integer.class));
                    s.setLong(3,exchange.getMessage().getHeader(KafkaConstants.OFFSET,Long.class));
                    s.setString(4,rejection);s.setString(5,com.eventmanagement.processor.domain.StableIdentity.of("rejection",body==null?"":body));s.executeUpdate();
                }
                c.commit();
            }catch(Exception failure){c.rollback();throw failure;}
        }
        commit.commit();
    }
}
