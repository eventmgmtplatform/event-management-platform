package com.eventmanagement.integration;

import static org.junit.jupiter.api.Assertions.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import org.apache.camel.Exchange;
import org.apache.camel.support.DefaultExchange;
import org.apache.camel.impl.DefaultCamelContext;
import org.junit.jupiter.api.Test;

class GnmLaunchExecutionProcessorTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Test
    void shouldInvokeLaunchExactlyOnceUsingPreparedProviderState() throws Exception {
        CountingHttpInvoker invoker = new CountingHttpInvoker();

        GnmErrorClassifier classifier = new GnmErrorClassifier();

        GnmRetryExecutor retryExecutor =
                new GnmRetryExecutor(
                        invoker,
                        classifier,
                        1,
                        0
                );

        GnmLaunchExecutionProcessor processor =
                new GnmLaunchExecutionProcessor(retryExecutor);

        try (DefaultCamelContext camel = new DefaultCamelContext()) {
            Exchange exchange = new DefaultExchange(camel);

            exchange.setProperty(
                    "gnmProviderContext",
                    new EverbridgeProviderContext(
                            "453003085618991",
                            "8101205968426652",
                            "GSMA_C00_HV",
                            List.of(
                                    "3760896702677017",
                                    "367116624592928"
                            ),
                            15
                    )
            );

            exchange.setProperty(
                    "gnmProviderRequest",
                    MAPPER.readTree("""
                        {
                          "incidentAction":"Launch",
                          "name":"VITRO test"
                        }
                        """)
            );

            processor.process(exchange);

            assertEquals(1, invoker.launchCalls);

            GnmRetryExecutor.ExecutionResult result =
                    exchange.getProperty(
                            "gnmLaunchExecutionResult",
                            GnmRetryExecutor.ExecutionResult.class
                    );

            assertNotNull(result);
            assertEquals(
                    GnmRetryExecutor.Outcome.SUCCESS,
                    result.outcome()
            );
            assertEquals(1, result.attempts());
            assertEquals(200, result.httpResult().httpStatus());
        }
    }

    @Test
    void shouldFailBeforeTransportWhenProviderContextIsMissing() throws Exception {
        CountingHttpInvoker invoker = new CountingHttpInvoker();

        GnmRetryExecutor retryExecutor =
                new GnmRetryExecutor(
                        invoker,
                        new GnmErrorClassifier(),
                        1,
                        0
                );

        GnmLaunchExecutionProcessor processor =
                new GnmLaunchExecutionProcessor(retryExecutor);

        try (DefaultCamelContext camel = new DefaultCamelContext()) {
            Exchange exchange = new DefaultExchange(camel);

            exchange.setProperty(
                    "gnmProviderRequest",
                    MAPPER.readTree("""
                        {"incidentAction":"Launch"}
                        """)
            );

            IllegalStateException failure =
                    assertThrows(
                            IllegalStateException.class,
                            () -> processor.process(exchange)
                    );

            assertTrue(
                    failure.getMessage().contains("gnmProviderContext")
            );

            assertEquals(0, invoker.launchCalls);
        }
    }

    @Test
    void shouldFailBeforeTransportWhenProviderRequestIsMissing() throws Exception {
        CountingHttpInvoker invoker = new CountingHttpInvoker();

        GnmRetryExecutor retryExecutor =
                new GnmRetryExecutor(
                        invoker,
                        new GnmErrorClassifier(),
                        1,
                        0
                );

        GnmLaunchExecutionProcessor processor =
                new GnmLaunchExecutionProcessor(retryExecutor);

        try (DefaultCamelContext camel = new DefaultCamelContext()) {
            Exchange exchange = new DefaultExchange(camel);

            exchange.setProperty(
                    "gnmProviderContext",
                    new EverbridgeProviderContext(
                            "453003085618991",
                            "8101205968426652",
                            "GSMA_C00_HV",
                            List.of("3760896702677017"),
                            15
                    )
            );

            assertThrows(
                    IllegalStateException.class,
                    () -> processor.process(exchange)
            );

            assertEquals(0, invoker.launchCalls);
        }
    }

    private static final class CountingHttpInvoker
            implements GnmHttpInvoker {

        private int launchCalls;

        @Override
        public HttpResult launch(
                String organizationId,
                String requestBody
        ) {
            launchCalls++;

            return new HttpResult(
                    200,
                    """
                    {
                      "status":200,
                      "message":"OK",
                      "result":{
                        "id":"2713046552385248"
                      }
                    }
                    """
            );
        }

        @Override
        public HttpResult getIncident(
                String organizationId,
                String incidentId
        ) {
            throw new AssertionError(
                    "GET incident must not be called by Launch execution tests"
            );
        }

}
}
