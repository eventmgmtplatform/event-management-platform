package com.eventmanagement.integration;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.support.DefaultExchange;
import org.junit.jupiter.api.Test;
import java.nio.file.*;
import static org.junit.jupiter.api.Assertions.*;
/** Cross-component fixture; calls validators only, never provider execution or Kafka. */
class ProcessorGroupCommandContractTest {
    @Test void acceptsProcessorGroupEnvelopeAndCanonicalServiceNowPayload()throws Exception {
        var mapper=new ObjectMapper();String json=Files.readString(Path.of("../../testing/services/event-processor/resources/contracts/processor-group-command.json"));
        try(var camel=new DefaultCamelContext()) {
            var exchange=new DefaultExchange(camel);exchange.getMessage().setBody(json);
            new IntegrationCommandProcessor(mapper).process(exchange);new ServiceNowCommandProcessor().process(exchange);
            assertEquals("router-1",exchange.getProperty("resource"));assertEquals("Router unavailable",exchange.getProperty("summary"));assertEquals(3,exchange.getProperty("severity"));
            assertEquals(mapper.readTree(json),exchange.getProperty("originalIntegrationCommand"));
        }
    }
}
