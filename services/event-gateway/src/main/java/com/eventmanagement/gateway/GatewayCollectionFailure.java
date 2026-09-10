package com.eventmanagement.gateway;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.apache.camel.Exchange;

@ApplicationScoped
public class GatewayCollectionFailure {
    private final GatewayReceiptStore store;
    private final ObjectMapper mapper;
    @Inject
    public GatewayCollectionFailure(GatewayReceiptStore store,ObjectMapper mapper) { this.store=store; this.mapper=mapper; }

    public void respond(Exchange exchange) throws Exception {
        Exception cause = exchange.getProperty(Exchange.EXCEPTION_CAUGHT,Exception.class);
        String id = exchange.getProperty("gatewayReceiptId",String.class);
        boolean published = Boolean.TRUE.equals(exchange.getProperty("gatewayKafkaAcknowledged",Boolean.class));
        boolean invalid = id != null && (cause instanceof IllegalArgumentException || cause instanceof com.fasterxml.jackson.core.JsonProcessingException);
        String code = id == null ? "ORIGINAL_STORAGE_UNAVAILABLE" : invalid ? "INVALID_EVENT" : published ? "PUBLICATION_CHECKPOINT_FAILED" : "EVENT_DELIVERY_FAILED";
        try { store.mark(exchange,invalid ? "REJECTED" : published ? "PUBLISH_UNCONFIRMED" : "DELIVERY_FAILED",code); }
        catch (Exception ignored) { invalid=false; code="RECEIPT_STATUS_UNAVAILABLE"; }
        var response=mapper.createObjectNode();
        response.put("accepted",false); response.put("errorCode",code);
        response.put("message",invalid ? "El evento no cumple el contrato" : "No fue posible confirmar la recepción y publicación del evento");
        if(id!=null) response.put("receiptId",id);
        exchange.getMessage().removeHeaders("*");
        exchange.getMessage().setHeader(Exchange.HTTP_RESPONSE_CODE,invalid ? 400 : 503);
        exchange.getMessage().setHeader(Exchange.CONTENT_TYPE,"application/json");
        if(id!=null) exchange.getMessage().setHeader("X-Gateway-Receipt-Id",id);
        exchange.getMessage().setBody(mapper.writeValueAsString(response));
    }
}
