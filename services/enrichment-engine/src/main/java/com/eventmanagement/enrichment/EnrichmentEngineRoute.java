package com.eventmanagement.enrichment;

import jakarta.enterprise.context.ApplicationScoped;
import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;

@ApplicationScoped
public class EnrichmentEngineRoute extends RouteBuilder {
    @Override
    public void configure() {
        from("platform-http:/api/v1/enrichment?httpMethodRestrict=GET")
                .routeId("enrichment-engine-status")
                .setHeader(Exchange.CONTENT_TYPE, constant("application/json"))
                .setHeader(Exchange.HTTP_RESPONSE_CODE, constant(200))
                .setBody(constant(
                        "{\"status\":\"UP\",\"service\":\"enrichment-engine\",\"message\":\"Hola Mundo\"}"
                ));
    }
}
