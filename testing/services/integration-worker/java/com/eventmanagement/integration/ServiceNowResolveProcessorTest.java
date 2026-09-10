package com.eventmanagement.integration;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.support.DefaultExchange;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class ServiceNowResolveProcessorTest {
    final ObjectMapper mapper=new ObjectMapper();
    @Test void httpSuccessWithoutStateConfirmationFails()throws Exception {verify(false,false,false);}
    @Test void confirmedResolutionDoesNotRepeatMutation()throws Exception {verify(true,true,false);}
    @Test void ambiguousClaimOnlyReads()throws Exception {verify(false,true,true);}
    private void verify(boolean resolved,boolean reconcile,boolean expectedFailure)throws Exception {
        var ticket=mapper.createObjectNode().put("number","INCTEST").put("sys_id","synthetic-id").put("state",resolved?"resolved-test":"open-test").put("close_code","monitor-recovered").put("close_notes","Recovery");
        ServiceNowLookupClient lookup=new ServiceNowLookupClient(){public LookupResult findByEventId(String id){throw new AssertionError();}public LookupResult findByTicketNumber(String number){return new LookupResult(Status.FOUND,ticket);}};
        int[] calls={0};ServiceNowHttpInvoker http=new ServiceNowHttpInvoker(){public HttpResult invoke(String b){throw new AssertionError();}public HttpResult updateTicket(String id,String b){calls[0]++;return new HttpResult(200,"{}");}};
        var p=new ServiceNowResolveProcessor(lookup,http,mapper);var e=new DefaultExchange(new DefaultCamelContext());
        e.setProperty("integrationPayload",mapper.createObjectNode().put("ticketNumber","INCTEST").put("sysId","synthetic-id").put("expectedState","resolved-test").put("closeCode","monitor-recovered").put("closeNotes","Recovery"));
        if(reconcile)e.setProperty("integrationIdempotencyDecision","RECONCILE");
        if(!resolved)assertThrows(IllegalStateException.class,()->p.process(e));else {p.process(e);assertEquals("RESOLVED_CONFIRMED",e.getProperty("ticketLifecycleState"));}
        assertEquals(reconcile?0:1,calls[0]);
    }
}
