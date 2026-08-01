package com.eventmanagement.gateway;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.jboss.logging.Logger;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;

@Named("gatewayExceptionHandler")
@ApplicationScoped
public class GatewayExceptionHandler implements Processor {

    private static final Logger LOG =
            Logger.getLogger(GatewayExceptionHandler.class);

    private final ObjectMapper objectMapper;

    @Inject
    public GatewayExceptionHandler(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public void process(Exchange exchange) throws Exception {

        Exception exception = exchange.getProperty(
                Exchange.EXCEPTION_CAUGHT,
                Exception.class
        );

        String message =
                exception != null && exception.getMessage() != null
                        ? exception.getMessage()
                        : "Error interno del Event Gateway";

        ObjectNode response =
                objectMapper.createObjectNode();

        response.put("accepted", false);
        response.put("errorCode", "INVALID_EVENT");
        response.put("message", message);

        response.put(
                "timestamp",
                OffsetDateTime.now(ZoneOffset.UTC).toString()
        );

        exchange.getMessage().setHeader(
                Exchange.HTTP_RESPONSE_CODE,
                400
        );

        exchange.getMessage().setHeader(
                Exchange.CONTENT_TYPE,
                "application/json"
        );

        exchange.getMessage().setBody(
                objectMapper.writeValueAsString(response)
        );

        LOG.warnv(
                "Evento rechazado: {0}",
                message
        );
    }
}
