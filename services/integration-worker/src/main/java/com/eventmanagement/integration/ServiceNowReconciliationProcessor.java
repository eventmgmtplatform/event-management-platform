package com.eventmanagement.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

@Named("serviceNowReconciliationProcessor")
@ApplicationScoped
public class ServiceNowReconciliationProcessor
        implements Processor {

    public static final String OUTCOME_PROPERTY =
            "serviceNowReconciliationOutcome";

    private static final Logger LOG = Logger.getLogger(
            ServiceNowReconciliationProcessor.class
    );

    private final ServiceNowLookupClient lookupClient;
    private final ObjectMapper objectMapper;
    private final int notFoundConfirmations;
    private final long confirmationDelayMs;

    @Inject
    public ServiceNowReconciliationProcessor(
            ServiceNowLookupClient lookupClient,
            ObjectMapper objectMapper,
            @ConfigProperty(
                    name = "integration.servicenow.reconciliation.not-found-confirmations",
                    defaultValue = "3"
            ) int notFoundConfirmations,
            @ConfigProperty(
                    name = "integration.servicenow.reconciliation.confirmation-delay-ms",
                    defaultValue = "1000"
            ) long confirmationDelayMs
    ) {
        if (notFoundConfirmations < 2) {
            throw new IllegalArgumentException(
                    "At least two NOT_FOUND confirmations are required"
            );
        }
        if (confirmationDelayMs < 0) {
            throw new IllegalArgumentException(
                    "Confirmation delay cannot be negative"
            );
        }
        this.lookupClient = lookupClient;
        this.objectMapper = objectMapper;
        this.notFoundConfirmations = notFoundConfirmations;
        this.confirmationDelayMs = confirmationDelayMs;
    }

    @Override
    public void process(Exchange exchange) throws Exception {
        exchange.removeProperty(OUTCOME_PROPERTY);
        String commandId = exchange.getProperty("commandId", String.class);
        String eventId = exchange.getProperty("eventId", String.class);

        for (int confirmation = 1;
             confirmation <= notFoundConfirmations;
             confirmation++) {
            ServiceNowLookupClient.LookupResult result;
            try {
                result = lookupClient.findByEventId(eventId);
            } catch (Exception exception) {
                exchange.setProperty(OUTCOME_PROPERTY, "RETRY_LATER");
                LOG.warnv(
                        "ServiceNow reconciliation deferred: " +
                        "commandId={0}, error={1}",
                        commandId,
                        exception.getClass().getSimpleName()
                );
                return;
            }

            if (result.status() == ServiceNowLookupClient.Status.FOUND) {
                ObjectNode response = objectMapper.createObjectNode();
                response.set("result", result.ticket());
                exchange.getMessage().setBody(
                        objectMapper.writeValueAsString(response)
                );
                exchange.getMessage().setHeader(
                        Exchange.HTTP_RESPONSE_CODE,
                        200
                );
                exchange.setProperty("integrationAttempt", 1);
                exchange.setProperty(OUTCOME_PROPERTY, "FOUND");
                LOG.infov(
                        "ServiceNow reconciliation found ticket: " +
                        "commandId={0}, eventId={1}",
                        commandId,
                        eventId
                );
                return;
            }

            LOG.infov(
                    "ServiceNow reconciliation NOT_FOUND confirmation: " +
                    "commandId={0}, confirmation={1}/{2}",
                    commandId,
                    confirmation,
                    notFoundConfirmations
            );

            if (confirmation < notFoundConfirmations &&
                    confirmationDelayMs > 0) {
                Thread.sleep(confirmationDelayMs);
            }
        }

        exchange.setProperty(OUTCOME_PROPERTY, "NOT_FOUND_CONFIRMED");
        LOG.warnv(
                "ServiceNow reconciliation confirmed no ticket: " +
                "commandId={0}, confirmations={1}",
                commandId,
                notFoundConfirmations
        );
    }
}
