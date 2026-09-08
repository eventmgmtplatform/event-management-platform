package com.eventmanagement.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.apache.camel.Exchange;
import org.apache.camel.ProducerTemplate;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

@ApplicationScoped
public class CamelServiceNowLookupClient
        implements ServiceNowLookupClient {

    private final ProducerTemplate producerTemplate;
    private final ObjectMapper objectMapper;
    private final String endpointUri;

    @Inject
    public CamelServiceNowLookupClient(
            ProducerTemplate producerTemplate,
            ObjectMapper objectMapper,
            @ConfigProperty(name = "integration.servicenow.base-url")
            String baseUrl,
            @ConfigProperty(name = "integration.servicenow.lookup-ticket-path")
            String lookupTicketPath,
            @ConfigProperty(name = "integration.servicenow.timeout-ms")
            long timeoutMs
    ) {
        this.producerTemplate = producerTemplate;
        this.objectMapper = objectMapper;
        this.endpointUri =
                baseUrl + lookupTicketPath +
                "?throwExceptionOnFailure=true" +
                "&automaticRetriesDisabled=true" +
                "&connectTimeout=" + timeoutMs +
                "&responseTimeout=" + timeoutMs;
    }

    @Override
    public LookupResult findByEventId(String eventId)
            throws Exception {
        if (eventId == null || eventId.isBlank()) {
            throw new IllegalArgumentException(
                    "eventId is required for ServiceNow reconciliation"
            );
        }

        return find("u_event_id=" + eventId.trim(), eventId);
    }

    @Override
    public LookupResult findByTicketNumber(String number) throws Exception {
        if (number == null || !number.matches("[A-Za-z0-9_-]{1,100}")) throw new IllegalArgumentException("Invalid ticket number");
        return find("number=" + number, number);
    }

    private LookupResult find(String serviceNowQuery, String eventId) throws Exception {
        String httpQuery =
                "sysparm_query=" + URLEncoder.encode(
                        serviceNowQuery,
                        StandardCharsets.UTF_8
                ) + "&sysparm_limit=2";

        Exchange response = producerTemplate.request(
                endpointUri,
                request -> {
                    request.getMessage().setHeader(
                            Exchange.HTTP_METHOD,
                            "GET"
                    );
                    request.getMessage().setHeader(
                            Exchange.HTTP_QUERY,
                            httpQuery
                    );
                }
        );

        Exception exception = response.getException();
        if (exception == null) {
            exception = response.getProperty(
                    Exchange.EXCEPTION_CAUGHT,
                    Exception.class
            );
        }
        if (exception != null) {
            throw exception;
        }

        int httpStatus = response.getMessage().getHeader(
                Exchange.HTTP_RESPONSE_CODE,
                0,
                Integer.class
        );
        if (httpStatus < 200 || httpStatus >= 300) {
            throw new IllegalStateException(
                    "ServiceNow lookup returned HTTP " + httpStatus
            );
        }

        String responseBody =
                response.getMessage().getBody(String.class);
        JsonNode root = objectMapper.readTree(responseBody);
        JsonNode results = root == null ? null : root.get("result");

        if (results == null || !results.isArray()) {
            throw new IllegalStateException(
                    "ServiceNow lookup response has no result array"
            );
        }
        if (results.isEmpty()) {
            return new LookupResult(Status.NOT_FOUND, null);
        }
        if (results.size() != 1) {
            throw new IllegalStateException(
                    "ServiceNow lookup is ambiguous for eventId: " + eventId
            );
        }

        JsonNode ticket = results.get(0);
        requireTicketField(ticket, "sys_id", eventId);
        requireTicketField(ticket, "number", eventId);
        return new LookupResult(Status.FOUND, ticket.deepCopy());
    }

    private void requireTicketField(
            JsonNode ticket,
            String field,
            String eventId
    ) {
        if (ticket == null || !ticket.isObject() ||
                ticket.path(field).asText("").isBlank()) {
            throw new IllegalStateException(
                    "ServiceNow lookup ticket has no " + field +
                    " for eventId: " + eventId
            );
        }
    }
}
