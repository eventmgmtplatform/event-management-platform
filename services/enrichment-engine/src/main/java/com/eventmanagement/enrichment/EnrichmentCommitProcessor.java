package com.eventmanagement.enrichment;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Named;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.component.kafka.consumer.KafkaManualCommit;
import org.jboss.logging.Logger;

@Named("enrichmentCommitProcessor")
@ApplicationScoped
public class EnrichmentCommitProcessor implements Processor {

    private static final Logger LOG = Logger.getLogger(EnrichmentCommitProcessor.class);

    @Override
    public void process(Exchange exchange) {
        KafkaManualCommit manualCommit = exchange.getProperty(
                "enrichmentManualCommit",
                KafkaManualCommit.class
        );
        if (manualCommit == null) {
            throw new IllegalStateException("Kafka manual commit preservado no está disponible");
        }

        manualCommit.commit();

        LOG.infov(
                "Offset confirmado: eventId={0}, eventKey={1}",
                exchange.getProperty("enrichmentEventId", String.class),
                exchange.getProperty("enrichmentEventKey", String.class)
        );
    }
}
