package com.eventmanagement.integration;

import org.junit.jupiter.api.Test;

import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.net.http.HttpConnectTimeoutException;
import java.net.http.HttpTimeoutException;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.Queue;
import java.util.concurrent.TimeoutException;

import static org.junit.jupiter.api.Assertions.*;

class GnmRetryExecutorTest {

    @Test
    void shouldSucceedOnFirstAttempt() {
        FakeInvoker invoker = new FakeInvoker();
        invoker.succeed(200, "{\"message\":\"OK\"}");

        GnmRetryExecutor.ExecutionResult result =
                executor(invoker, 3).executeLaunch("org-1", "{}");

        assertEquals(GnmRetryExecutor.Outcome.SUCCESS, result.outcome());
        assertEquals(1, result.attempts());
        assertEquals(1, invoker.invocations);
        assertEquals(200, result.httpResult().httpStatus());
        assertNull(result.failure());
    }

    @Test
    void shouldRetry429ThenSucceed() {
        FakeInvoker invoker = new FakeInvoker();
        invoker.succeed(429, "{\"message\":\"rate limited\"}");
        invoker.succeed(200, "{\"message\":\"OK\"}");

        GnmRetryExecutor.ExecutionResult result =
                executor(invoker, 3).executeLaunch("org-1", "{}");

        assertEquals(GnmRetryExecutor.Outcome.SUCCESS, result.outcome());
        assertEquals(2, result.attempts());
        assertEquals(2, invoker.invocations);
    }

    @Test
    void shouldExhaustRetryableHttpFailures() {
        FakeInvoker invoker = new FakeInvoker();
        invoker.succeed(429, "{}");
        invoker.succeed(429, "{}");
        invoker.succeed(429, "{\"message\":\"final\"}");

        GnmRetryExecutor.ExecutionResult result =
                executor(invoker, 3).executeLaunch("org-1", "{}");

        assertEquals(
                GnmRetryExecutor.Outcome.RETRY_EXHAUSTED,
                result.outcome());

        assertEquals(3, result.attempts());
        assertEquals(3, invoker.invocations);
        assertEquals(
                "{\"message\":\"final\"}",
                result.httpResult().responseBody());
    }

    @Test
    void shouldNotRetryPermanentHttpFailure() {
        FakeInvoker invoker = new FakeInvoker();
        invoker.succeed(401, "{\"message\":\"Unauthorized\"}");

        GnmRetryExecutor.ExecutionResult result =
                executor(invoker, 3).executeLaunch("org-1", "{}");

        assertEquals(
                GnmRetryExecutor.Outcome.PERMANENT_FAILURE,
                result.outcome());

        assertEquals(1, invoker.invocations);
    }

    @Test
    void shouldNotRetryHttp408() {
        assertAmbiguousHttp(408, "{}");
    }

    @Test
    void shouldNotRetryHttp500() {
        assertAmbiguousHttp(500, "{}");
    }

    @Test
    void shouldNotRetryDuplicateCloseSemantic() {
        assertAmbiguousHttp(
                400,
                "{\"message\":\"Invalid Incident operation.\"}");
    }

    @Test
    void shouldRetryConnectException() {
        FakeInvoker invoker = new FakeInvoker();

        invoker.fail(
                new ConnectException("Connection refused"));

        invoker.succeed(200, "{\"message\":\"OK\"}");

        GnmRetryExecutor.ExecutionResult result =
                executor(invoker, 3).executeLaunch("org-1", "{}");

        assertEquals(GnmRetryExecutor.Outcome.SUCCESS, result.outcome());
        assertEquals(2, result.attempts());
        assertEquals(2, invoker.invocations);
    }

    @Test
    void shouldRetryConnectTimeout() {
        FakeInvoker invoker = new FakeInvoker();

        invoker.fail(
                new HttpConnectTimeoutException(
                        "connect timeout"));

        invoker.succeed(200, "{\"message\":\"OK\"}");

        GnmRetryExecutor.ExecutionResult result =
                executor(invoker, 3).executeLaunch("org-1", "{}");

        assertEquals(GnmRetryExecutor.Outcome.SUCCESS, result.outcome());
        assertEquals(2, invoker.invocations);
    }

    @Test
    void shouldNotRetryReadTimeout() {
        FakeInvoker invoker = new FakeInvoker();

        SocketTimeoutException failure =
                new SocketTimeoutException("read timeout");

        invoker.fail(failure);

        GnmRetryExecutor.ExecutionResult result =
                executor(invoker, 3).executeLaunch("org-1", "{}");

        assertEquals(
                GnmRetryExecutor.Outcome.RECONCILIATION_REQUIRED,
                result.outcome());

        assertSame(failure, result.failure());
        assertEquals(1, invoker.invocations);
    }

    @Test
    void shouldNotRetryHttpTimeout() {
        FakeInvoker invoker = new FakeInvoker();

        HttpTimeoutException failure =
                new HttpTimeoutException("timeout");

        invoker.fail(failure);

        GnmRetryExecutor.ExecutionResult result =
                executor(invoker, 3).executeLaunch("org-1", "{}");

        assertEquals(
                GnmRetryExecutor.Outcome.RECONCILIATION_REQUIRED,
                result.outcome());

        assertEquals(1, invoker.invocations);
    }

    @Test
    void shouldNotRetryGenericTimeout() {
        FakeInvoker invoker = new FakeInvoker();

        TimeoutException failure =
                new TimeoutException("timeout");

        invoker.fail(failure);

        GnmRetryExecutor.ExecutionResult result =
                executor(invoker, 3).executeLaunch("org-1", "{}");

        assertEquals(
                GnmRetryExecutor.Outcome.RECONCILIATION_REQUIRED,
                result.outcome());

        assertEquals(1, invoker.invocations);
    }

    @Test
    void shouldNotRetryUnknownException() {
        FakeInvoker invoker = new FakeInvoker();

        IllegalStateException failure =
                new IllegalStateException("unexpected");

        invoker.fail(failure);

        GnmRetryExecutor.ExecutionResult result =
                executor(invoker, 3).executeLaunch("org-1", "{}");

        assertEquals(
                GnmRetryExecutor.Outcome.PERMANENT_FAILURE,
                result.outcome());

        assertSame(failure, result.failure());
        assertEquals(1, invoker.invocations);
    }

    @Test
    void shouldExecuteExactlyOnceWhenMaxAttemptsIsOne() {
        FakeInvoker invoker = new FakeInvoker();
        invoker.succeed(429, "{\"message\":\"final\"}");

        GnmRetryExecutor.ExecutionResult result =
                executor(invoker, 1).executeLaunch("org-1", "{}");

        assertEquals(
                GnmRetryExecutor.Outcome.RETRY_EXHAUSTED,
                result.outcome());

        assertEquals(1, result.attempts());
        assertEquals(1, invoker.invocations);
    }

    @Test
    void shouldPreserveFinalHttpResult() {
        FakeInvoker invoker = new FakeInvoker();

        invoker.succeed(
                401,
                "{\"status\":401,\"message\":\"Unauthorized\"}");

        GnmRetryExecutor.ExecutionResult result =
                executor(invoker, 3).executeLaunch("org-1", "{}");

        assertNotNull(result.httpResult());

        assertEquals(
                "{\"status\":401,\"message\":\"Unauthorized\"}",
                result.httpResult().responseBody());
    }

    @Test
    void shouldSurfaceAmbiguousForReconciliation() {
        FakeInvoker invoker = new FakeInvoker();
        invoker.succeed(500, "{\"message\":\"unknown state\"}");

        GnmRetryExecutor.ExecutionResult result =
                executor(invoker, 3).executeLaunch("org-1", "{}");

        assertTrue(result.reconciliationRequired());
        assertFalse(result.successful());
        assertEquals(1, invoker.invocations);
    }

    @Test
    void shouldRejectInvalidMaxAttempts() {
        FakeInvoker invoker = new FakeInvoker();

        assertThrows(
                IllegalArgumentException.class,
                () -> new GnmRetryExecutor(
                        invoker,
                        new GnmErrorClassifier(),
                        0,
                        0));
    }

    @Test
    void shouldRejectNegativeDelay() {
        FakeInvoker invoker = new FakeInvoker();

        assertThrows(
                IllegalArgumentException.class,
                () -> new GnmRetryExecutor(
                        invoker,
                        new GnmErrorClassifier(),
                        3,
                        -1));
    }

    private void assertAmbiguousHttp(
            int status,
            String body) {

        FakeInvoker invoker = new FakeInvoker();
        invoker.succeed(status, body);

        GnmRetryExecutor.ExecutionResult result =
                executor(invoker, 3).executeLaunch("org-1", "{}");

        assertEquals(
                GnmRetryExecutor.Outcome.RECONCILIATION_REQUIRED,
                result.outcome());

        assertEquals(1, result.attempts());
        assertEquals(1, invoker.invocations);
        assertNotNull(result.httpResult());
    }

    private GnmRetryExecutor executor(
            GnmHttpInvoker invoker,
            int maxAttempts) {

        return new GnmRetryExecutor(
                invoker,
                new GnmErrorClassifier(),
                maxAttempts,
                0);
    }

    private static class FakeInvoker
            implements GnmHttpInvoker {

        private final Queue<Object> outcomes =
                new ArrayDeque<>();

        private int invocations;

        void fail(Exception exception) {
            outcomes.add(exception);
        }

        void succeed(
                int status,
                String body) {

            outcomes.add(
                    new HttpResult(status, body));
        }

        @Override
        public HttpResult launch(
                String organizationId,
                String requestBody)
                throws Exception {

            invocations++;

            Object outcome = outcomes.remove();

            if (outcome instanceof Exception exception) {
                throw exception;
            }

            return (HttpResult) outcome;
        }

        @Override
        public HttpResult getIncident(
                String organizationId,
                String incidentId
        ) {
            throw new AssertionError(
                    "GET incident must not be called by Launch retry tests"
            );
        }

}
}
