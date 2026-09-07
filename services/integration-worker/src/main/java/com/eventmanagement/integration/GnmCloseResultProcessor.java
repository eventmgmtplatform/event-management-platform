package com.eventmanagement.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;

/**
 * Converts a successful provider PUT into CLOSE_ACCEPTED.
 *
 * <p>A successful PUT is not authoritative proof of Closed state.
 * closeNotificationId is therefore never fabricated here.</p>
 */
@Named("gnmCloseResultProcessor")
@ApplicationScoped
public class GnmCloseResultProcessor implements Processor {

    private final ObjectMapper objectMapper;

    @Inject
    public GnmCloseResultProcessor(
            ObjectMapper objectMapper
    ) {
        this.objectMapper = objectMapper;
    }

    @Override
    public void process(Exchange exchange) throws Exception {

        GnmRetryExecutor.ExecutionResult execution =
                exchange.getProperty(
                        "gnmCloseExecutionResult",
                        GnmRetryExecutor.ExecutionResult.class
                );

        if (execution == null) {
            throw new IllegalStateException(
                    "GNM Close execution result unavailable"
            );
        }

        if (execution.outcome()
                != GnmRetryExecutor.Outcome.SUCCESS) {

            throw new IllegalStateException(
                    "GNM Close did not complete successfully: "
                            + execution.outcome()
            );
        }

        GnmHttpInvoker.HttpResult httpResult =
                execution.httpResult();

        if (httpResult == null) {
            throw new IllegalStateException(
                    "GNM successful Close execution has no HTTP result"
            );
        }

        String incidentId =
                requiredExchangeProperty(
                        exchange,
                        "gnmIncidentId"
                );

        /*
         * Parse provider response when present and validate its incident
         * identity if result.id was returned.
         */
        if (httpResult.responseBody() != null
                && !httpResult.responseBody().isBlank()) {

            JsonNode providerResponse =
                    objectMapper.readTree(
                            httpResult.responseBody()
                    );

            String returnedIncidentId =
                    providerResponse.path("result")
                            .path("id")
                            .asText("")
                            .trim();

            if (!returnedIncidentId.isBlank()
                    && !incidentId.equals(returnedIncidentId)) {

                throw new IllegalStateException(
                        "GNM Close provider response incidentId "
                                + "does not match requested identity"
                );
            }
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

        ObjectNode result =
                objectMapper.createObjectNode();

        result.put("commandId", commandId);
        result.put("eventId", eventId);
        result.put("integrationType", "GNM");
        result.put("operation", "CLOSE_NOTIFICATION");
        result.put("status", "SUCCESS");
        result.put("provider", "EVERBRIDGE");

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

        /*
         * Preserve an OPEN phase identity only when it is already available
         * on the Exchange. Never infer or fabricate it.
         */
        String openNotificationId =
                exchange.getProperty(
                        "gnmOpenNotificationId",
                        String.class
                );

        if (openNotificationId != null
                && !openNotificationId.isBlank()) {

            identity.put(
                    "openNotificationId",
                    openNotificationId.trim()
            );
        }

        identity.put(
                "lifecycleState",
                "CLOSE_ACCEPTED"
        );

        exchange.setProperty(
                "gnmCloseLifecycleState",
                "CLOSE_ACCEPTED"
        );

        exchange.setProperty(
                "gnmLifecycleState",
                "CLOSE_ACCEPTED"
        );

        exchange.getMessage().setBody(
                objectMapper.writeValueAsString(result)
        );
    }

    private static String requiredExchangeProperty(
            Exchange exchange,
            String property
    ) {

        String value =
                exchange.getProperty(
                        property,
                        String.class
                );

        if (value == null || value.isBlank()) {
            throw new IllegalStateException(
                    "Required exchange property missing: "
                            + property
            );
        }

        return value.trim();
    }
}
