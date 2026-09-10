package com.eventmanagement.processor.adapters.postgres;
import com.eventmanagement.processor.ports.out.*;
import com.eventmanagement.processor.domain.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import javax.sql.DataSource;

@ApplicationScoped
public class PostgresProcessingUnitOfWork implements ProcessingUnitOfWork {
    private final DataSource dataSource;private final ObjectMapper mapper;private final PostgresRuleStore rules;
    @Inject public PostgresProcessingUnitOfWork(DataSource ds,ObjectMapper mapper,PostgresRuleStore rules){this.dataSource=ds;this.mapper=mapper;this.rules=rules;}
    public void execute(Event event,String hash,Work work)throws Exception {
        try(var c=dataSource.getConnection()) {
            c.setAutoCommit(false);
            try {
                try(var lock=c.prepareStatement("SELECT pg_advisory_xact_lock(hashtextextended(?,13))")) {
                    lock.setString(1,event.tenant());lock.setQueryTimeout(5);lock.execute();
                }
                String id=StableIdentity.of("processing-v1",event.tenant(),event.eventId());
                try(var s=c.prepareStatement("SELECT input_hash FROM event_processor.processing_record WHERE processing_id=?")) {
                    s.setString(1,id);s.setQueryTimeout(5);try(var r=s.executeQuery()) {
                        if(r.next()) {if(!hash.equals(r.getString(1)))throw new IllegalArgumentException("EVENT_ID_COLLISION");c.commit();return;}
                    }
                }
                boolean[] persisted={false};
                ProcessingStore session=(processingId,inputHash,eventId,tenant,evidence,topic,key,output)->{
                    if(persisted[0])throw new IllegalStateException("ONE_INPUT_RECORD_REQUIRED");
                    if(!id.equals(processingId) || !hash.equals(inputHash) || !event.tenant().equals(tenant))throw new IllegalArgumentException("TRANSACTION_SCOPE_MISMATCH");
                    boolean inserted=PostgresProcessingStore.accept(c,processingId,inputHash,eventId,tenant,evidence,topic,key,output);
                    if(!inserted)throw new IllegalStateException("REPLAY_RACE_RETRY");
                    // Only accepted decisions have correlationApplied=true; DLQ never requests state.
                    var decision=mapper.readTree(evidence);
                    if(decision.path("correlationApplied").asBoolean(false)) {
                        var request=new com.eventmanagement.processor.adapters.kafka.StateRequestAdapter(mapper)
                                .encode(event,processingId,decision);
                        try(var statement=c.prepareStatement("INSERT INTO event_processor.output_outbox(message_id,processing_id,topic,message_key,payload) VALUES (?,?,'events.state.requested',?,?::jsonb)")) {
                            statement.setString(1,request.path("messageId").asText());statement.setString(2,processingId);
                            statement.setString(3,event.eventKey());statement.setString(4,request.toString());statement.executeUpdate();
                        }
                    }
                    if(decision.path("correlationApplied").asBoolean(false))
                        new PostgresLifecycleSession(c,mapper,event.tenant()).observe(event,decision);
                    persisted[0]=true;return true;
                };
                work.run(session,new PostgresCorrelationSession(c,mapper,event.tenant()),tenant->{if(!event.tenant().equals(tenant))throw new IllegalArgumentException("SNAPSHOT_TENANT_MISMATCH");return rules.snapshot(c,tenant);},new PostgresCommandOutbox(c,mapper,event.tenant()));
                if(!persisted[0])throw new IllegalStateException("PROCESSING_RECORD_REQUIRED");
                c.commit();
            }catch(Exception failure){c.rollback();throw failure;}
        }
    }
}
