package com.eventmanagement.integration;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.support.DefaultExchange;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class GnmResultEnvelopeProcessorTest {
    @Test void resultHasStableIdentityAndTenantBeforeLedger()throws Exception {
        var mapper=new ObjectMapper();var p=new GnmResultEnvelopeProcessor();p.mapper=mapper;
        var e=new DefaultExchange(new DefaultCamelContext());e.setProperty("integrationType","GNM");
        var command=mapper.createObjectNode().put("commandId","test-command").put("eventId","cycle").put("eventKey","correlation:cycle").put("tenant","synthetic").put("operation","SEND_NOTIFICATION").put("createdAt","2026-09-10T00:00:00Z");
        e.setProperty("originalIntegrationCommand",command);var body=mapper.createObjectNode().put("status","SUCCESS");body.putObject("providerNotificationIdentity").put("incidentId","provider-id").put("lifecycleState","OPEN_CONFIRMED");
        e.getMessage().setBody(body.toString());p.process(e);var r=mapper.readTree(e.getMessage().getBody(String.class));
        assertEquals("synthetic",r.path("tenant").asText());assertEquals("correlation:cycle",r.path("eventKey").asText());assertFalse(r.path("resultId").asText().isBlank());
        p.process(e);assertEquals(r,mapper.readTree(e.getMessage().getBody(String.class)));
    }
}
