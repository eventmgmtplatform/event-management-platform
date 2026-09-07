package com.eventmanagement.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;

@Named("gnmLaunchResultProcessor")
@ApplicationScoped
public class GnmLaunchResultProcessor implements Processor {

    private final ObjectMapper objectMapper;

    @Inject
    public GnmLaunchResultProcessor(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public void process(Exchange exchange) throws Exception {

        GnmRetryExecutor.ExecutionResult execution =
                exchange.getProperty(
                        "gnmLaunchExecutionResult",
                        GnmRetryExecutor.ExecutionResult.class
                );

        if (execution == null) {
            throw new IllegalStateException(
                    "GNM Launch execution result unavailable"
            );
        }

        if (execution.outcome() !=
                GnmRetryExecutor.Outcome.SUCCESS) {

            throw new IllegalStateException(
                    "GNM Launch did not complete successfully: "
                            + execution.outcome()
            );
        }

        GnmHttpInvoker.HttpResult httpResult =
                execution.httpResult();

        if (httpResult == null) {
            throw new IllegalStateException(
                    "GNM successful execution has no HTTP result"
            );
        }

        JsonNode providerResponse =
                objectMapper.readTree(httpResult.responseBody());

        String incidentId =
                requiredText(
                        providerResponse.path("result"),
                        "id"
                );

        JsonNode canonicalEvent =
                exchange.getProperty(
                        "gnmCanonicalEvent",
                        JsonNode.class
                );

        if (canonicalEvent == null ||
                !canonicalEvent.isObject()) {
            throw new IllegalStateException(
                    "GNM canonical event unavailable"
            );
        }

        String commandId =
                requiredExchangeProperty(
                        exchange,
                        "commandId"
                );

        String eventId =
                requiredExchangeProperty(
                        exchange,
                        "eventId"
                );

        String serverSerial =
                canonicalEvent.path("legacyCorrelation")
                        .path("serverSerial")
                        .asText("");

        ObjectNode result =
                objectMapper.createObjectNode();

        result.put("commandId", commandId);
        result.put("eventId", eventId);
        result.put("integrationType", "GNM");
        result.put("operation", "SEND_NOTIFICATION");
        result.put("status", "SUCCESS");

        result.put(
                "provider",
                "EVERBRIDGE"
        );

        ObjectNode transport =
                result.putObject(
                        "providerTransportResult"
                );

        transport.put(
                "httpStatus",
                httpResult.httpStatus()
        );

        transport.put(
                "accepted",
                true
        );

        ObjectNode identity =
                result.putObject(
                        "providerNotificationIdentity"
                );

        identity.put(
                "incidentId",
                incidentId
        );

        identity.put(
                "lifecycleState",
                "LAUNCH_ACCEPTED"
        );

        /*
         * OPEN is intentionally NOT confirmed here.
         *
         * GNM-03D will GET the provider incident and obtain:
         *   incidentStatus
         *   openNotificationId
         *   phase identity
         */

        if (!serverSerial.isBlank()) {
            result.putObject("correlation")
                    .put(
                            "serverSerial",
                            serverSerial
                    );
        }

        exchange.setProperty(
                "gnmIncidentId",
                incidentId
        );

        exchange.setProperty(
                "gnmLaunchLifecycleState",
                "LAUNCH_ACCEPTED"
        );

        exchange.getMessage()
                .setBody(
                        objectMapper.writeValueAsString(result)
                );
    }

    private String requiredText(
            JsonNode node,
            String field
    ) {

        JsonNode value =
                node.get(field);

        if (value == null ||
                value.isNull() ||
                value.asText().isBlank()) {

            throw new IllegalStateException(
                    "Required GNM provider response field missing: "
                            + field
            );
        }

        return value.asText().trim();
    }

    private String requiredExchangeProperty(
            Exchange exchange,
            String property
    ) {

        String value =
                exchange.getProperty(
                        property,
                        String.class
                );

        if (value == null ||
                value.isBlank()) {

            throw new IllegalStateException(
                    "Required exchange property missing: "
                            + property
            );
        }

        return value;
    }
}
