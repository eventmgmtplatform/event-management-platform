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

        from("kafka:{{enrichment.kafka.input-topic}}"
                + "?brokers={{enrichment.kafka.brokers}}"
                + "&groupId={{enrichment.kafka.consumer-group}}"
                + "&autoOffsetReset={{enrichment.kafka.auto-offset-reset}}"
                + "&autoCommitEnable=false"
                + "&allowManualCommit=true"
                + "&breakOnFirstError=true")
                .routeId("enrichment-events-raw-route")
                .log("Evento recibido desde {{enrichment.kafka.input-topic}}")
                .process("enrichmentPassThroughProcessor")
                .to("kafka:{{enrichment.kafka.output-topic}}"
                        + "?brokers={{enrichment.kafka.brokers}}"
                        + "&requestRequiredAcks=all")
                .process("enrichmentCommitProcessor")
                .log("Evento publicado en {{enrichment.kafka.output-topic}} y offset confirmado");
    }
}
