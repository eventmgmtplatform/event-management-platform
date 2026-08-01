package com.eventmanagement.integration;

import com.fasterxml.jackson.databind.JsonNode;
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

@Named("integrationResultProcessor")
@ApplicationScoped
public class IntegrationResultProcessor implements Processor {

    private static final Logger LOG =
            Logger.getLogger(IntegrationResultProcessor.class);

    private final ObjectMapper objectMapper;

    @Inject
    public IntegrationResultProcessor(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public void process(Exchange exchange) throws Exception {

        String responseBody =
                exchange.getMessage().getBody(String.class);

        int httpStatus = exchange.getMessage().getHeader(
                Exchange.HTTP_RESPONSE_CODE,
                0,
                Integer.class
        );

        if (httpStatus < 200 || httpStatus >= 300) {
            throw new IllegalStateException(
                    "ServiceNow respondió HTTP " + httpStatus
            );
        }

        JsonNode serviceNowResponse =
                objectMapper.readTree(responseBody);

        JsonNode resultNode =
                serviceNowResponse.path("result");

        String ticketNumber =
                resultNode.path("number").asText("");

        String sysId =
                resultNode.path("sys_id").asText("");

        if (ticketNumber.isBlank()) {
            throw new IllegalStateException(
                    "ServiceNow no devolvió result.number"
            );
        }

        String commandId =
                exchange.getProperty("commandId", String.class);

        String eventId =
                exchange.getProperty("eventId", String.class);

        String eventKey =
                exchange.getProperty("eventKey", String.class);

        String tenant =
                exchange.getProperty("tenant", String.class);

        ObjectNode integrationResult =
                objectMapper.createObjectNode();

        integrationResult.put(
                "schemaVersion",
                "1.0"
        );

        integrationResult.put(
                "resultId",
                UUID.randomUUID().toString()
        );

        integrationResult.put(
                "commandId",
                commandId
        );

        integrationResult.put(
                "eventId",
                eventId
        );

        integrationResult.put(
                "eventKey",
                eventKey
        );

        integrationResult.put(
                "tenant",
                tenant
        );

        integrationResult.put(
                "integrationType",
                "SERVICENOW"
        );

        integrationResult.put(
                "operation",
                "CREATE_TICKET"
        );

        integrationResult.put(
                "status",
                "SUCCESS"
        );

        integrationResult.put(
                "externalId",
                ticketNumber
        );

        integrationResult.put(
                "ticketNumber",
                ticketNumber
        );

        integrationResult.put(
                "completedAt",
                OffsetDateTime.now(ZoneOffset.UTC).toString()
        );

        integrationResult.put(
                "httpStatus",
                httpStatus
        );

        ObjectNode response =
                integrationResult.putObject("response");

        response.put("ticketNumber", ticketNumber);
        response.put("sysId", sysId);

        response.set(
                "raw",
                serviceNowResponse.deepCopy()
        );

        String resultJson =
                objectMapper.writeValueAsString(integrationResult);

        exchange.getMessage().setBody(resultJson);

        exchange.getMessage().setHeader(
                KafkaConstants.KEY,
                eventKey
        );

        LOG.infov(
                "Resultado exitoso: eventId={0}, ticketNumber={1}",
                eventId,
                ticketNumber
        );
    }
}
