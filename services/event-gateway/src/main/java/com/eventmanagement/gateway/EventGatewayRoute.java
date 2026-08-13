package com.eventmanagement.gateway;

import jakarta.enterprise.context.ApplicationScoped;
import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.kafka.KafkaConstants;

@ApplicationScoped
public class EventGatewayRoute extends RouteBuilder {

    @Override
    public void configure() {

        /*
         * Errores de validación.
         */
        onException(IllegalArgumentException.class)
            .handled(true)
            .process("gatewayExceptionHandler");

        /*
         * Errores de infraestructura, por ejemplo:
         * broker Kafka no disponible.
         */
        onException(Exception.class)
            .handled(true)
            .setHeader(
                Exchange.HTTP_RESPONSE_CODE,
                constant(503)
            )
            .setHeader(
                Exchange.CONTENT_TYPE,
                constant("application/json")
            )
            .setBody(
                simple(
                    "{\"accepted\":false,"
                    + "\"errorCode\":\"EVENT_DELIVERY_FAILED\","
                    + "\"message\":\"No fue posible publicar el evento\","
                    + "\"timestamp\":\"${date:now:yyyy-MM-dd'T'HH:mm:ssXXX}\"}"
                )
            );

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
            .convertBodyTo(String.class)
            .log("Evento original recibido: ${body}")

            /*
             * Valida y sustituye el body por el evento normalizado.
             */
            .process("eventValidationProcessor")

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

            .log(
                "Evento publicado en Kafka. "
                + "eventId=${exchangeProperty.publishedEventId}"
            )

            /*
             * Evita que headers internos de Camel/Kafka terminen
             * expuestos como headers HTTP.
             */
            .removeHeaders("*")

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
