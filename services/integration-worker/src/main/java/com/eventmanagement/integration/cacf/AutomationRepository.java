package com.eventmanagement.integration.cacf;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.sql.*;
import java.time.Instant;
import java.util.*;
import javax.sql.DataSource;

/** All lifecycle decisions and publication intents commit in one database transaction. */
@ApplicationScoped
public class AutomationRepository {
    private final DataSource dataSource;
    private final ObjectMapper mapper;
    @Inject
    public AutomationRepository(DataSource dataSource, ObjectMapper mapper) {
        this.dataSource=dataSource; this.mapper=mapper;
    }
    public static final class Conflict extends IllegalArgumentException {
        public Conflict(String message) {super(message);}
    }
    @FunctionalInterface private interface Work<T> { T run(Connection c) throws Exception; }
    private <T> T transaction(Work<T> work) throws Exception {
        try(Connection c=dataSource.getConnection()) {
            c.setAutoCommit(false);
            try {T result=work.run(c);c.commit();return result;}
            catch(Exception e){c.rollback();throw e;}
        }
    }
    private static int update(Connection c,String sql,Object...values) throws SQLException {
        try(PreparedStatement s=c.prepareStatement(sql)){bind(s,values);return s.executeUpdate();}
    }
    private static void bind(PreparedStatement s,Object...values) throws SQLException {
        for(int i=0;i<values.length;i++)s.setObject(i+1,values[i]);
    }
    private ObjectNode row(Connection c,UUID id,boolean lock) throws Exception {
        try(PreparedStatement s=c.prepareStatement("SELECT row_to_json(e)::text FROM event_management.automation_execution e WHERE execution_id=?"+(lock?" FOR UPDATE":""))) {
            s.setObject(1,id);try(ResultSet r=s.executeQuery()){return r.next()?(ObjectNode)mapper.readTree(r.getString(1)):null;}
        }
    }
    public JsonNode get(UUID id) throws Exception {return transaction(c -> row(c,id,false));}

    public JsonNode accept(AutomationRequest request) throws Exception {
        return transaction(c -> {
            int inserted=update(c,"""
                INSERT INTO event_management.automation_execution
                (execution_id,command_id,event_id,event_key,customer_code,request,requester_id,transaction_number,itsm_ticket_number,result_timeout_seconds)
                VALUES (?,?,?,?,?,?::jsonb,?,?,?,?) ON CONFLICT DO NOTHING
                """,request.executionId(),request.commandId(),request.eventId(),request.eventKey(),request.customerCode(),
                request.payload().toString(),NextAdapter.requesterId(request.payload()),request.executionId().toString(),
                request.payload().path("ticket").path("number").asText(null),request.timeoutSeconds());
            ObjectNode stored=row(c,request.executionId(),true);
            if(stored==null || !stored.path("request").equals(request.payload())
                    || !stored.path("command_id").asText().equals(request.commandId())
                    || !stored.path("event_key").asText().equals(request.eventKey()))
                throw new Conflict("Automation identity already belongs to a different request");
            if(inserted==1) {
                update(c,"INSERT INTO event_management.automation_provider_dispatch(execution_id,operation) VALUES (?,'CREATE')",request.executionId());
                ticketCommand(c,stored,null);
            }
            return stored;
        });
    }

    public void associateTicket(UUID id,String ticket) throws Exception {
        if(ticket==null || ticket.isBlank() || ticket.length()>100)throw new IllegalArgumentException("Invalid ticket number");
        transaction(c -> {
            ObjectNode e=row(c,id,true);if(e==null)throw new NoSuchElementException("Automation not found");
            String current=e.path("itsm_ticket_number").asText("");
            if(!current.isBlank() && !current.equals(ticket))throw new Conflict("Ticket association is immutable");
            update(c,"UPDATE event_management.automation_execution SET itsm_ticket_number=?,version=version+1,updated_at=now() WHERE execution_id=?",ticket,id);
            ObjectNode associated=row(c,id,false);
            enqueueUpdate(c,associated);
            if(associated.path("completed_at").isTextual()) {
                try(PreparedStatement s=c.prepareStatement("SELECT payload::text FROM event_management.automation_result WHERE execution_id=?")) {
                    s.setObject(1,id);try(ResultSet r=s.executeQuery()){if(r.next())ticketCommand(c,associated,mapper.readTree(r.getString(1)));}
                }
            } else ticketCommand(c,associated,null);
            return null;
        });
    }

    public record Dispatch(UUID executionId,String operation,JsonNode execution) {}
    public Dispatch claimDispatch() throws Exception {
        return transaction(c -> {
            UUID id;String operation;
            try(PreparedStatement s=c.prepareStatement("""
                SELECT d.execution_id,d.operation FROM event_management.automation_provider_dispatch d
                JOIN event_management.automation_execution e ON e.execution_id=d.execution_id
                WHERE d.status='PENDING' AND e.completed_at IS NULL
                ORDER BY e.requested_at FOR UPDATE OF d SKIP LOCKED LIMIT 1
                """);ResultSet r=s.executeQuery()) {
                if(!r.next())return null;id=r.getObject(1,UUID.class);operation=r.getString(2);
            }
            update(c,"UPDATE event_management.automation_provider_dispatch SET status='IN_FLIGHT',claimed_at=now() WHERE execution_id=? AND operation=?",id,operation);
            if(operation.equals("CREATE"))update(c,"UPDATE event_management.automation_execution SET state='SUBMITTING',submitted_at=now(),version=version+1 WHERE execution_id=? AND state='RECEIVED'",id);
            return new Dispatch(id,operation,row(c,id,false));
        });
    }

    public void recordDispatch(Dispatch dispatch,byte[] response,int httpStatus,boolean accepted) throws Exception {
        transaction(c -> {
            ObjectNode e=row(c,dispatch.executionId(),true);
            evidence(c,dispatch.executionId(),"INBOUND",dispatch.operation()+"_RESPONSE","",httpStatus,response,false,e.path("completed_at").isTextual());
            update(c,"UPDATE event_management.automation_provider_dispatch SET status=?,completed_at=now() WHERE execution_id=? AND operation=?",
                    accepted?"SENT":"REVIEW",dispatch.executionId(),dispatch.operation());
            if(dispatch.operation().equals("CREATE") && !e.path("completed_at").isTextual()) {
                if(accepted) update(c,"UPDATE event_management.automation_execution SET state='SUBMITTED',version=version+1,updated_at=now() WHERE execution_id=? AND state='SUBMITTING'",dispatch.executionId());
                else if(!e.path("accepted_at").isTextual())complete(c,e,"UNKNOWN","SUBMISSION_FAILED",null);
            }
            return null;
        });
    }

    public void recordRequest(Dispatch dispatch,byte[] xml) throws Exception {
        transaction(c -> { evidence(c,dispatch.executionId(),"OUTBOUND",dispatch.operation(),"",null,xml,false,false);return null;});
    }

    public void callback(NextAdapter.Message message) throws Exception {
        transaction(c -> {
            Set<UUID> matches=new HashSet<>();
            String[][] identities={{"requester_id",message.requesterId()},{"provider_execution_id",message.providerId()},{"transaction_number",message.transactionNumber()}};
            for(String[] identity:identities) {
                if(identity[1].isBlank())continue;
                try(PreparedStatement s=c.prepareStatement("SELECT execution_id FROM event_management.automation_execution WHERE "+identity[0]+"=?")) {
                    s.setString(1,identity[1]);try(ResultSet r=s.executeQuery()){while(r.next())matches.add(r.getObject(1,UUID.class));}
                }
            }
            if(matches.size()>1)throw new Conflict("Callback identities identify different executions");
            if(matches.isEmpty())throw new NoSuchElementException("Callback execution not found");
            UUID id=matches.iterator().next();ObjectNode e=row(c,id,true);
            if(!message.requesterId().isBlank() && !message.requesterId().equals(e.path("requester_id").asText()))throw new Conflict("Requester identity mismatch");
            String provider=e.path("provider_execution_id").asText("");
            if(!provider.isBlank() && !message.providerId().isBlank() && !provider.equals(message.providerId()))throw new Conflict("Provider identity mismatch");
            boolean duplicate=false;
            try(PreparedStatement s=c.prepareStatement("SELECT 1 FROM event_management.automation_provider_message WHERE execution_id=? AND direction='CALLBACK' AND payload_sha256=?")) {
                bind(s,id,hash(message.raw()));try(ResultSet r=s.executeQuery()){duplicate=r.next();}
            }
            boolean late=e.path("completed_at").isTextual();
            evidence(c,id,"CALLBACK",message.transactionName(),message.transactionNumber(),null,message.raw(),duplicate,late);
            if(duplicate || late)return null;
            boolean expired;
            try(PreparedStatement s=c.prepareStatement("SELECT deadline_at <= now() FROM event_management.automation_execution WHERE execution_id=?")) {
                s.setObject(1,id);try(ResultSet r=s.executeQuery()){r.next();expired=r.getBoolean(1);}
            }
            if(expired) {
                complete(c,e,"TIMEOUT","TIMED_OUT",null);
                update(c,"UPDATE event_management.automation_provider_message SET late_result=true WHERE execution_id=? AND direction='CALLBACK' AND payload_sha256=?",id,hash(message.raw()));
                return null;
            }
            // A changed representation of an ACK must not restart the deadline.
            if(provider.isBlank() && !message.providerId().isBlank())update(c,"UPDATE event_management.automation_execution SET provider_execution_id=? WHERE execution_id=?",message.providerId(),id);
            if(message.acknowledgement()) {
                if(message.providerId().isBlank() && provider.isBlank())throw new IllegalArgumentException("Acknowledgement requires ProviderID");
                update(c,"""
                    UPDATE event_management.automation_execution SET state='IN_PROGRESS',
                    accepted_at=COALESCE(accepted_at,now()),deadline_at=COALESCE(deadline_at,now()+result_timeout_seconds*interval '1 second'),
                    provider_ewt=?,version=version+1,updated_at=now() WHERE execution_id=?
                    """,message.estimatedWait(),id);
                enqueueUpdate(c,row(c,id,false));
            } else {
                // Enforce wall-clock expiry even when the watcher has not yet run.
                complete(c,row(c,id,false),message.outcome(),"COMPLETED",message);
            }
            return null;
        });
    }

    private void enqueueUpdate(Connection c,ObjectNode e) throws Exception {
        if(!e.path("provider_execution_id").asText("").isBlank() && !e.path("itsm_ticket_number").asText("").isBlank()
                && !e.path("completed_at").isTextual())
            update(c,"INSERT INTO event_management.automation_provider_dispatch(execution_id,operation) VALUES (?,'TKTUPDATE') ON CONFLICT DO NOTHING",UUID.fromString(e.path("execution_id").asText()));
    }

    public int expire(int submissionTimeoutSeconds) throws Exception {
        return transaction(c -> {
            update(c,"UPDATE event_management.automation_provider_dispatch SET status='REVIEW' WHERE status='IN_FLIGHT' AND claimed_at+?*interval '1 second'<=now()",submissionTimeoutSeconds);
            List<UUID> ids=new ArrayList<>();
            try(PreparedStatement s=c.prepareStatement("""
                SELECT execution_id FROM event_management.automation_execution
                WHERE completed_at IS NULL AND (deadline_at<=now() OR
                  (accepted_at IS NULL AND submitted_at+?*interval '1 second'<=now()))
                ORDER BY requested_at FOR UPDATE SKIP LOCKED LIMIT 100
                """)) {
                s.setInt(1,submissionTimeoutSeconds);try(ResultSet r=s.executeQuery()){while(r.next())ids.add(r.getObject(1,UUID.class));}
            }
            for(UUID id:ids)complete(c,row(c,id,false),"TIMEOUT","TIMED_OUT",null);
            return ids.size();
        });
    }

    private void complete(Connection c,ObjectNode e,String outcome,String state,NextAdapter.Message providerMessage) throws Exception {
        UUID id=UUID.fromString(e.path("execution_id").asText());UUID resultId=UUID.randomUUID();
        ObjectNode result=mapper.createObjectNode();
        result.put("schemaVersion","1.1");result.put("resultId",resultId.toString());result.put("messageId",resultId.toString());
        result.put("messageType","AUTOMATION_COMPLETED");result.put("executionId",id.toString());result.put("commandId",e.path("command_id").asText());
        result.put("eventId",e.path("event_id").asText());result.put("eventKey",e.path("event_key").asText());result.put("tenant",e.path("customer_code").asText());
        result.put("integrationType","CACF");result.put("operation","AUTOMATION_REQUESTED");result.put("provider","NEXT");
        result.put("externalId",e.path("provider_execution_id").asText(""));result.put("providerExecutionId",e.path("provider_execution_id").asText(""));
        result.put("status",outcome.equals("TIMEOUT")?"FAILED":"SUCCESS");result.put("state",state);result.put("outcome",outcome);
        result.put("requiresReview",outcome.equals("UNKNOWN"));result.put("completedAt",Instant.now().toString());
        result.set("ticket",e.path("request").path("ticket").deepCopy());
        if(providerMessage!=null){result.put("providerOutcome",providerMessage.status());result.put("providerSubstatus",providerMessage.substatus());}
        if(!outcome.equals("UNKNOWN")) {
            ObjectNode action=result.putObject("ticketAction");
            action.put("action",outcome.equals("REMEDIATED")?"ADD_WORK_NOTE":"REASSIGN");
            action.put("assignmentGroup",e.path("request").path("ticket").path("originalAssignmentGroup").asText());
            action.put("addWorkNote",true);action.put("source","CACF_NEXT");
        }
        update(c,"UPDATE event_management.automation_execution SET state=?,outcome=?,completed_at=now(),version=version+1,updated_at=now() WHERE execution_id=?",state,outcome,id);
        update(c,"INSERT INTO event_management.automation_result(result_id,execution_id,outcome,requires_review,payload) VALUES (?,?,?,?,?::jsonb)",resultId,id,outcome,outcome.equals("UNKNOWN"),result.toString());
        outbox(c,id,"AUTOMATION_COMPLETED",result);
        ticketCommand(c,e,result);
    }

    private void ticketCommand(Connection c,ObjectNode e,JsonNode result) throws Exception {
        JsonNode ticketData=e.path("request").path("ticket");
        String provider=ticketData.path("provider").asText("SERVICENOW").toUpperCase(java.util.Locale.ROOT);
        String ticket=e.path("itsm_ticket_number").asText(ticketData.path("number").asText(""));
        String glpiId=ticketData.path("id").asText(ticketData.path("sysId").asText(""));
        if(("GLPI".equals(provider)?glpiId.isBlank():ticket.isBlank()) || (result!=null && result.path("outcome").asText().equals("UNKNOWN")))return;
        UUID id=UUID.fromString(e.path("execution_id").asText());
        String phase=result==null?"HOLDING":"RESULT";
        ObjectNode command=mapper.createObjectNode();
        command.put("commandId",id+"-"+phase.toLowerCase(java.util.Locale.ROOT));
        command.put("eventId",e.path("event_id").asText());command.put("eventKey",e.path("event_key").asText());
        command.put("tenant",e.path("customer_code").asText());command.put("integrationType",provider);command.put("operation","APPLY_AUTOMATION_RESULT");
        ObjectNode payload=command.putObject("payload");
        if("GLPI".equals(provider))payload.put("ticketId",Long.parseLong(glpiId)); else payload.put("ticketNumber",ticket);
        payload.put("action",result==null?"REASSIGN":result.path("ticketAction").path("action").asText());
        payload.put("assignmentGroup",e.path("request").path("ticket").path(result==null?"holdingAssignmentGroup":"originalAssignmentGroup").asText());
        String workNote=result==null?"CACF/NEXT: ticket assigned to automation holding group. executionId="+id
            :"CACF/NEXT automation result: outcome="+result.path("outcome").asText()+"; executionId="+id+"; providerExecutionId="+e.path("provider_execution_id").asText("")
                +(result.path("outcome").asText().equals("REMEDIATED")?". Resolution requires the configured ticket policy.":". Ticket assigned to human support group by CACF/NEXT automation workflow.");
        payload.put("content",workNote); payload.put("workNote",workNote);
        outbox(c,id,provider+"_"+phase,command);
    }

    private void outbox(Connection c,UUID id,String type,JsonNode payload) throws Exception {
        update(c,"INSERT INTO event_management.automation_outbox(outbox_id,execution_id,event_type,payload) VALUES (?,?,?,?::jsonb) ON CONFLICT DO NOTHING",UUID.randomUUID(),id,type,payload.toString());
    }
    @FunctionalInterface public interface Publisher {void publish(String type,JsonNode payload) throws Exception;}
    public boolean publishOne(Publisher publisher) throws Exception {
        return transaction(c -> {
            UUID id;JsonNode payload;String type;
            // Preserve intent order across replicas (holding assignment before terminal action).
            try(PreparedStatement s=c.prepareStatement("SELECT outbox_id,payload::text,event_type FROM event_management.automation_outbox WHERE sequence_id=(SELECT min(sequence_id) FROM event_management.automation_outbox WHERE NOT published) FOR UPDATE SKIP LOCKED");ResultSet r=s.executeQuery()) {
                if(!r.next())return false;id=r.getObject(1,UUID.class);payload=mapper.readTree(r.getString(2));type=r.getString(3);
            }
            publisher.publish(type,payload);
            update(c,"UPDATE event_management.automation_outbox SET published=true,published_at=now() WHERE outbox_id=?",id);return true;
        });
    }
    private void evidence(Connection c,UUID id,String direction,String type,String transaction,Integer status,byte[] raw,boolean duplicate,boolean late) throws Exception {
        update(c,"""
            INSERT INTO event_management.automation_provider_message
            (message_id,execution_id,direction,message_type,transaction_number,http_status,payload,raw_payload,payload_sha256,duplicate,late_result)
            VALUES (?,?,?,?,?,?,?,?,?,?,?)
            """,UUID.randomUUID(),id,direction,type,transaction,status,new String(raw,StandardCharsets.UTF_8),raw,hash(raw),duplicate,late);
    }
    private static String hash(byte[] bytes) throws Exception {return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));}

    public Map<String,Long> metrics() throws Exception {
        return transaction(c -> {
            Map<String,Long> values=new LinkedHashMap<>();
            String[] names={"executions","results","callbacks_duplicate","callbacks_late","outbox_pending","provider_review"};
            String[] queries={"SELECT count(*) FROM event_management.automation_execution","SELECT count(*) FROM event_management.automation_result",
                "SELECT count(*) FROM event_management.automation_provider_message WHERE duplicate","SELECT count(*) FROM event_management.automation_provider_message WHERE late_result",
                "SELECT count(*) FROM event_management.automation_outbox WHERE NOT published","SELECT count(*) FROM event_management.automation_provider_dispatch WHERE status='REVIEW'"};
            for(int i=0;i<names.length;i++)try(Statement s=c.createStatement();ResultSet r=s.executeQuery(queries[i])){r.next();values.put(names[i],r.getLong(1));}
            return values;
        });
    }
}
