package com.eventmanagement.gateway;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.kafka.KafkaConstants;

@ApplicationScoped
public class EventGatewayRoute extends RouteBuilder {

    @Inject GatewayReceiptStore receipts;
    @Inject GatewayCollectionFailure failures;
    @Inject GatewayRulePipeline rules;

    @Override
    public void configure() {

        onException(Exception.class).handled(true).process(failures::respond);

        from("platform-http:/api/v1/gateway/ready?httpMethodRestrict=GET")
            .routeId("event-gateway-storage-readiness")
            .process(receipts::ready);

        /*
         * Estado del Event Gateway.
         */
        from("platform-http:/api/v1/gateway?httpMethodRestrict=GET")
            .routeId("event-gateway-status")
            .log("Solicitud recibida en GET /api/v1/gateway")
            .setHeader(
                Exchange.CONTENT_TYPE,
                constant("application/json")
            )
            .setHeader(
                Exchange.HTTP_RESPONSE_CODE,
                constant(200)
            )
            .setBody(
                constant(
                    "{\"status\":\"UP\","
                    + "\"service\":\"event-gateway\","
                    + "\"runtime\":\"camel-quarkus\"}"
                )
            );

        /*
         * Recepción, validación, normalización y publicación en Kafka.
         */
        from("platform-http:/api/v1/events?httpMethodRestrict=POST")
            .routeId("event-gateway-ingress")
            .convertBodyTo(byte[].class)
            .process(receipts::capture)
            .convertBodyTo(String.class)

            /*
             * Valida y sustituye el body por el evento normalizado.
             */
            .process(rules::process)
            .process(receipts::validated)

            /*
             * Conservamos los datos que necesitaremos para construir
             * la respuesta HTTP después de publicar en Kafka.
             */
            .setProperty(
                "normalizedEvent",
                body()
            )
            .setProperty(
                "publishedEventId",
                header("eventId")
            )

            /*
             * Zabbix Message Bus v1.1 utiliza eventKey para garantizar
             * orden por identidad lógica. El contrato legacy establece
             * eventKey=eventId para conservar su comportamiento.
             */
            .setHeader(
                KafkaConstants.KEY,
                header("eventKey")
            )

            .log(
                "Publicando evento en Kafka. "
                + "topic={{event.gateway.kafka.topic}}, "
                + "eventId=${header.eventId}, "
                + "eventKey=${header.eventKey}"
            )

            /*
             * Publicación síncrona en el tópico events.raw.
             */
            .to(
                "kafka:{{event.gateway.kafka.topic}}"
                + "?brokers={{event.gateway.kafka.brokers}}"
                + "&requestRequiredAcks=all"
            )

            .setProperty("gatewayKafkaAcknowledged",constant(true))
            .process(e -> receipts.mark(e,"PUBLISHED",null))

            .log(
                "Evento publicado en Kafka. "
                + "eventId=${exchangeProperty.publishedEventId}"
            )

            /*
             * Evita que headers internos de Camel/Kafka terminen
             * expuestos como headers HTTP.
             */
            .removeHeaders("*")
            .setHeader("X-Gateway-Receipt-Id",exchangeProperty("gatewayReceiptId"))

            .setHeader(
                Exchange.CONTENT_TYPE,
                constant("application/json")
            )
            .setHeader(
                Exchange.HTTP_RESPONSE_CODE,
                constant(202)
            )
            .setBody(
                simple(
                    "{\"accepted\":true,"
                    + "\"message\":\"Event published to Kafka\","
                    + "\"eventId\":\"${exchangeProperty.publishedEventId}\","
                    + "\"topic\":\"{{event.gateway.kafka.topic}}\"}"
                )
            );
    }
}
