package com.eventmanagement.integration;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.ProducerTemplate;
import org.eclipse.microprofile.config.inject.ConfigProperty;

@ApplicationScoped
public class CamelGnmHttpInvoker implements GnmHttpInvoker {

    private final ProducerTemplate producerTemplate;
    private final String baseUrl;
    private final String incidentsPath;
    private final int timeoutMs;

    @Inject
    public CamelGnmHttpInvoker(
            CamelContext camelContext,
            @ConfigProperty(name = "integration.gnm.base-url")
            String baseUrl,
            @ConfigProperty(
                    name = "integration.gnm.incidents-path",
                    defaultValue = "/rest/incidents")
            String incidentsPath,
            @ConfigProperty(
                    name = "integration.gnm.timeout-ms",
                    defaultValue = "20000")
            int timeoutMs) {

        this.producerTemplate = camelContext.createProducerTemplate();
        this.baseUrl = normalizeBaseUrl(baseUrl);
        this.incidentsPath = normalizePath(incidentsPath);
        this.timeoutMs = timeoutMs;
    }

    @Override
    public HttpResult launch(
            String organizationId,
            String requestBody) throws Exception {

        if (organizationId == null || organizationId.isBlank()) {
            throw new IllegalArgumentException(
                    "Everbridge organizationId is required");
        }

        if (requestBody == null || requestBody.isBlank()) {
            throw new IllegalArgumentException(
                    "Everbridge requestBody is required");
        }

        String endpoint =
                baseUrl
                        + incidentsPath
                        + "/"
                        + organizationId
                        + "?throwExceptionOnFailure=false"
                        + "&connectTimeout="
                        + timeoutMs
                        + "&responseTimeout="
                        + timeoutMs;

        Exchange response =
                producerTemplate.request(
                        endpoint,
                        exchange -> {
                            exchange.getMessage().setHeader(
                                    Exchange.HTTP_METHOD,
                                    "POST");

                            exchange.getMessage().setHeader(
                                    Exchange.CONTENT_TYPE,
                                    "application/json");

                            exchange.getMessage().setHeader(
                                    "Accept",
                                    "application/json");

                            exchange.getMessage().setBody(requestBody);
                        });

        Integer status =
                response.getMessage().getHeader(
                        Exchange.HTTP_RESPONSE_CODE,
                        Integer.class);

        String body =
                response.getMessage().getBody(String.class);

        if (status == null) {
            throw new IllegalStateException(
                    "GNM provider response did not contain HTTP status");
        }

        return new HttpResult(status, body);
    }

    @Override
    public HttpResult getIncident(
            String organizationId,
            String incidentId) throws Exception {

        if (organizationId == null || organizationId.isBlank()) {
            throw new IllegalArgumentException(
                    "Everbridge organizationId is required");
        }

        if (incidentId == null || incidentId.isBlank()) {
            throw new IllegalArgumentException(
                    "Everbridge incidentId is required");
        }

        String endpoint =
                baseUrl
                        + incidentsPath
                        + "/"
                        + organizationId
                        + "/"
                        + incidentId
                        + "?throwExceptionOnFailure=false"
                        + "&connectTimeout="
                        + timeoutMs
                        + "&responseTimeout="
                        + timeoutMs;

        Exchange response =
                producerTemplate.request(
                        endpoint,
                        exchange -> {
                            exchange.getMessage().setHeader(
                                    Exchange.HTTP_METHOD,
                                    "GET");

                            exchange.getMessage().setHeader(
                                    "Accept",
                                    "application/json");
                        });

        Integer status =
                response.getMessage().getHeader(
                        Exchange.HTTP_RESPONSE_CODE,
                        Integer.class);

        String body =
                response.getMessage().getBody(String.class);

        if (status == null) {
            throw new IllegalStateException(
                    "GNM provider response did not contain HTTP status");
        }

        return new HttpResult(status, body);
    }

    @Override
    public HttpResult closeIncident(
            String organizationId,
            String incidentId,
            String requestBody) throws Exception {

        if (organizationId == null || organizationId.isBlank()) {
            throw new IllegalArgumentException(
                    "Everbridge organizationId is required");
        }

        if (incidentId == null || incidentId.isBlank()) {
            throw new IllegalArgumentException(
                    "Everbridge incidentId is required");
        }

        if (requestBody == null || requestBody.isBlank()) {
            throw new IllegalArgumentException(
                    "Everbridge requestBody is required");
        }

        String endpoint =
                baseUrl
                        + incidentsPath
                        + "/"
                        + organizationId
                        + "/"
                        + incidentId
                        + "?throwExceptionOnFailure=false"
                        + "&connectTimeout="
                        + timeoutMs
                        + "&responseTimeout="
                        + timeoutMs;

        Exchange response =
                producerTemplate.request(
                        endpoint,
                        exchange -> {
                            exchange.getMessage().setHeader(
                                    Exchange.HTTP_METHOD,
                                    "PUT");

                            exchange.getMessage().setHeader(
                                    Exchange.CONTENT_TYPE,
                                    "application/json");

                            exchange.getMessage().setHeader(
                                    "Accept",
                                    "application/json");

                            exchange.getMessage().setBody(requestBody);
                        });

        Integer status =
                response.getMessage().getHeader(
                        Exchange.HTTP_RESPONSE_CODE,
                        Integer.class);

        String body =
                response.getMessage().getBody(String.class);

        if (status == null) {
            throw new IllegalStateException(
                    "GNM provider response did not contain HTTP status");
        }

        return new HttpResult(status, body);
    }


    private static String normalizeBaseUrl(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(
                    "integration.gnm.base-url is required");
        }

        String normalized = value.trim();

        while (normalized.endsWith("/")) {
            normalized =
                    normalized.substring(
                            0,
                            normalized.length() - 1);
        }

        return normalized;
    }

    private static String normalizePath(String value) {
        if (value == null || value.isBlank()) {
            return "/rest/incidents";
        }

        String normalized = value.trim();

        if (!normalized.startsWith("/")) {
            normalized = "/" + normalized;
        }

        while (normalized.endsWith("/")
                && normalized.length() > 1) {

            normalized =
                    normalized.substring(
                            0,
                            normalized.length() - 1);
        }

        return normalized;
    }
}
