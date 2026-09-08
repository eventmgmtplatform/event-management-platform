package com.eventmanagement.integration;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import org.apache.camel.Exchange;
import org.apache.camel.ProducerTemplate;
import org.eclipse.microprofile.config.inject.ConfigProperty;

@Named("serviceNowHttpInvoker")
@ApplicationScoped
public class CamelServiceNowHttpInvoker
        implements ServiceNowHttpInvoker {

    private final ProducerTemplate producerTemplate;
    private final String endpointUri;
    private final String updateBase;
    private final long timeoutMs;

    @Inject
    public CamelServiceNowHttpInvoker(
            ProducerTemplate producerTemplate,
            @ConfigProperty(
                    name = "integration.servicenow.base-url"
            )
            String baseUrl,
            @ConfigProperty(
                    name = "integration.servicenow.create-ticket-path"
            )
            String createTicketPath,
            @ConfigProperty(
                    name = "integration.servicenow.timeout-ms"
            )
            long timeoutMs
    ) {
        this.producerTemplate = producerTemplate;
        this.updateBase = baseUrl + createTicketPath;
        this.timeoutMs = timeoutMs;

        this.endpointUri =
                baseUrl +
                createTicketPath +
                "?throwExceptionOnFailure=true&automaticRetriesDisabled=true" +
                "&connectTimeout=" + timeoutMs +
                "&responseTimeout=" + timeoutMs;
    }

    @Override
    public HttpResult invoke(String requestBody)
            throws Exception {

        Exchange response = producerTemplate.request(
                endpointUri,
                request -> {
                    request.getMessage().setBody(requestBody);
                    request.getMessage().setHeader(
                            Exchange.CONTENT_TYPE,
                            "application/json"
                    );
                    request.getMessage().setHeader(
                            Exchange.HTTP_METHOD,
                            "POST"
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

        String responseBody =
                response.getMessage().getBody(String.class);

        return new HttpResult(
                httpStatus,
                responseBody
        );
    }
    @Override
    public HttpResult updateTicket(String sysId, String requestBody) throws Exception {
        if (sysId == null || !sysId.matches("[A-Za-z0-9_-]{1,100}")) throw new IllegalArgumentException("Invalid ServiceNow sysId");
        Exchange response = producerTemplate.request(updateBase + "/" + sysId
                + "?throwExceptionOnFailure=true&automaticRetriesDisabled=true&connectTimeout=" + timeoutMs + "&responseTimeout=" + timeoutMs,
                request -> {
                    request.getMessage().setBody(requestBody);
                    request.getMessage().setHeader(Exchange.CONTENT_TYPE, "application/json");
                    request.getMessage().setHeader(Exchange.HTTP_METHOD, "PATCH");
                });
        Exception error = response.getException();
        if (error == null) error = response.getProperty(Exchange.EXCEPTION_CAUGHT, Exception.class);
        if (error != null) throw error;
        return new HttpResult(response.getMessage().getHeader(Exchange.HTTP_RESPONSE_CODE, 0, Integer.class), response.getMessage().getBody(String.class));
    }
}
