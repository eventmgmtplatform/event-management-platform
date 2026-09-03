package com.eventmanagement.integration;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Named;
import org.apache.camel.http.base.HttpOperationFailedException;

import java.net.ConnectException;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.net.http.HttpTimeoutException;
import java.util.concurrent.TimeoutException;

@Named("serviceNowErrorClassifier")
@ApplicationScoped
public class ServiceNowErrorClassifier {

    public Classification classify(Throwable throwable) {

        Throwable cause = mostRelevantCause(throwable);

        if (cause instanceof CommandIdCollisionException) {
            return new Classification(
                    "COMMAND_ID_COLLISION",
                    "IDEMPOTENCY",
                    false,
                    null
            );
        }

        if (cause instanceof IllegalArgumentException) {
            return new Classification(
                    "INVALID_INTEGRATION_COMMAND",
                    "VALIDATION",
                    false,
                    null
            );
        }

        if (cause instanceof HttpOperationFailedException httpError) {
            return classifyHttpStatus(
                    httpError.getStatusCode()
            );
        }

        if (isTransportFailure(cause)) {
            return new Classification(
                    "SERVICENOW_UNAVAILABLE",
                    "TRANSPORT",
                    true,
                    null
            );
        }

        return new Classification(
                "INTERNAL_INTEGRATION_ERROR",
                "INTERNAL",
                false,
                null
        );
    }

    static Classification classifyHttpStatus(int status) {

        if (status == 408) {
            return new Classification(
                    "SERVICENOW_TIMEOUT",
                    "HTTP",
                    true,
                    status
            );
        }

        if (status == 429) {
            return new Classification(
                    "SERVICENOW_RATE_LIMITED",
                    "HTTP",
                    true,
                    status
            );
        }

        if (status >= 500 && status <= 599) {
            return new Classification(
                    "SERVICENOW_SERVER_ERROR",
                    "HTTP",
                    true,
                    status
            );
        }

        if (status == 401 || status == 403) {
            return new Classification(
                    "SERVICENOW_AUTHENTICATION_ERROR",
                    "HTTP",
                    false,
                    status
            );
        }

        if (status == 404) {
            return new Classification(
                    "SERVICENOW_RESOURCE_NOT_FOUND",
                    "HTTP",
                    false,
                    status
            );
        }

        if (status == 400 || status == 422) {
            return new Classification(
                    "SERVICENOW_BAD_REQUEST",
                    "HTTP",
                    false,
                    status
            );
        }

        return new Classification(
                "SERVICENOW_HTTP_ERROR",
                "HTTP",
                false,
                status
        );
    }

    private boolean isTransportFailure(Throwable cause) {

        return cause instanceof ConnectException ||
                cause instanceof SocketTimeoutException ||
                cause instanceof HttpTimeoutException ||
                cause instanceof TimeoutException ||
                cause instanceof SocketException;
    }

    private Throwable mostRelevantCause(Throwable throwable) {

        if (throwable == null) {
            return new IllegalStateException(
                    "Unknown integration failure"
            );
        }

        Throwable current = throwable;

        while (current.getCause() != null &&
                current.getCause() != current) {
            current = current.getCause();
        }

        return current;
    }

    public record Classification(
            String code,
            String category,
            boolean retryable,
            Integer httpStatus
    ) {
    }
}
