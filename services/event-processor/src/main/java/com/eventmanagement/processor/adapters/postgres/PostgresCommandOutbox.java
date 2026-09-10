package com.eventmanagement.processor.adapters.postgres;
import com.eventmanagement.processor.ports.out.CommandOutbox;
import com.eventmanagement.processor.domain.*;
import com.eventmanagement.processor.adapters.kafka.WorkerCommandAdapter;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.Connection;
import java.time.Instant;
import java.util.List;
/** Semantic ledger survives output cleanup; the first complete Worker envelope is immutable. */
public final class PostgresCommandOutbox implements CommandOutbox {
    private final Connection connection;private final ObjectMapper mapper;private final String tenant;
    public PostgresCommandOutbox(Connection connection,ObjectMapper mapper,String tenant){this.connection=connection;this.mapper=mapper;this.tenant=tenant;}
    public boolean exists(String id) {
        try(var s=connection.prepareStatement("SELECT 1 FROM event_processor.integration_command WHERE tenant=? AND command_id=?")) {
            s.setString(1,tenant);s.setString(2,id);s.setQueryTimeout(5);try(var r=s.executeQuery()){return r.next();}
        }catch(Exception e){throw new IllegalStateException("COMMAND_LEDGER_UNAVAILABLE",e);}
    }
    public void enqueue(Event event,String processingId,Instant createdAt,List<ProcessingContext.CommandIntent> commands)throws Exception {
        if(!tenant.equals(event.tenant()))throw new IllegalArgumentException("COMMAND_TENANT_MISMATCH");
        var adapter=new WorkerCommandAdapter(mapper);
        for(var intent:commands) {
            if(exists(intent.commandId()))continue;
            // These commands describe a correlation situation, not an individual source event's lifecycle.
            var aggregate=new Event(intent.cycleId(),"correlation:"+intent.cycleId(),tenant,event.status(),event.severity(),event.receivedAt(),event.originalJson());
            var envelope=adapter.encode(aggregate,processingId,intent.cycleId(),intent.configuration(),intent.integrationType(),intent.operation(),mapper.valueToTree(intent.payload()),createdAt);
            if(!intent.commandId().equals(envelope.path("commandId").asText()))throw new IllegalStateException("COMMAND_IDENTITY_MAPPING_MISMATCH");
            var metadata=(com.fasterxml.jackson.databind.node.ObjectNode)envelope.path("metadata");
            metadata.put("sourceEventId",event.eventId());metadata.put("sourceEventKey",event.eventKey());metadata.put("correlationGroupId",intent.cycleId());
            String json=mapper.writeValueAsString(envelope);
            try(var s=connection.prepareStatement("INSERT INTO event_processor.integration_command(command_id,processing_id,tenant,envelope) VALUES (?,?,?,?::jsonb)")) {
                s.setString(1,intent.commandId());s.setString(2,processingId);s.setString(3,tenant);s.setString(4,json);s.executeUpdate();
            }
            try(var s=connection.prepareStatement("INSERT INTO event_processor.output_outbox(message_id,processing_id,topic,message_key,payload) VALUES (?,?,'integration.commands',?,?::jsonb)")) {
                s.setString(1,intent.commandId());s.setString(2,processingId);s.setString(3,aggregate.eventKey());s.setString(4,json);s.executeUpdate();
            }
            if(envelope.path("payload").has("lifecycle"))
                new PostgresLifecycleSession(connection,mapper,tenant).register(event,envelope);
        }
    }
}
