package com.eventmanagement.integration;

import org.apache.camel.Exchange;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.support.DefaultExchange;
import org.junit.jupiter.api.Test;

import java.net.ConnectException;
import java.util.ArrayDeque;
import java.util.Queue;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ServiceNowRetryExecutorTest {

    @Test
    void shouldSucceedOnThirdAttempt()
            throws Exception {

        FakeInvoker invoker = new FakeInvoker();

        invoker.fail(new ConnectException("Attempt one"));
        invoker.fail(new ConnectException("Attempt two"));
        invoker.succeed(201, "{\"result\":{}}");

        ServiceNowRetryExecutor executor =
                executor(invoker, 3);

        Exchange exchange = exchange();

        executor.process(exchange);

        assertEquals(3, invoker.invocations);
        assertEquals(
                3,
                exchange.getProperty(
                        "integrationAttempt",
                        Integer.class
                )
        );
        assertEquals(
                201,
                exchange.getMessage().getHeader(
                        Exchange.HTTP_RESPONSE_CODE,
                        Integer.class
                )
        );
    }

    @Test
    void shouldNotRetryPermanentError() {

        FakeInvoker invoker = new FakeInvoker();

        invoker.fail(
                new IllegalArgumentException(
                        "Permanent validation failure"
                )
        );

        ServiceNowRetryExecutor executor =
                executor(invoker, 3);

        Exchange exchange = exchange();

        assertThrows(
                IllegalArgumentException.class,
                () -> executor.process(exchange)
        );

        assertEquals(1, invoker.invocations);
        assertEquals(
                1,
                exchange.getProperty(
                        "integrationAttempt",
                        Integer.class
                )
        );
    }

    @Test
    void shouldStopAfterMaximumAttempts() {

        FakeInvoker invoker = new FakeInvoker();

        invoker.fail(new ConnectException("Attempt one"));
        invoker.fail(new ConnectException("Attempt two"));
        invoker.fail(new ConnectException("Attempt three"));

        ServiceNowRetryExecutor executor =
                executor(invoker, 3);

        Exchange exchange = exchange();

        assertThrows(
                ConnectException.class,
                () -> executor.process(exchange)
        );

        assertEquals(3, invoker.invocations);
        assertEquals(
                3,
                exchange.getProperty(
                        "integrationAttempt",
                        Integer.class
                )
        );
    }

    private ServiceNowRetryExecutor executor(
            ServiceNowHttpInvoker invoker,
            int maxAttempts
    ) {

        return new ServiceNowRetryExecutor(
                invoker,
                new ServiceNowErrorClassifier(),
                maxAttempts,
                0
        );
    }

    private Exchange exchange() {

        Exchange exchange =
                new DefaultExchange(new DefaultCamelContext());

        exchange.setProperty("commandId", "cmd-retry-001");
        exchange.getMessage().setBody(
                "{\"short_description\":\"Retry test\"}"
        );

        return exchange;
    }

    private static class FakeInvoker
            implements ServiceNowHttpInvoker {

        private final Queue<Object> outcomes =
                new ArrayDeque<>();

        private int invocations;

        void fail(Exception exception) {
            outcomes.add(exception);
        }

        void succeed(int status, String body) {
            outcomes.add(new HttpResult(status, body));
        }

        @Override
        public HttpResult invoke(String requestBody)
                throws Exception {

            invocations++;

            Object outcome = outcomes.remove();

            if (outcome instanceof Exception exception) {
                throw exception;
            }

            return (HttpResult) outcome;
        }
    }
}
