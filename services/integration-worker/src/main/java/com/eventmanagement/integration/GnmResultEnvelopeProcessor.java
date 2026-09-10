package com.eventmanagement.integration;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
/** Complete GNM provider results before ledger persistence and publication. */
@Named("gnmResultEnvelopeProcessor") @ApplicationScoped
public class GnmResultEnvelopeProcessor implements Processor {
    @Inject ObjectMapper mapper;
    public void process(Exchange e)throws Exception {
        if(!"GNM".equals(e.getProperty("integrationType",String.class)))return;
        var r=(ObjectNode)mapper.readTree(e.getMessage().getBody(String.class));
        JsonNode command=e.getProperty("originalIntegrationCommand",JsonNode.class);
        for(String field:java.util.List.of("commandId","eventId","eventKey","tenant","operation")) {
            String value=command.path(field).asText();
            if(value.isBlank())throw new IllegalStateException("GNM_RESULT_IDENTITY_REQUIRED");
            if(r.hasNonNull(field) && !value.equals(r.path(field).asText()))throw new IllegalStateException("GNM_RESULT_IDENTITY_COLLISION");
            r.put(field,value);
        }
        if(!r.hasNonNull("resultId"))r.put("resultId",java.util.UUID.nameUUIDFromBytes(("gnm-result:"+command.path("commandId").asText()).getBytes(java.nio.charset.StandardCharsets.UTF_8)).toString());
        r.put("schemaVersion","1.1");
        if(!r.hasNonNull("completedAt")) {
            boolean replay="REPLAY".equals(e.getProperty("integrationIdempotencyDecision",String.class));
            if(replay)r.put("completedAt",command.path("createdAt").asText("1970-01-01T00:00:00Z")).put("timestampSource","LEGACY_COMMAND_CREATED_AT");
            else r.put("completedAt",java.time.Instant.now().toString());
        }
        String incident=r.path("providerNotificationIdentity").path("incidentId").asText();
        if(!incident.isBlank())r.put("externalId",incident);
        e.getMessage().setBody(r.toString());
    }
}
