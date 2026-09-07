package com.eventmanagement.integration;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;

/**
 * Camel boundary for the GNM Launch mutation.
 *
 * This processor is intentionally thin:
 * - GnmCommandProcessor prepares the provider context/request.
 * - GnmRetryExecutor owns retry semantics.
 * - This class only adapts Exchange state to that executor.
 *
 * It must only be invoked after an EXECUTE durable claim.
 */
@Named("gnmLaunchExecutionProcessor")
@ApplicationScoped
public class GnmLaunchExecutionProcessor implements Processor {

    private final GnmRetryExecutor retryExecutor;

    @Inject
    public GnmLaunchExecutionProcessor(GnmRetryExecutor retryExecutor) {
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
                retryExecutor.executeLaunch(
                        context.organizationId(),
                        request.toString()
                );

        exchange.setProperty(
                "gnmLaunchExecutionResult",
                result
        );
    }
}
