package com.eventmanagement.integration;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

@Named("serviceNowRetryExecutor")
@ApplicationScoped
public class ServiceNowRetryExecutor
        implements Processor {

    private static final Logger LOG =
            Logger.getLogger(ServiceNowRetryExecutor.class);

    private final ServiceNowHttpInvoker httpInvoker;
    private final ServiceNowErrorClassifier errorClassifier;
    private final int maxAttempts;
    private final long initialDelayMs;

    @Inject
    public ServiceNowRetryExecutor(
            ServiceNowHttpInvoker httpInvoker,
            ServiceNowErrorClassifier errorClassifier,
            @ConfigProperty(
                    name = "integration.servicenow.retry.max-attempts",
                    defaultValue = "3"
            )
            int maxAttempts,
            @ConfigProperty(
                    name = "integration.servicenow.retry.initial-delay-ms",
                    defaultValue = "1000"
            )
            long initialDelayMs
    ) {
        if (maxAttempts < 1) {
            throw new IllegalArgumentException(
                    "Retry max-attempts must be at least 1"
            );
        }

        if (initialDelayMs < 0) {
            throw new IllegalArgumentException(
                    "Retry initial-delay-ms cannot be negative"
            );
        }

        this.httpInvoker = httpInvoker;
        this.errorClassifier = errorClassifier;
        this.maxAttempts = maxAttempts;
        this.initialDelayMs = initialDelayMs;
    }

    @Override
    public void process(Exchange exchange)
            throws Exception {

        String requestBody =
                exchange.getMessage().getBody(String.class);

        String commandId =
                exchange.getProperty(
                        "commandId",
                        String.class
                );

        for (int attempt = 1;
             attempt <= maxAttempts;
             attempt++) {

            exchange.setProperty(
                    "integrationAttempt",
                    attempt
            );

            try {
                ServiceNowHttpInvoker.HttpResult result =
                        httpInvoker.invoke(requestBody);

                exchange.getMessage().setBody(
                        result.responseBody()
                );

                exchange.getMessage().setHeader(
                        Exchange.HTTP_RESPONSE_CODE,
                        result.httpStatus()
                );

                LOG.infov(
                        "ServiceNow attempt succeeded: " +
                        "commandId={0}, attempt={1}",
                        commandId,
                        attempt
                );

                return;
            } catch (Exception exception) {

                ServiceNowErrorClassifier.Classification classification =
                        errorClassifier.classify(exception);

                boolean retry =
                        classification.retryable() &&
                        attempt < maxAttempts;

                LOG.warnv(
                        "ServiceNow attempt failed: " +
                        "commandId={0}, attempt={1}, " +
                        "code={2}, retry={3}",
                        commandId,
                        attempt,
                        classification.code(),
                        retry
                );

                if (!retry) {
                    throw exception;
                }

                sleepBeforeNextAttempt(attempt);
            }
        }

        throw new IllegalStateException(
                "Retry loop finished without a result"
        );
    }

    private void sleepBeforeNextAttempt(int failedAttempt)
            throws InterruptedException {

        long delayMs =
                initialDelayMs * failedAttempt;

        if (delayMs <= 0) {
            return;
        }

        Thread.sleep(delayMs);
    }
}
