package com.eventmanagement.processor.adapters.postgres;

import com.eventmanagement.processor.adapters.kafka.WorkerCommandAdapter;
import com.eventmanagement.processor.domain.Event;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.sql.Connection;
import java.time.Instant;
import java.util.UUID;

/** Decision and next intent share the caller's transaction and tenant advisory lock. */
public final class PostgresLifecycleSession {
    private final Connection c;
    private final ObjectMapper mapper;
    private final String tenant;
    public PostgresLifecycleSession(Connection c,ObjectMapper mapper,String tenant){this.c=c;this.mapper=mapper;this.tenant=tenant;}

    public void register(Event source,JsonNode command)throws Exception {
        String cycle=command.path("eventId").asText();
        if(load(cycle)!=null)return;
        ObjectNode d=mapper.createObjectNode();
        d.put("tenant",tenant).put("cycleId",cycle).put("processingId",command.path("processingId").asText());
        d.put("sourceEventId",source.eventId()).put("sourceEventKey",source.eventKey());
        d.put("recovered",false).put("suppressed",false).put("state","TICKET_PENDING");
        d.set("source",mapper.readTree(source.originalJson()));d.set("initialCommand",command);
        d.putObject("commands").put("ticket",command.path("commandId").asText());d.putObject("results");
        try(var s=c.prepareStatement("INSERT INTO event_processor.lifecycle(tenant,cycle_id,processing_id,document) VALUES (?,?,?,?::jsonb)")) {
            s.setString(1,tenant);s.setString(2,cycle);s.setString(3,d.path("processingId").asText());s.setString(4,d.toString());s.executeUpdate();
        }
    }

    public void observe(Event event,JsonNode decision)throws Exception {
        for(var relationship:decision.path("correlation").path("decisions")) {
            if(!relationship.path("changed").asBoolean())continue;
            var group=relationship.path("group");var d=load(group.path("groupId").asText());
            if(d==null)continue;
            // Recovery is monotone for this correlation cycle. A new problem opens a different cycle.
            d.set("group",group.deepCopy());
            boolean expired=false;for(var member:group.path("members"))if("EXPIRED".equals(member.path("status").asText()))expired=true;
            if(group.path("resolved").asBoolean() && expired) {d.put("state","REVIEW").put("reason","EXPIRED_IS_NOT_MONITOR_RECOVERY");save(d);continue;}
            if(group.path("resolved").asBoolean()) {
                d.put("recovered",true);d.put("recoveryEventId",event.eventId());d.put("recoveredAt",event.receivedAt().toString());
            }
            d.put("suppressed",!java.util.Set.of("CONTINUE","GENERATE_COMMANDS").contains(decision.path("directive").asText()));
            advance(d);save(d);
        }
    }

    public void result(JsonNode result)throws Exception {
        String cycle=result.path("eventId").asText();var d=load(cycle);
        if(d==null)return; // Existing non-orchestrated integrations remain independent.
        String commandId=result.path("commandId").asText();String step=null;
        var entries=d.path("commands").fields();
        while(entries.hasNext()){var e=entries.next();if(e.getValue().asText().equals(commandId))step=e.getKey();}
        if(step==null && d.has("executionId") && commandId.equals(d.path("executionId").asText()+"-result"))step="note";
        if(step==null)return;
        String disposition="ACCEPTED";
        if(!result.path("eventKey").asText().equals("correlation:"+cycle) || !tenant.equals(result.path("tenant").asText())) disposition="IDENTITY_REVIEW";
        String provider=switch(step){case "open","close"->"GNM";case "automation"->"CACF";default->"SERVICENOW";};
        String operation=switch(step){case "ticket"->"CREATE_TICKET";case "open"->"SEND_NOTIFICATION";case "close"->"CLOSE_NOTIFICATION";case "automation"->"AUTOMATION_REQUESTED";case "note"->"APPLY_AUTOMATION_RESULT";default->"RESOLVE_TICKET";};
        if(!provider.equals(result.path("integrationType").asText()) || !operation.equals(result.path("operation").asText()))disposition="IDENTITY_REVIEW";
        String id=result.path("resultId").asText();if(id.isBlank())throw new IllegalArgumentException("LIFECYCLE_RESULT_ID_REQUIRED");
        try(var s=c.prepareStatement("SELECT payload::text FROM event_processor.lifecycle_result WHERE result_id=?")) {
            s.setString(1,id);try(var r=s.executeQuery()){if(r.next()){
                if(!mapper.readTree(r.getString(1)).equals(result)){if(!"COMPLETED".equals(d.path("state").asText()))d.put("state","REVIEW");d.put("reason","RESULT_ID_COLLISION");save(d);}return;
            }}
        }
        if(d.path("results").has(step))disposition="LATE";
        try(var s=c.prepareStatement("INSERT INTO event_processor.lifecycle_result(result_id,tenant,cycle_id,command_id,payload,disposition) VALUES (?,?,?,?,?::jsonb,?)")) {
            s.setString(1,id);s.setString(2,tenant);s.setString(3,cycle);s.setString(4,commandId);s.setString(5,result.toString());s.setString(6,disposition);s.executeUpdate();
        }
        if(disposition.equals("ACCEPTED")) {
            ((ObjectNode)d.path("results")).set(step,result);
            if(!confirmed(step,result,d))d.put("state","REVIEW").put("reason",step+"_UNCONFIRMED");
            else {
                if(step.equals("ticket"))d.put("ticketNumber",result.path("ticketNumber").asText()).put("ticketId",result.path("externalSystemId").asText());
                if(step.equals("open"))d.put("incidentId",result.path("providerNotificationIdentity").path("incidentId").asText());
                advance(d);
            }
        } else if(disposition.equals("IDENTITY_REVIEW"))d.put("state","REVIEW").put("reason",disposition);
        save(d);
    }

    public static boolean confirmed(String step,JsonNode r,JsonNode d) {
        if(!"SUCCESS".equals(r.path("status").asText()))return false;
        return switch(step) {
            case "ticket" -> !r.path("ticketNumber").asText().isBlank() && !r.path("externalSystemId").asText().isBlank();
            case "open","close" -> (step.equals("open")?"OPEN_CONFIRMED":"CLOSED_CONFIRMED").equals(r.path("providerNotificationIdentity").path("lifecycleState").asText())
                && !r.path("providerNotificationIdentity").path("incidentId").asText().isBlank()
                && (step.equals("open") || d.path("incidentId").asText().equals(r.path("providerNotificationIdentity").path("incidentId").asText()));
            case "automation" -> "COMPLETED".equals(r.path("state").asText()) && "REMEDIATED".equals(r.path("outcome").asText())
                && !r.path("requiresReview").asBoolean() && d.path("executionId").asText().equals(r.path("executionId").asText());
            case "resolve" -> "RESOLVED_CONFIRMED".equals(r.path("ticketLifecycleState").asText()) && d.path("ticketNumber").asText().equals(r.path("ticketNumber").asText())
                && d.path("ticketId").asText().equals(r.path("externalSystemId").asText());
            case "note" -> d.path("ticketNumber").asText().equals(r.path("ticketNumber").asText());
            default -> false;
        };
    }

    private void advance(ObjectNode d)throws Exception {
        if("REVIEW".equals(d.path("state").asText()) || "COMPLETED".equals(d.path("state").asText()))return;
        if(d.path("suppressed").asBoolean()){d.put("state","SUPPRESSED_PENDING");return;}
        if(!d.has("ticketNumber"))return;
        if(d.path("recovered").asBoolean()) {
            // Never issue a new automation after recovery. Already-issued work is not cancelled.
            if(d.path("commands").has("open") && !d.has("incidentId"))return;
            if(d.path("commands").has("automation") && (!d.path("results").has("automation") || !d.path("results").has("note"))) {d.put("state","RECOVERED_AUTOMATION_PENDING");return;}
            if(d.has("incidentId") && !d.path("results").has("close")){emit(d,"close","GNM","CLOSE_NOTIFICATION",gnm(d,true));return;}
            if(!d.path("results").has("resolve")) {
                var p=mapper.createObjectNode().put("ticketNumber",d.path("ticketNumber").asText()).put("sysId",d.path("ticketId").asText());
                p.put("expectedState",profile(d).path("resolvedState").asText()).put("closeCode",profile(d).path("closeCode").asText());
                p.put("closeNotes","Monitoring recovery confirmed for cycle "+d.path("cycleId").asText());
                emit(d,"resolve","SERVICENOW","RESOLVE_TICKET",p);return;
            }
            d.put("state","COMPLETED");return;
        }
        if(!d.path("commands").has("open")){emit(d,"open","GNM","SEND_NOTIFICATION",gnm(d,false));return;}
        if(d.has("incidentId") && !d.path("commands").has("automation")) {
            String execution=UUID.nameUUIDFromBytes((tenant+":"+d.path("cycleId").asText()).getBytes(java.nio.charset.StandardCharsets.UTF_8)).toString();
            d.put("executionId",execution);
            var p=mapper.createObjectNode().put("schemaVersion","1.0").put("executionId",execution).put("eventId",d.path("cycleId").asText());
            p.putObject("customer").put("code",tenant);
            p.putObject("ticket").put("number",d.path("ticketNumber").asText()).put("originalAssignmentGroup",profile(d).path("originalAssignmentGroup").asText()).put("holdingAssignmentGroup",profile(d).path("holdingAssignmentGroup").asText());
            // Group/cycle identity avoids RequesterID collisions across a reopened source condition.
            p.putObject("event").put("sourceSystem","Zabbix").put("sourceSerial",d.path("cycleId").asText()).put("sourceEventId",d.path("sourceEventId").asText())
                .put("summary",initial(d).path("summary").asText()).put("resourceId",initial(d).path("resource").asText()).put("severity",initial(d).path("severity").asInt()).put("eventKey",d.path("sourceEventKey").asText());
            p.putObject("automation").put("provider","NEXT").put("resultTimeoutSeconds",600);
            emit(d,"automation","CACF","AUTOMATION_REQUESTED",p);return;
        }
        if(d.path("results").has("automation"))d.put("state","REMEDIATED_AWAITING_RECOVERY");
    }
    private JsonNode initial(JsonNode d){return d.path("initialCommand").path("payload");}
    private JsonNode profile(JsonNode d){return initial(d).path("lifecycle");}
    private ObjectNode gnm(JsonNode d,boolean close) {
        var p=mapper.createObjectNode().put("customerCode",tenant).put("customer",tenant).put("severity",close?0:initial(d).path("severity").asInt()).put("severityName",close?"Clear":"Fatal")
            .put("node",initial(d).path("resource").asText()).put("resourceId",initial(d).path("resource").asText()).put("summary",initial(d).path("summary").asText())
            .put("ticketNumber",d.path("ticketNumber").asText()).put("ticketGroup",profile(d).path("originalAssignmentGroup").asText());
        p.putObject("legacyCorrelation").put("serverSerial",d.path("sourceEventId").asText());
        var n=p.putObject("notification").put("type","HUNT").put("group",profile(d).path("notificationGroup").asText());
        if(close)n.put("incidentId",d.path("incidentId").asText());return p;
    }
    private void emit(ObjectNode d,String step,String provider,String operation,ObjectNode payload)throws Exception {
        if(d.path("commands").has(step))return;
        String cycle=d.path("cycleId").asText(),processing=d.path("processingId").asText();
        var event=new Event(cycle,"correlation:"+cycle,tenant,Event.Status.PROBLEM,5,Instant.now(),d.path("source").toString());
        var cmd=new WorkerCommandAdapter(mapper).encode(event,processing,cycle,"default",provider,operation,payload,Instant.now());
        ((ObjectNode)cmd.path("metadata")).put("sourceEventId",d.path("sourceEventId").asText()).put("sourceEventKey",d.path("sourceEventKey").asText()).put("correlationGroupId",cycle);
        String id=cmd.path("commandId").asText();
        try(var s=c.prepareStatement("INSERT INTO event_processor.integration_command(command_id,processing_id,tenant,envelope) VALUES (?,?,?,?::jsonb)")) {
            s.setString(1,id);s.setString(2,processing);s.setString(3,tenant);s.setString(4,cmd.toString());s.executeUpdate();
        }
        try(var s=c.prepareStatement("INSERT INTO event_processor.output_outbox(message_id,processing_id,topic,message_key,payload) VALUES (?,?,'integration.commands',?,?::jsonb)")) {
            s.setString(1,id);s.setString(2,processing);s.setString(3,event.eventKey());s.setString(4,cmd.toString());s.executeUpdate();
        }
        ((ObjectNode)d.path("commands")).put(step,id);d.put("state",step.toUpperCase(java.util.Locale.ROOT)+"_PENDING");
    }
    private ObjectNode load(String cycle)throws Exception {
        try(var s=c.prepareStatement("SELECT document::text FROM event_processor.lifecycle WHERE tenant=? AND cycle_id=? FOR UPDATE")) {
            s.setString(1,tenant);s.setString(2,cycle);try(var r=s.executeQuery()){return r.next()?(ObjectNode)mapper.readTree(r.getString(1)):null;}
        }
    }
    private void save(ObjectNode d)throws Exception {
        try(var s=c.prepareStatement("UPDATE event_processor.lifecycle SET document=?::jsonb,updated_at=now() WHERE tenant=? AND cycle_id=?")) {
            s.setString(1,d.toString());s.setString(2,tenant);s.setString(3,d.path("cycleId").asText());s.executeUpdate();
        }
    }
}
