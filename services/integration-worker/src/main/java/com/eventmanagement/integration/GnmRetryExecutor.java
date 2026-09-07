package com.eventmanagement.integration;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

import java.util.Objects;

/**
 * Provider-specific bounded retry executor for GNM / Everbridge.
 *
 * <p>This component is intentionally not connected to the Camel route yet.
 * It executes Launch transport attempts and delegates all failure meaning
 * to {@link GnmErrorClassifier}.</p>
 *
 * <p>Only RETRYABLE failures may be repeated. AMBIGUOUS failures are never
 * blindly retried because the provider may already have accepted the
 * mutation. Those failures must be surfaced for reconciliation.</p>
 */
@ApplicationScoped
public class GnmRetryExecutor {

    private static final Logger LOG =
            Logger.getLogger(GnmRetryExecutor.class);

    public enum Outcome {
        SUCCESS,
        PERMANENT_FAILURE,
        RETRY_EXHAUSTED,
        RECONCILIATION_REQUIRED
    }

    public record ExecutionResult(
            Outcome outcome,
            int attempts,
            GnmHttpInvoker.HttpResult httpResult,
            Throwable failure
    ) {
        public ExecutionResult {
            Objects.requireNonNull(outcome, "outcome");

            if (attempts < 1) {
                throw new IllegalArgumentException(
                        "attempts must be at least 1");
            }
        }

        public boolean successful() {
            return outcome == Outcome.SUCCESS;
        }

        public boolean reconciliationRequired() {
            return outcome == Outcome.RECONCILIATION_REQUIRED;
        }
    }

    private final GnmHttpInvoker httpInvoker;
    private final GnmErrorClassifier errorClassifier;
    private final int maxAttempts;
    private final long initialDelayMs;

    @Inject
    public GnmRetryExecutor(
            GnmHttpInvoker httpInvoker,
            GnmErrorClassifier errorClassifier,
            @ConfigProperty(
                    name = "integration.gnm.retry.max-attempts",
                    defaultValue = "3")
            int maxAttempts,
            @ConfigProperty(
                    name = "integration.gnm.retry.initial-delay-ms",
                    defaultValue = "1000")
            long initialDelayMs) {

        if (maxAttempts < 1) {
            throw new IllegalArgumentException(
                    "Retry max-attempts must be at least 1");
        }

        if (initialDelayMs < 0) {
            throw new IllegalArgumentException(
                    "Retry initial-delay-ms cannot be negative");
        }

        this.httpInvoker =
                Objects.requireNonNull(httpInvoker, "httpInvoker");

        this.errorClassifier =
                Objects.requireNonNull(errorClassifier, "errorClassifier");

        this.maxAttempts = maxAttempts;
        this.initialDelayMs = initialDelayMs;
    }

    public ExecutionResult executeLaunch(
            String organizationId,
            String requestBody) {

        for (int attempt = 1;
             attempt <= maxAttempts;
             attempt++) {

            try {
                GnmHttpInvoker.HttpResult result =
                        httpInvoker.launch(
                                organizationId,
                                requestBody);

                GnmErrorClassifier.Classification classification =
                        errorClassifier.classifyHttpResult(
                                result.httpStatus(),
                                result.responseBody());

                switch (classification) {

                    case SUCCESS:
                        LOG.infov(
                                "GNM Launch succeeded: attempt={0}, httpStatus={1}",
                                attempt,
                                result.httpStatus());

                        return new ExecutionResult(
                                Outcome.SUCCESS,
                                attempt,
                                result,
                                null);

                    case PERMANENT:
                        LOG.warnv(
                                "GNM Launch permanent failure: attempt={0}, httpStatus={1}",
                                attempt,
                                result.httpStatus());

                        return new ExecutionResult(
                                Outcome.PERMANENT_FAILURE,
                                attempt,
                                result,
                                null);

                    case AMBIGUOUS:
                        LOG.warnv(
                                "GNM Launch requires reconciliation: attempt={0}, httpStatus={1}",
                                attempt,
                                result.httpStatus());

                        return new ExecutionResult(
                                Outcome.RECONCILIATION_REQUIRED,
                                attempt,
                                result,
                                null);

                    case RETRYABLE:
                        if (attempt >= maxAttempts) {
                            return new ExecutionResult(
                                    Outcome.RETRY_EXHAUSTED,
                                    attempt,
                                    result,
                                    null);
                        }

                        sleepBeforeNextAttempt(attempt);
                        break;
                }

            } catch (Exception exception) {

                GnmErrorClassifier.Classification classification =
                        errorClassifier.classifyException(exception);

                switch (classification) {

                    case AMBIGUOUS:
                        return new ExecutionResult(
                                Outcome.RECONCILIATION_REQUIRED,
                                attempt,
                                null,
                                exception);

                    case PERMANENT:
                        return new ExecutionResult(
                                Outcome.PERMANENT_FAILURE,
                                attempt,
                                null,
                                exception);

                    case RETRYABLE:
                        if (attempt >= maxAttempts) {
                            return new ExecutionResult(
                                    Outcome.RETRY_EXHAUSTED,
                                    attempt,
                                    null,
                                    exception);
                        }

                        try {
                            sleepBeforeNextAttempt(attempt);
                        } catch (InterruptedException interrupted) {
                            Thread.currentThread().interrupt();

                            return new ExecutionResult(
                                    Outcome.PERMANENT_FAILURE,
                                    attempt,
                                    null,
                                    interrupted);
                        }

                        break;

                    case SUCCESS:
                        throw new IllegalStateException(
                                "Exception cannot be classified as SUCCESS",
                                exception);
                }
            }
        }

        throw new IllegalStateException(
                "GNM retry loop finished without a result");
    }

    /**
     * Executes CloseWithNotification as a single provider mutation.
     *
     * Close is intentionally not blindly retried. Everbridge Close is
     * non-idempotent: a repeated mutation may return
     * "Invalid Incident operation" after the first PUT already closed
     * the incident. Ambiguous outcomes are therefore surfaced for
     * authoritative GET reconciliation.
     */
    public ExecutionResult executeClose(
            String organizationId,
            String incidentId,
            String requestBody) {

        final int attempt = 1;

        try {
            GnmHttpInvoker.HttpResult result =
                    httpInvoker.closeIncident(
                            organizationId,
                            incidentId,
                            requestBody);

            /*
             * Everbridge duplicate Close:
             *
             * PUT against an already-closed incident returns HTTP 400
             * with "Invalid Incident operation".
             *
             * This is not safe to classify as a terminal permanent
             * failure because the desired provider state may already
             * be Closed. Force GET reconciliation and never send a
             * second PUT.
             */
            if (result.httpStatus() == 400
                    && errorClassifier.isInvalidIncidentOperation(
                            result.responseBody())) {

                LOG.warnv(
                        "GNM Close returned Invalid Incident operation; "
                                + "GET reconciliation required: "
                                + "attempt={0}, httpStatus={1}",
                        attempt,
                        result.httpStatus());

                return new ExecutionResult(
                        Outcome.RECONCILIATION_REQUIRED,
                        attempt,
                        result,
                        null);
            }

            GnmErrorClassifier.Classification classification =
                    errorClassifier.classifyHttpResult(
                            result.httpStatus(),
                            result.responseBody());

            switch (classification) {

                case SUCCESS:
                    LOG.infov(
                            "GNM Close accepted: attempt={0}, httpStatus={1}",
                            attempt,
                            result.httpStatus());

                    return new ExecutionResult(
                            Outcome.SUCCESS,
                            attempt,
                            result,
                            null);

                case PERMANENT:
                    return new ExecutionResult(
                            Outcome.PERMANENT_FAILURE,
                            attempt,
                            result,
                            null);

                case AMBIGUOUS:
                case RETRYABLE:
                    /*
                     * RETRYABLE does not mean retry the mutation here.
                     * For Close, both states require GET reconciliation.
                     */
                    return new ExecutionResult(
                            Outcome.RECONCILIATION_REQUIRED,
                            attempt,
                            result,
                            null);
            }

        } catch (Exception exception) {

            GnmErrorClassifier.Classification classification =
                    errorClassifier.classifyException(exception);

            switch (classification) {

                case PERMANENT:
                    return new ExecutionResult(
                            Outcome.PERMANENT_FAILURE,
                            attempt,
                            null,
                            exception);

                case AMBIGUOUS:
                case RETRYABLE:
                    return new ExecutionResult(
                            Outcome.RECONCILIATION_REQUIRED,
                            attempt,
                            null,
                            exception);

                case SUCCESS:
                    throw new IllegalStateException(
                            "Exception cannot be classified as SUCCESS",
                            exception);
            }
        }

        throw new IllegalStateException(
                "GNM Close execution finished without a result");
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
