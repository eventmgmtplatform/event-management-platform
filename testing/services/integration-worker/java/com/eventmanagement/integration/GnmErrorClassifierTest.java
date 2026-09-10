package com.eventmanagement.integration;

import org.junit.jupiter.api.Test;

import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.net.http.HttpConnectTimeoutException;
import java.net.http.HttpTimeoutException;
import java.util.concurrent.TimeoutException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GnmErrorClassifierTest {

    private final GnmErrorClassifier classifier = new GnmErrorClassifier();

    @Test
    void classifiesSuccessfulHttpStatusesAsSuccess() {
        assertEquals(
                GnmErrorClassifier.Classification.SUCCESS,
                classifier.classifyHttpStatus(200)
        );

        assertEquals(
                GnmErrorClassifier.Classification.SUCCESS,
                classifier.classifyHttpStatus(201)
        );

        assertEquals(
                GnmErrorClassifier.Classification.SUCCESS,
                classifier.classifyHttpStatus(204)
        );
    }

    @Test
    void classifiesAuthenticationAndValidationFailuresAsPermanent() {
        assertEquals(
                GnmErrorClassifier.Classification.PERMANENT,
                classifier.classifyHttpStatus(400)
        );

        assertEquals(
                GnmErrorClassifier.Classification.PERMANENT,
                classifier.classifyHttpStatus(401)
        );

        assertEquals(
                GnmErrorClassifier.Classification.PERMANENT,
                classifier.classifyHttpStatus(403)
        );

        assertEquals(
                GnmErrorClassifier.Classification.PERMANENT,
                classifier.classifyHttpStatus(404)
        );

        assertEquals(
                GnmErrorClassifier.Classification.PERMANENT,
                classifier.classifyHttpStatus(422)
        );
    }

    @Test
    void classifiesRateLimitAsRetryable() {
        assertEquals(
                GnmErrorClassifier.Classification.RETRYABLE,
                classifier.classifyHttpStatus(429)
        );
    }

    @Test
    void classifiesRequestTimeoutAsAmbiguous() {
        assertEquals(
                GnmErrorClassifier.Classification.AMBIGUOUS,
                classifier.classifyHttpStatus(408)
        );
    }

    @Test
    void classifiesServerFailuresAsAmbiguous() {
        assertEquals(
                GnmErrorClassifier.Classification.AMBIGUOUS,
                classifier.classifyHttpStatus(500)
        );

        assertEquals(
                GnmErrorClassifier.Classification.AMBIGUOUS,
                classifier.classifyHttpStatus(502)
        );

        assertEquals(
                GnmErrorClassifier.Classification.AMBIGUOUS,
                classifier.classifyHttpStatus(503)
        );

        assertEquals(
                GnmErrorClassifier.Classification.AMBIGUOUS,
                classifier.classifyHttpStatus(504)
        );
    }

    @Test
    void detectsEverbridgeInvalidIncidentOperationAsAmbiguous() {
        String body =
                "{\"status\":400,\"message\":\"Invalid Incident operation.\"}";

        assertEquals(
                GnmErrorClassifier.Classification.AMBIGUOUS,
                classifier.classifyHttpResult(400, body)
        );

        assertTrue(classifier.requiresReconciliation(400, body));
    }

    @Test
    void ordinaryBadRequestRemainsPermanent() {
        String body =
                "{\"status\":400,\"message\":\"Bad Request\"}";

        assertEquals(
                GnmErrorClassifier.Classification.PERMANENT,
                classifier.classifyHttpResult(400, body)
        );

        assertFalse(classifier.requiresReconciliation(400, body));
    }

    @Test
    void invalidIncidentOperationDetectionIsCaseInsensitive() {
        assertTrue(
                classifier.isInvalidIncidentOperation(
                        "{\"message\":\"INVALID INCIDENT OPERATION.\"}"
                )
        );
    }

    @Test
    void nullOrBlankBodyIsNotInvalidIncidentOperation() {
        assertFalse(classifier.isInvalidIncidentOperation(null));
        assertFalse(classifier.isInvalidIncidentOperation(""));
        assertFalse(classifier.isInvalidIncidentOperation("   "));
    }

    @Test
    void connectFailureIsRetryable() {
        assertEquals(
                GnmErrorClassifier.Classification.RETRYABLE,
                classifier.classifyException(
                        new ConnectException("connection refused")
                )
        );
    }

    @Test
    void httpConnectTimeoutIsRetryable() {
        assertEquals(
                GnmErrorClassifier.Classification.RETRYABLE,
                classifier.classifyException(
                        new HttpConnectTimeoutException("connect timeout")
                )
        );
    }

    @Test
    void readAndGenericTimeoutsAreAmbiguous() {
        assertEquals(
                GnmErrorClassifier.Classification.AMBIGUOUS,
                classifier.classifyException(
                        new SocketTimeoutException("read timeout")
                )
        );

        assertEquals(
                GnmErrorClassifier.Classification.AMBIGUOUS,
                classifier.classifyException(
                        new HttpTimeoutException("http timeout")
                )
        );

        assertEquals(
                GnmErrorClassifier.Classification.AMBIGUOUS,
                classifier.classifyException(
                        new TimeoutException("timeout")
                )
        );
    }

    @Test
    void nestedTransportFailureIsDetected() {
        RuntimeException wrapped =
                new RuntimeException(
                        "wrapper",
                        new ConnectException("connection refused")
                );

        assertEquals(
                GnmErrorClassifier.Classification.RETRYABLE,
                classifier.classifyException(wrapped)
        );
    }

    @Test
    void unknownExceptionIsPermanentByDefault() {
        assertEquals(
                GnmErrorClassifier.Classification.PERMANENT,
                classifier.classifyException(
                        new IllegalStateException("unexpected")
                )
        );
    }

    @Test
    void retryWithoutReconciliationIsRestrictedToRetryableClassification() {
        assertTrue(
                classifier.isRetryableWithoutReconciliation(
                        429,
                        "{\"message\":\"Too Many Requests\"}"
                )
        );

        assertFalse(
                classifier.isRetryableWithoutReconciliation(
                        500,
                        "{\"message\":\"Internal Server Error\"}"
                )
        );

        assertFalse(
                classifier.isRetryableWithoutReconciliation(
                        400,
                        "{\"message\":\"Invalid Incident operation.\"}"
                )
        );
    }
}
