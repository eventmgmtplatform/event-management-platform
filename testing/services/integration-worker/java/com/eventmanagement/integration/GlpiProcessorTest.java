package com.eventmanagement.integration;

import com.fasterxml.jackson.databind.*;
import org.apache.camel.*;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.support.DefaultExchange;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class GlpiProcessorTest {
    ObjectMapper mapper=new ObjectMapper();
    Exchange command(String operation,String json)throws Exception {
        var e=new DefaultExchange(new DefaultCamelContext());
        e.setProperty("commandId","glpi-command-1");e.setProperty("eventId","event-1");e.setProperty("eventKey","key-1");
        e.setProperty("tenant","test");e.setProperty("integrationType","GLPI");e.setProperty("operation",operation);
        e.setProperty("integrationPayload",mapper.readTree(json));e.setProperty("integrationIdempotencyDecision","EXECUTE");
        var p=new GlpiCommandProcessor();p.mapper=mapper;p.process(e);return e;
    }
    class Client extends GlpiHttpClient {
        List<String> calls=new ArrayList<>(); boolean timeout=false; boolean closed=false; int matches=1;
        Client(){super(mapper,"http://localhost","app","user",1000);}
        public String openSession(){return "test-session";}
        public void closeSession(String s){}
        public JsonNode call(String method,String path,JsonNode body,String session)throws Exception {
            calls.add(method+" "+path);
            if(method.equals("POST") && timeout)throw new java.io.IOException("uncertain");
            if(path.startsWith("/search"))return mapper.readTree("{\"totalcount\":"+matches+",\"data\":[{\"2\":42}]}");
            if(method.equals("POST"))return mapper.readTree("{\"id\":42}");
            if(method.equals("PUT")){closed=true;return mapper.readTree("[{\"42\":true}]");}
            return mapper.readTree("{\"id\":42,\"status\":"+(closed?6:5)+"}");
        }
    }
    GlpiExecutionProcessor execution(Client c) {
        var p=new GlpiExecutionProcessor();p.mapper=mapper;p.client=c;
        p.ledger=new IntegrationCommandLedger(){
            public Claim claim(JsonNode n){throw new UnsupportedOperationException();}
            public void checkpointProvider(String id,String owner,String payload){}
            public void complete(String id,String owner,String payload){}
        };return p;
    }
    @Test void mapsNativePropertiesAndSeverity()throws Exception {
        var e=command("CREATE_TICKET","{\"summary\":\"CPU high\",\"severity\":5,\"entities_id\":2}");
        var input=e.getProperty("glpiRequest",JsonNode.class).path("input");
        assertEquals(5,input.path("urgency").asInt());assertEquals(2,input.path("entities_id").asInt());
        assertTrue(input.path("name").asText().contains("[OEM:glpi-command-1]"));assertFalse(input.has("sys_id"));
        assertThrows(IllegalArgumentException.class,()->command("CREATE_TICKET","{\"summary\":\"x\",\"priority\":7}"));
    }
    @Test void createsNativeTicketAndProviderResult()throws Exception {
        var e=command("CREATE_TICKET","{\"summary\":\"CPU high\"}");var c=new Client();execution(c).process(e);
        var result=mapper.readTree(e.getMessage().getBody(String.class));
        assertEquals("GLPI",result.path("integrationType").asText());assertEquals("42",result.path("externalSystemId").asText());
        assertEquals(List.of("POST /Ticket","GET /Ticket/42"),c.calls);
    }
    @Test void uncertainPostStopsWithoutTerminalFailure()throws Exception {
        var e=command("CREATE_TICKET","{\"summary\":\"CPU high\"}");var c=new Client();c.timeout=true;execution(c).process(e);
        assertTrue(e.isRouteStop());assertEquals(List.of("POST /Ticket"),c.calls);
        assertEquals(true,e.getProperty("glpiReconciliationRequired"));
    }
    @Test void reconciliationNeverCreatesWhenNotFound()throws Exception {
        var e=command("CREATE_TICKET","{\"summary\":\"CPU high\"}");e.setProperty("integrationIdempotencyDecision","RECONCILE");
        var c=new Client();c.matches=0;execution(c).process(e);assertTrue(e.isRouteStop());
        assertTrue(c.calls.stream().allMatch(s->s.startsWith("GET ")));
    }
    @Test void checkpointRecoveryUsesOnlyAuthoritativeGet()throws Exception {
        var e=command("CREATE_TICKET","{\"summary\":\"CPU high\"}");e.setProperty("integrationIdempotencyDecision","RECONCILE");
        e.setProperty("integrationProviderCheckpoint","{\"provider\":\"GLPI\",\"operation\":\"CREATE_TICKET\",\"ticketId\":42}");
        var c=new Client();execution(c).process(e);assertEquals(List.of("GET /Ticket/42"),c.calls);assertFalse(e.isRouteStop());
    }
    @Test void solutionUsesNativeITILSolution()throws Exception {
        var e=command("RESOLVE_TICKET","{\"ticketId\":42,\"content\":\"Monitoring recovered\"}");var c=new Client();execution(c).process(e);
        assertEquals(List.of("POST /ITILSolution","GET /Ticket/42"),c.calls);
        assertEquals("RESOLVED_CONFIRMED",mapper.readTree(e.getMessage().getBody(String.class)).path("ticketLifecycleState").asText());
    }
    @Test void closesSolvedTicketWithNativeStatusSix()throws Exception {
        var e=command("CLOSE_TICKET","{\"ticketId\":42}");var c=new Client();execution(c).process(e);
        assertEquals(List.of("GET /Ticket/42","PUT /Ticket/42","GET /Ticket/42"),c.calls);
        assertEquals("CLOSED_CONFIRMED",mapper.readTree(e.getMessage().getBody(String.class)).path("ticketLifecycleState").asText());
    }
    @Test void automationResultCreatesPrivateNativeFollowup()throws Exception {
        var e=command("APPLY_AUTOMATION_RESULT","{\"ticketId\":42,\"content\":\"Job completed\"}");
        var input=e.getProperty("glpiRequest",JsonNode.class).path("input");
        assertEquals(42,input.path("items_id").asInt());assertEquals(1,input.path("is_private").asInt());
    }
    @Test void alreadyClosedTicketIsIdempotent()throws Exception {
        var e=command("CLOSE_TICKET","{\"ticketId\":42}");var c=new Client();c.closed=true;execution(c).process(e);
        assertEquals(List.of("GET /Ticket/42","GET /Ticket/42"),c.calls);
        assertEquals("CLOSED_CONFIRMED",mapper.readTree(e.getMessage().getBody(String.class)).path("ticketLifecycleState").asText());
    }
}
