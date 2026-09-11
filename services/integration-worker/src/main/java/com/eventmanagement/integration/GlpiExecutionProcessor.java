package com.eventmanagement.integration;

import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.*;
import org.apache.camel.*;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.UUID;

@Named("glpiExecutionProcessor")
@ApplicationScoped
public class GlpiExecutionProcessor implements Processor {
    @Inject GlpiHttpClient client;
    @Inject ObjectMapper mapper;
    @Inject IntegrationCommandLedger ledger;
    @Override public void process(Exchange exchange) throws Exception {
        String session=client.openSession();
        try {
            String operation=exchange.getProperty("operation",String.class);
            boolean reconcile="RECONCILE".equals(exchange.getProperty("integrationIdempotencyDecision",String.class));
            long id=exchange.getProperty("glpiTicketId",0L,Long.class);
            String checkpoint=exchange.getProperty("integrationProviderCheckpoint",String.class);
            if(reconcile && checkpoint!=null && !checkpoint.isBlank()) {
                JsonNode saved=mapper.readTree(checkpoint);
                if(!"GLPI".equals(saved.path("provider").asText()) || !operation.equals(saved.path("operation").asText()))
                    throw new IllegalStateException("GLPI checkpoint identity mismatch");
                id=saved.path("ticketId").asLong(0);
            } else if(reconcile && "CREATE_TICKET".equals(operation)) {
                String marker="[OEM:"+exchange.getProperty("commandId",String.class)+"]";
                JsonNode matches=client.call("GET","/search/Ticket?criteria[0][field]=1&criteria[0][searchtype]=contains&criteria[0][value]="+URLEncoder.encode(marker,StandardCharsets.UTF_8)+"&forcedisplay[0]=2",null,session);
                if(matches.path("totalcount").asInt()!=1) { exchange.setRouteStop(true);return; }
                id=matches.path("data").path(0).path("2").asLong(0);
            } else if(reconcile) {
                // A prior mutation may have committed; recovery is read-only.
                if(!java.util.Set.of("RESOLVE_TICKET","CLOSE_TICKET").contains(operation)) { exchange.setRouteStop(true);return; }
            } else {
                JsonNode request=exchange.getProperty("glpiRequest",JsonNode.class);
                // Once a mutation starts, ambiguous failures leave the durable claim unresolved.
                exchange.setProperty("glpiMutationStarted",true);
                JsonNode created;
                try {
                    if("CLOSE_TICKET".equals(operation)) {
                        JsonNode current=client.call("GET","/Ticket/"+id,null,session);
                        int currentStatus=current.path("status").asInt(0);
                        if(currentStatus!=5 && currentStatus!=6) { exchange.setRouteStop(true);return; }
                        if(currentStatus==6) created=mapper.createObjectNode().put("id",id);
                        else {
                            JsonNode updated=client.call("PUT","/Ticket/"+id,request,session);
                            ObjectNode closeResponse=mapper.createObjectNode().put("id",id);
                            closeResponse.set("raw",updated); created=closeResponse;
                        }
                    } else {
                        String path="CREATE_TICKET".equals(operation)?"/Ticket":("RESOLVE_TICKET".equals(operation)?"/ITILSolution":"/ITILFollowup");
                        created=client.call("POST",path,request,session);
                    }
                }
                catch(org.apache.camel.http.base.HttpOperationFailedException http) {
                    if(java.util.Set.of(400,401,403,404,405,422).contains(http.getStatusCode()))
                        exchange.setProperty("glpiMutationStarted",false);
                    throw http;
                }
                if(created.path("id").asLong(0)<1) throw new IllegalStateException("GLPI mutation ID missing");
                if("CREATE_TICKET".equals(operation))id=created.path("id").asLong();
                ObjectNode saved=mapper.createObjectNode().put("provider","GLPI").put("operation",operation)
                    .put("ticketId",id).put("mutationId",created.path("id").asLong());
                ledger.checkpointProvider(exchange.getProperty("commandId",String.class),
                    exchange.getProperty(IntegrationCommandClaimProcessor.CLAIM_OWNER_PROPERTY,String.class),saved.toString());
            }
            if(id<1) { exchange.setRouteStop(true);return; }
            JsonNode ticket=client.call("GET","/Ticket/"+id,null,session);
            if(ticket.path("id").asLong()!=id)throw new IllegalStateException("GLPI ticket identity mismatch");
            if("RESOLVE_TICKET".equals(operation) && ticket.path("status").asInt()!=5 && ticket.path("status").asInt()!=6) {
                exchange.setRouteStop(true);return;
            }
            if("CLOSE_TICKET".equals(operation) && ticket.path("status").asInt()!=6) { exchange.setRouteStop(true);return; }
            ObjectNode result=mapper.createObjectNode();
            result.put("schemaVersion","1.1"); result.put("resultId",UUID.randomUUID().toString());
            for(String field:java.util.List.of("commandId","eventId","eventKey","tenant","integrationType","operation"))result.put(field,exchange.getProperty(field,String.class));
            result.put("correlationId",exchange.getProperty("commandId",String.class));
            result.put("ticketNumber",Long.toString(id));
            result.put("status","SUCCESS");result.put("attempt",1);result.put("externalId",Long.toString(id));
            result.put("externalReference",Long.toString(id));result.put("externalSystemId",Long.toString(id));
            result.put("completedAt",Instant.now().toString());result.put("httpStatus",200);
            result.putObject("response").put("ticketId",id).set("raw",ticket);
            if("RESOLVE_TICKET".equals(operation))result.put("ticketLifecycleState","RESOLVED_CONFIRMED");
            if("CLOSE_TICKET".equals(operation))result.put("ticketLifecycleState","CLOSED_CONFIRMED");
            exchange.getMessage().setBody(mapper.writeValueAsString(result));
        } catch(Exception exception) {
            if(Boolean.TRUE.equals(exchange.getProperty("glpiMutationStarted")) || "RECONCILE".equals(exchange.getProperty("integrationIdempotencyDecision",String.class))) {
                // No false terminal FAILED, and no second POST. Recovery is GET only.
                exchange.setProperty("glpiReconciliationRequired",true);exchange.setRouteStop(true);
            } else throw exception;
        } finally { client.closeSession(session); }
    }
}
