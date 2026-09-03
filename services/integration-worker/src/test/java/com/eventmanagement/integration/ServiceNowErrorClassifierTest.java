package com.eventmanagement.integration;

import org.junit.jupiter.api.Test;

import java.net.ConnectException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ServiceNowErrorClassifierTest {

    private final ServiceNowErrorClassifier classifier =
            new ServiceNowErrorClassifier();

    @Test
    void shouldClassifyValidationErrorAsPermanent() {

        var result = classifier.classify(
                new IllegalArgumentException("Missing eventId")
        );

        assertEquals(
                "INVALID_INTEGRATION_COMMAND",
                result.code()
        );
        assertFalse(result.retryable());
    }

    @Test
    void shouldClassifyConnectionErrorAsRetryable() {

        var result = classifier.classify(
                new ConnectException("Connection refused")
        );

        assertEquals(
                "SERVICENOW_UNAVAILABLE",
                result.code()
        );
        assertTrue(result.retryable());
    }

    @Test
    void shouldClassifyHttp408AsRetryable() {

        var result =
                ServiceNowErrorClassifier
                        .classifyHttpStatus(408);

        assertEquals("SERVICENOW_TIMEOUT", result.code());
        assertTrue(result.retryable());
    }

    @Test
    void shouldClassifyHttp429AsRetryable() {

        var result =
                ServiceNowErrorClassifier
                        .classifyHttpStatus(429);

        assertEquals(
                "SERVICENOW_RATE_LIMITED",
                result.code()
        );
        assertTrue(result.retryable());
    }

    @Test
    void shouldClassifyHttp503AsRetryable() {

        var result =
                ServiceNowErrorClassifier
                        .classifyHttpStatus(503);

        assertEquals(
                "SERVICENOW_SERVER_ERROR",
                result.code()
        );
        assertTrue(result.retryable());
    }

    @Test
    void shouldClassifyAuthenticationErrorAsPermanent() {

        var result =
                ServiceNowErrorClassifier
                        .classifyHttpStatus(401);

        assertEquals(
                "SERVICENOW_AUTHENTICATION_ERROR",
                result.code()
        );
        assertFalse(result.retryable());
    }

    @Test
    void shouldClassifyNotFoundAsPermanent() {

        var result =
                ServiceNowErrorClassifier
                        .classifyHttpStatus(404);

        assertEquals(
                "SERVICENOW_RESOURCE_NOT_FOUND",
                result.code()
        );
        assertFalse(result.retryable());
    }

    @Test
    void shouldClassifyBadRequestAsPermanent() {

        var result =
                ServiceNowErrorClassifier
                        .classifyHttpStatus(400);

        assertEquals(
                "SERVICENOW_BAD_REQUEST",
                result.code()
        );
        assertFalse(result.retryable());
    }
}
