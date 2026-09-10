package com.eventmanagement.integration;

import org.apache.camel.Exchange;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.support.DefaultExchange;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class IntegrationProviderRouterTest {

    private final IntegrationProviderRouter router =
            new IntegrationProviderRouter();

    @Test
    void shouldResolveServiceNowProvider() {

        Exchange exchange = exchange("SERVICENOW");

        router.process(exchange);

        assertEquals(
                "SERVICENOW",
                exchange.getProperty(
                        IntegrationProviderRouter.PROVIDER_PROPERTY,
                        String.class
                )
        );
    }

    @Test
    void shouldResolveGnmProvider() {

        Exchange exchange = exchange("GNM");

        router.process(exchange);

        assertEquals(
                "GNM",
                exchange.getProperty(
                        IntegrationProviderRouter.PROVIDER_PROPERTY,
                        String.class
                )
        );
    }

    @Test
    void shouldRejectUnknownProvider() {

        Exchange exchange = exchange("UNKNOWN");

        assertThrows(
                IllegalArgumentException.class,
                () -> router.process(exchange)
        );
    }

    private Exchange exchange(String integrationType) {

        Exchange exchange =
                new DefaultExchange(new DefaultCamelContext());

        exchange.setProperty(
                "integrationType",
                integrationType
        );

        return exchange;
    }
}
