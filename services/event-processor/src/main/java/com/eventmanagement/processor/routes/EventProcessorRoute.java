package com.eventmanagement.processor.routes;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.Exchange;
import com.eventmanagement.processor.adapters.kafka.AcceptedEventProcessor;

@ApplicationScoped
public class EventProcessorRoute extends RouteBuilder {
    @Inject AcceptedEventProcessor processor;
    @Override public void configure() {
        // Do not let Camel's default error handler log message bodies or dependency exceptions.
        errorHandler(defaultErrorHandler().maximumRedeliveries(0).logExhausted(false));
        from("platform-http:/api/v1/enrichment?httpMethodRestrict=GET")
                .routeId("processor-legacy-status").setHeader(Exchange.CONTENT_TYPE,constant("application/json"))
                .setBody(constant("{\"status\":\"UP\",\"service\":\"event-processor\",\"mode\":\"FOUNDATION\"}"));
        from("kafka:{{processor.kafka.input-topic}}?brokers={{processor.kafka.brokers}}"
                +"&groupId={{processor.kafka.consumer-group}}&autoOffsetReset={{processor.kafka.auto-offset-reset}}"
                +"&autoCommitEnable=false&allowManualCommit=true&breakOnFirstError=true")
                .routeId("processor-events-raw").process(processor);
    }
}
