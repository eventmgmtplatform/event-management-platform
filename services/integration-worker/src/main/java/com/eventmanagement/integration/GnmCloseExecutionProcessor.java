package com.eventmanagement.integration;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;

/**
 * Camel boundary for the GNM CloseWithNotification mutation.
 *
 * <p>CLOSE is intentionally single-mutation. GnmRetryExecutor.executeClose()
 * must never blindly repeat the provider PUT.</p>
 */
@Named("gnmCloseExecutionProcessor")
@ApplicationScoped
public class GnmCloseExecutionProcessor implements Processor {

    private final GnmRetryExecutor retryExecutor;

    @Inject
    public GnmCloseExecutionProcessor(
            GnmRetryExecutor retryExecutor
    ) {
        this.retryExecutor = retryExecutor;
    }

    @Override
    public void process(Exchange exchange) throws Exception {

        EverbridgeProviderContext context =
                exchange.getProperty(
                        "gnmProviderContext",
                        EverbridgeProviderContext.class
                );

        JsonNode request =
                exchange.getProperty(
                        "gnmProviderRequest",
                        JsonNode.class
                );

        String incidentId =
                requiredProperty(
                        exchange,
                        "gnmIncidentId"
                );

        if (context == null) {
            throw new IllegalStateException(
                    "Missing exchange property: gnmProviderContext"
            );
        }

        if (request == null) {
            throw new IllegalStateException(
                    "Missing exchange property: gnmProviderRequest"
            );
        }

        GnmRetryExecutor.ExecutionResult result =
                retryExecutor.executeClose(
                        context.organizationId(),
                        incidentId,
                        request.toString()
                );

        exchange.setProperty(
                "gnmCloseExecutionResult",
                result
        );

        /*
         * Keep Camel Simple predicates scalar-only.
         *
         * Object navigation such as
         * ${exchangeProperty.gnmCloseExecutionResult.outcome}
         * requires Camel's bean language at runtime. The integration-worker
         * intentionally does not depend on that language merely to inspect
         * the provider execution outcome.
         */
        exchange.setProperty(
                "gnmCloseExecutionOutcome",
                result.outcome().name()
        );
    }

    private static String requiredProperty(
            Exchange exchange,
            String name
    ) {
        String value =
                exchange.getProperty(
                        name,
                        String.class
                );

        if (value == null || value.isBlank()) {
            throw new IllegalStateException(
                    "Missing exchange property: " + name
            );
        }

        return value.trim();
    }
}
