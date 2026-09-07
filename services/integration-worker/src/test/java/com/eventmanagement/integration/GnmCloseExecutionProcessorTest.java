package com.eventmanagement.integration;

import static org.junit.jupiter.api.Assertions.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import org.apache.camel.Exchange;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.support.DefaultExchange;
import org.junit.jupiter.api.Test;

class GnmCloseExecutionProcessorTest {

    private static final ObjectMapper MAPPER =
            new ObjectMapper();

    @Test
    void shouldInvokeCloseExactlyOnce()
            throws Exception {

        CountingInvoker invoker =
                new CountingInvoker();

        GnmRetryExecutor executor =
                new GnmRetryExecutor(
                        invoker,
                        new GnmErrorClassifier(),
                        3,
                        0
                );

        GnmCloseExecutionProcessor processor =
                new GnmCloseExecutionProcessor(
                        executor
                );

        try (DefaultCamelContext camel =
                     new DefaultCamelContext()) {

            Exchange exchange =
                    new DefaultExchange(camel);

            exchange.setProperty(
                    "gnmProviderContext",
                    context()
            );

            exchange.setProperty(
                    "gnmIncidentId",
                    "2713046552385248"
            );

            exchange.setProperty(
                    "gnmProviderRequest",
                    MAPPER.readTree("""
                    {
                      "incidentAction":"CloseWithNotification"
                    }
                    """)
            );

            processor.process(exchange);

            assertEquals(1, invoker.closeCalls);

            GnmRetryExecutor.ExecutionResult result =
                    exchange.getProperty(
                            "gnmCloseExecutionResult",
                            GnmRetryExecutor.ExecutionResult.class
                    );

            assertNotNull(result);
            assertEquals(
                    GnmRetryExecutor.Outcome.SUCCESS,
                    result.outcome()
            );
        }
    }

    @Test
    void shouldRejectMissingIncidentBeforePut()
            throws Exception {

        CountingInvoker invoker =
                new CountingInvoker();

        GnmCloseExecutionProcessor processor =
                new GnmCloseExecutionProcessor(
                        new GnmRetryExecutor(
                                invoker,
                                new GnmErrorClassifier(),
                                3,
                                0
                        )
                );

        try (DefaultCamelContext camel =
                     new DefaultCamelContext()) {

            Exchange exchange =
                    new DefaultExchange(camel);

            exchange.setProperty(
                    "gnmProviderContext",
                    context()
            );

            exchange.setProperty(
                    "gnmProviderRequest",
                    MAPPER.createObjectNode()
            );

            assertThrows(
                    IllegalStateException.class,
                    () -> processor.process(exchange)
            );

            assertEquals(0, invoker.closeCalls);
        }
    }

    private static EverbridgeProviderContext context() {
        return new EverbridgeProviderContext(
                "453003085618991",
                "8101205968426652",
                "GSMA_C00_HV",
                List.of("3760896702677017"),
                15
        );
    }

    private static final class CountingInvoker
            implements GnmHttpInvoker {

        private int closeCalls;

        @Override
        public HttpResult launch(
                String organizationId,
                String requestBody
        ) {
            throw new AssertionError(
                    "Launch must not be called"
            );
        }

        @Override
        public HttpResult closeIncident(
                String organizationId,
                String incidentId,
                String requestBody
        ) {
            closeCalls++;

            assertEquals(
                    "453003085618991",
                    organizationId
            );

            assertEquals(
                    "2713046552385248",
                    incidentId
            );

            return new HttpResult(
                    200,
                    """
                    {
                      "status":200,
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
                    "GET must not be called by Close execution"
            );
        }
    }
}
