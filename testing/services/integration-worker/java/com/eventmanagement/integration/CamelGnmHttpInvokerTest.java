package com.eventmanagement.integration;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.apache.camel.CamelContext;
import org.apache.camel.impl.DefaultCamelContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class CamelGnmHttpInvokerTest {

    private CamelContext camelContext;

    @BeforeEach
    void setUp() throws Exception {
        camelContext = new DefaultCamelContext();
        camelContext.start();
    }

    @AfterEach
    void tearDown() throws Exception {
        if (camelContext != null) {
            camelContext.stop();
        }
    }

    @Test
    void shouldRejectMissingOrganizationId() {
        CamelGnmHttpInvoker invoker =
                new CamelGnmHttpInvoker(
                        camelContext,
                        "http://127.0.0.1:8182",
                        "/rest/incidents",
                        20000);

        IllegalArgumentException error =
                assertThrows(
                        IllegalArgumentException.class,
                        () -> invoker.launch(
                                " ",
                                "{\"incidentAction\":\"Launch\"}"));

        assertTrue(
                error.getMessage().contains("organizationId"));
    }

    @Test
    void shouldRejectMissingRequestBody() {
        CamelGnmHttpInvoker invoker =
                new CamelGnmHttpInvoker(
                        camelContext,
                        "http://127.0.0.1:8182",
                        "/rest/incidents",
                        20000);

        IllegalArgumentException error =
                assertThrows(
                        IllegalArgumentException.class,
                        () -> invoker.launch(
                                "453003085618991",
                                " "));

        assertTrue(
                error.getMessage().contains("requestBody"));
    }

    @Test
    void shouldNormalizeConfigurationThroughSuccessfulMockLaunch()
            throws Exception {

        CamelGnmHttpInvoker invoker =
                new CamelGnmHttpInvoker(
                        camelContext,
                        "http://127.0.0.1:8182/",
                        "rest/incidents/",
                        20000);

        String certifiedLaunchRequest =
                loadClasspathResource(
                        "/gnm/vitro-everbridge-launch-golden.json");

        GnmHttpInvoker.HttpResult result =
                invoker.launch(
                        "453003085618991",
                        certifiedLaunchRequest);

        assertEquals(200, result.httpStatus());
        assertTrue(result.responseBody().contains(
                "2713046552385248"));
    }

    @Test
    void shouldExposeProviderHttpStatusWithoutCamelFailure()
            throws Exception {

        CamelGnmHttpInvoker invoker =
                new CamelGnmHttpInvoker(
                        camelContext,
                        "http://127.0.0.1:8182",
                        "/rest/incidents",
                        20000);

        GnmHttpInvoker.HttpResult result =
                invoker.launch(
                        "unknown-organization",
                        """
                        {
                          "incidentAction": "Launch"
                        }
                        """);

        assertTrue(result.httpStatus() >= 400);
    }

    private String loadClasspathResource(String resource)
            throws Exception {

        try (InputStream input =
                CamelGnmHttpInvokerTest.class
                        .getResourceAsStream(resource)) {

            if (input == null) {
                throw new IllegalStateException(
                        "Test resource not found: " + resource);
            }

            return new String(
                    input.readAllBytes(),
                    StandardCharsets.UTF_8);
        }
    }

}
