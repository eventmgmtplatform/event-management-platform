package com.eventmanagement.integration;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
/** Explicit per-route resolution contract. Ambiguous PATCH is reconciled by read, never repeated. */
@Named("serviceNowResolveProcessor") @ApplicationScoped
public class ServiceNowResolveProcessor implements Processor {
    private final ServiceNowLookupClient lookup;
    private final ServiceNowHttpInvoker client;
    private final ObjectMapper mapper;
    @Inject public ServiceNowResolveProcessor(ServiceNowLookupClient lookup,ServiceNowHttpInvoker client,ObjectMapper mapper){this.lookup=lookup;this.client=client;this.mapper=mapper;}
    public void process(Exchange e)throws Exception {
        JsonNode p=e.getProperty("integrationPayload",JsonNode.class);
        String number=required(p,"ticketNumber"),id=required(p,"sysId"),state=required(p,"expectedState"),code=required(p,"closeCode"),notes=required(p,"closeNotes");
        var before=read(number,id);
        if(!matches(before,state,code)) {
            if("RECONCILE".equals(e.getProperty("integrationIdempotencyDecision",String.class)))throw new IllegalStateException("RESOLUTION_REQUIRES_REVIEW");
            var body=mapper.createObjectNode().put("state",state).put("close_code",code).put("close_notes",notes);
            // Even on an uncertain HTTP outcome, only authoritative read can confirm success.
            try {client.updateTicket(id,body.toString());}catch(Exception uncertain){ /* read below; no retry */ }
        }
        var after=read(number,id);
        if(!matches(after,state,code))throw new IllegalStateException("RESOLUTION_NOT_CONFIRMED");
        e.getMessage().setBody(mapper.createObjectNode().set("result",after).toString());
        e.getMessage().setHeader(Exchange.HTTP_RESPONSE_CODE,200);
        e.setProperty("ticketLifecycleState","RESOLVED_CONFIRMED");
    }
    private JsonNode read(String number,String id)throws Exception {
        var r=lookup.findByTicketNumber(number);
        if(r.status()!=ServiceNowLookupClient.Status.FOUND || !id.equals(r.ticket().path("sys_id").asText()) || !number.equals(r.ticket().path("number").asText()))throw new IllegalStateException("RESOLUTION_TICKET_IDENTITY_MISMATCH");
        return r.ticket();
    }
    private boolean matches(JsonNode r,String state,String code){return state.equals(r.path("state").asText()) && code.equals(r.path("close_code").asText()) && !r.path("close_notes").asText().isBlank();}
    private String required(JsonNode p,String key){var n=p.path(key);if(!n.isTextual() || n.asText().isBlank() || n.asText().length()>1024)throw new IllegalArgumentException("INVALID_RESOLUTION_"+key);return n.asText();}
}
