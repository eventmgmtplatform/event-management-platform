package com.eventmanagement.integration.cacf;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;

@Named("cacfCommandProcessor") @ApplicationScoped
public class CacfCommandProcessor implements Processor {
    private final AutomationRepository repository;private final CacfSettings settings;
    @Inject public CacfCommandProcessor(AutomationRepository repository,CacfSettings settings){this.repository=repository;this.settings=settings;}
    public void process(Exchange exchange) throws Exception {
        if(!settings.enabled)throw new IllegalStateException("CACF is disabled");
        if(!"AUTOMATION_REQUESTED".equals(exchange.getProperty("operation",String.class)))throw new IllegalArgumentException("Unsupported CACF operation");
        JsonNode payload=exchange.getProperty("integrationPayload",JsonNode.class);
        var request=AutomationRequest.parse(payload,exchange.getProperty("commandId",String.class),exchange.getProperty("eventKey",String.class),settings.resultTimeout);
        if(!request.eventId().equals(exchange.getProperty("eventId",String.class)) || !request.customerCode().equals(exchange.getProperty("tenant",String.class)))
            throw new IllegalArgumentException("CACF envelope and payload identities disagree");
        repository.accept(request);
    }
}
