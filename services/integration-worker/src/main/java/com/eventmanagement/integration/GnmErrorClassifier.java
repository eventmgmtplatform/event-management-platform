package com.eventmanagement.integration;

import jakarta.enterprise.context.ApplicationScoped;

import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.net.http.HttpConnectTimeoutException;
import java.net.http.HttpTimeoutException;
import java.util.Locale;
import java.util.Objects;
import java.util.concurrent.TimeoutException;

/**
 * Provider-specific error classifier for the GNM / Everbridge integration.
 *
 * <p>This component classifies failures only. It does not execute retries,
 * reconciliation, HTTP calls, Kafka operations, database operations, or
 * provider side effects.</p>
 *
 * <p>Mutation operations such as Launch and CloseWithNotification require
 * stronger semantics than a conventional HTTP retry classifier because an
 * ambiguous transport failure can occur after the provider has already
 * accepted the mutation.</p>
 */
@ApplicationScoped
public class GnmErrorClassifier {

    public enum Classification {
        SUCCESS,
        PERMANENT,
        RETRYABLE,
        AMBIGUOUS
    }

    public Classification classifyHttpStatus(int httpStatus) {
        if (httpStatus >= 200 && httpStatus < 300) {
            return Classification.SUCCESS;
        }

        if (httpStatus == 408) {
            return Classification.AMBIGUOUS;
        }

        if (httpStatus == 429) {
            return Classification.RETRYABLE;
        }

        if (httpStatus >= 500 && httpStatus <= 599) {
            return Classification.AMBIGUOUS;
        }

        if (httpStatus >= 400 && httpStatus <= 499) {
            return Classification.PERMANENT;
        }

        return Classification.PERMANENT;
    }

    /**
     * Classifies an HTTP result while preserving the special Everbridge
     * duplicate-close semantic.
     *
     * <p>HTTP 400 with "Invalid Incident operation." cannot by itself prove
     * that CLOSE failed. The incident may already be Closed, so authoritative
     * GET reconciliation is required.</p>
     */
    public Classification classifyHttpResult(int httpStatus, String responseBody) {
        if (httpStatus == 400 && isInvalidIncidentOperation(responseBody)) {
            return Classification.AMBIGUOUS;
        }

        return classifyHttpStatus(httpStatus);
    }

    /**
     * Classifies transport exceptions.
     *
     * <p>Connection establishment failures are retryable because no successful
     * connection to the provider was established. Timeout-like failures are
     * ambiguous because the request may already have reached the provider.</p>
     */
    public Classification classifyException(Throwable failure) {
        Objects.requireNonNull(failure, "failure");

        Throwable current = failure;

        while (current != null) {
            if (current instanceof HttpConnectTimeoutException) {
                return Classification.RETRYABLE;
            }

            if (current instanceof ConnectException) {
                return Classification.RETRYABLE;
            }

            if (current instanceof HttpTimeoutException
                    || current instanceof SocketTimeoutException
                    || current instanceof TimeoutException) {
                return Classification.AMBIGUOUS;
            }

            current = current.getCause();
        }

        return Classification.PERMANENT;
    }

    public boolean isInvalidIncidentOperation(String responseBody) {
        if (responseBody == null || responseBody.isBlank()) {
            return false;
        }

        return responseBody
                .toLowerCase(Locale.ROOT)
                .contains("invalid incident operation");
    }

    public boolean requiresReconciliation(
            int httpStatus,
            String responseBody
    ) {
        return classifyHttpResult(httpStatus, responseBody)
                == Classification.AMBIGUOUS;
    }

    public boolean isRetryableWithoutReconciliation(
            int httpStatus,
            String responseBody
    ) {
        return classifyHttpResult(httpStatus, responseBody)
                == Classification.RETRYABLE;
    }
}
