package com.eventmanagement.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;

/** ServiceNow foundation owns the ticket mutation requested by CACF results. */
@Named("serviceNowAutomationActionProcessor") @ApplicationScoped
public class ServiceNowAutomationActionProcessor implements Processor {
    private final ServiceNowLookupClient lookup;private final ServiceNowHttpInvoker client;private final ObjectMapper mapper;
    @Inject public ServiceNowAutomationActionProcessor(ServiceNowLookupClient lookup,ServiceNowHttpInvoker client,ObjectMapper mapper){this.lookup=lookup;this.client=client;this.mapper=mapper;}
    public void process(Exchange exchange) throws Exception {
        if ("RECONCILE".equals(exchange.getProperty("integrationIdempotencyDecision",String.class)))
            throw new IllegalStateException("Automation ticket action requires review after uncertain delivery; mutation suppressed");
        JsonNode payload=exchange.getProperty("integrationPayload",JsonNode.class);
        var ticket=lookup.findByTicketNumber(payload.path("ticketNumber").asText());
        if(ticket.status()!=ServiceNowLookupClient.Status.FOUND)throw new IllegalArgumentException("ServiceNow ticket was not found");
        String action=payload.path("action").asText();
        if(!action.equals("REASSIGN") && !action.equals("ADD_WORK_NOTE"))throw new IllegalArgumentException("Unsupported CACF ticket action");
        String note=payload.path("workNote").asText();
        if(note.isBlank())throw new IllegalArgumentException("Automation work note required");
        var body=mapper.createObjectNode().put("work_notes",note);
        if(action.equals("REASSIGN")) {
            String group=payload.path("assignmentGroup").asText();
            if(group.isBlank())throw new IllegalArgumentException("Human assignment group required");
            body.put("assignment_group",group);
        }
        var result=client.updateTicket(ticket.ticket().path("sys_id").asText(),body.toString());
        if(result.httpStatus()<200 || result.httpStatus()>=300)throw new IllegalStateException("ServiceNow ticket action failed");
        exchange.getMessage().setBody(mapper.createObjectNode().set("result",ticket.ticket()).toString());
        exchange.getMessage().setHeader(Exchange.HTTP_RESPONSE_CODE,200);
    }
}
