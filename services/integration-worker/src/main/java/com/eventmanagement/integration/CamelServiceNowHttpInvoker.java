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
}
