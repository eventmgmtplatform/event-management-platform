package com.eventmanagement.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.support.DefaultExchange;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ServiceNowAutomationActionProcessorTest {
    @Test void delegatesHumanReassignmentAndWorkNoteToExistingClient() throws Exception {
        ObjectMapper mapper=new ObjectMapper();String[] sent={null};
        ServiceNowLookupClient lookup=new ServiceNowLookupClient(){
            public LookupResult findByEventId(String id){throw new AssertionError("Unexpected event lookup");}
            public LookupResult findByTicketNumber(String number){return new LookupResult(Status.FOUND,mapper.createObjectNode().put("sys_id","id-1").put("number",number));}
        };
        ServiceNowHttpInvoker client=new ServiceNowHttpInvoker(){
            public HttpResult invoke(String body){throw new AssertionError("Must not create another ticket");}
            public HttpResult updateTicket(String id,String body){assertEquals("id-1",id);sent[0]=body;return new HttpResult(200,"{}");}
        };
        try(var camel=new DefaultCamelContext()) {
            var exchange=new DefaultExchange(camel);
            exchange.setProperty("integrationPayload",mapper.createObjectNode().put("ticketNumber","INC1").put("action","REASSIGN").put("assignmentGroup","HUMAN").put("workNote","CACF/NEXT requires human intervention"));
            new ServiceNowAutomationActionProcessor(lookup,client,mapper).process(exchange);
            assertEquals("HUMAN",mapper.readTree(sent[0]).path("assignment_group").asText());
            assertTrue(mapper.readTree(sent[0]).path("work_notes").asText().startsWith("CACF/NEXT"));
            assertFalse(mapper.readTree(sent[0]).has("state"));
            sent[0]=null;exchange.setProperty("integrationIdempotencyDecision","RECONCILE");
            assertThrows(IllegalStateException.class,()->new ServiceNowAutomationActionProcessor(lookup,client,mapper).process(exchange));
            assertNull(sent[0]);
        }
    }
}
