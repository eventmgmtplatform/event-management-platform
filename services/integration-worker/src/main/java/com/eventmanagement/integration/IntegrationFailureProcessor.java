package com.eventmanagement.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.component.kafka.KafkaConstants;
import org.jboss.logging.Logger;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

@Named("integrationFailureProcessor")
@ApplicationScoped
public class IntegrationFailureProcessor implements Processor {

    private static final Logger LOG =
            Logger.getLogger(IntegrationFailureProcessor.class);

    private final ObjectMapper objectMapper;
    private final ServiceNowErrorClassifier errorClassifier;

    @Inject
    public IntegrationFailureProcessor(
            ObjectMapper objectMapper,
            ServiceNowErrorClassifier errorClassifier
    ) {
        this.objectMapper = objectMapper;
        this.errorClassifier = errorClassifier;
    }

    @Override
    public void process(Exchange exchange) throws Exception {

        Exception exception = exchange.getProperty(
                Exchange.EXCEPTION_CAUGHT,
                Exception.class
        );

        String commandId =
                propertyOrDefault(exchange, "commandId", "UNKNOWN");

        String correlationId =
                propertyOrDefault(
                        exchange,
                        "correlationId",
                        commandId
                );

        String eventId =
                propertyOrDefault(exchange, "eventId", "UNKNOWN");

        String eventKey =
                propertyOrDefault(exchange, "eventKey", eventId);

        String tenant =
                propertyOrDefault(exchange, "tenant", "UNKNOWN");

        String integrationType =
                propertyOrDefault(
                        exchange,
                        "integrationType",
                        "SERVICENOW"
                );

        String operation =
                propertyOrDefault(
                        exchange,
                        "operation",
                        "CREATE_TICKET"
                );

        String errorMessage =
                exception != null && exception.getMessage() != null
                        ? exception.getMessage()
                        : "Error desconocido de integración";

        Integer httpStatus = exchange.getMessage().getHeader(
                Exchange.HTTP_RESPONSE_CODE,
                Integer.class
        );

        ServiceNowErrorClassifier.Classification classification =
                errorClassifier.classify(exception);

        Integer resolvedHttpStatus =
                httpStatus != null
                        ? httpStatus
                        : classification.httpStatus();

        Integer attempt =
                exchange.getProperty(
                        "integrationAttempt",
                        Integer.class
                );

        if (attempt == null || attempt < 1) {
            attempt = 1;
        }

        ObjectNode failureResult =
                objectMapper.createObjectNode();

        failureResult.put(
                "schemaVersion",
                "1.1"
        );

        failureResult.put(
                "resultId",
                UUID.randomUUID().toString()
        );

        failureResult.put(
                "commandId",
                commandId
        );

        failureResult.put(
                "correlationId",
                correlationId
        );

        failureResult.put(
                "eventId",
                eventId
        );

        failureResult.put(
                "eventKey",
                eventKey
        );

        failureResult.put(
                "tenant",
                tenant
        );

        failureResult.put(
                "integrationType",
                integrationType
        );

        failureResult.put(
                "operation",
                operation
        );

        failureResult.put(
                "status",
                "FAILED"
        );

        failureResult.put(
                "attempt",
                attempt
        );

        failureResult.putNull(
                "externalId"
        );

        failureResult.putNull(
                "externalReference"
        );

        failureResult.putNull(
                "externalSystemId"
        );

        failureResult.put(
                "completedAt",
                OffsetDateTime.now(ZoneOffset.UTC).toString()
        );

        if (resolvedHttpStatus != null) {
            failureResult.put(
                    "httpStatus",
                    resolvedHttpStatus
            );
        } else {
            failureResult.putNull("httpStatus");
        }

        ObjectNode error =
                failureResult.putObject("error");

        error.put(
                "code",
                classification.code()
        );

        error.put(
                "category",
                classification.category()
        );

        error.put(
                "retryable",
                classification.retryable()
        );

        error.put(
                "attempt",
                attempt
        );

        error.put(
                "type",
                exception != null
                        ? exception.getClass().getSimpleName()
                        : "UnknownException"
        );

        error.put(
                "message",
                errorMessage
        );

        exchange.getMessage().setBody(
                objectMapper.writeValueAsString(failureResult)
        );

        exchange.getMessage().setHeader(
                KafkaConstants.KEY,
                eventKey
        );

        /*
         * Limpiamos la excepción para permitir publicar
         * el resultado FAILED en integration.results.
         */
        exchange.setException(null);

        LOG.errorv(
                exception,
                "Integración fallida: commandId={0}, eventId={1}",
                commandId,
                eventId
        );
    }

    private String propertyOrDefault(
            Exchange exchange,
            String propertyName,
            String defaultValue
    ) {

        String value =
                exchange.getProperty(propertyName, String.class);

        if (value == null || value.isBlank()) {
            return defaultValue;
        }

        return value;
    }
}
