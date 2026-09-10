package com.eventmanagement.state;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.component.kafka.KafkaConstants;
import org.apache.camel.component.kafka.consumer.KafkaManualCommit;
import org.jboss.logging.Logger;

@Named("integrationResultStateProcessor")
@ApplicationScoped
public class IntegrationResultStateProcessor implements Processor {

    private static final Logger LOG =
            Logger.getLogger(
                    IntegrationResultStateProcessor.class
            );

    private final ObjectMapper objectMapper;
    private final EventStateRepository repository;
    private final StateProjectionService openSearchClient;

    @Inject
    public IntegrationResultStateProcessor(
            ObjectMapper objectMapper,
            EventStateRepository repository,
            StateProjectionService openSearchClient
    ) {
        this.objectMapper = objectMapper;
        this.repository = repository;
        this.openSearchClient = openSearchClient;
    }

    @Override
    public void process(Exchange exchange) throws Exception {

        String body =
                exchange.getMessage().getBody(String.class);

        if (body == null) {
            throw new IllegalArgumentException(
                    "El resultado de integración está vacío"
            );
        }

        KafkaManualCommit manualCommit =
                exchange.getMessage().getHeader(
                        KafkaConstants.MANUAL_COMMIT,
                        KafkaManualCommit.class
                );

        if (manualCommit == null) {
            throw new IllegalStateException(
                    "Kafka manual commit no está disponible"
            );
        }

        JsonNode result;
        ConsolidatedEventState state;
        try {
            try {
                result = objectMapper.readTree(body);
            } catch (com.fasterxml.jackson.core.JsonProcessingException invalidJson) {
                throw new RejectedIntegrationResult("INVALID_JSON");
            }
            IntegrationResultContract.validate(result);
            state = repository.consolidate(result);
        } catch (RejectedIntegrationResult rejected) {
            String topic = exchange.getMessage().getHeader(KafkaConstants.TOPIC, String.class);
            Integer partition = exchange.getMessage().getHeader(KafkaConstants.PARTITION, Integer.class);
            Long offset = exchange.getMessage().getHeader(KafkaConstants.OFFSET, Long.class);
            if (topic == null || partition == null || offset == null) {
                throw new IllegalStateException("QUARANTINE_SOURCE_COORDINATES_REQUIRED");
            }
            repository.quarantine(topic, partition, offset, body, rejected.getMessage());
            // CDI transaction has committed. Failure to commit Kafka will replay
            // into the same quarantine PK without discarding the rejected input.
            manualCommit.commit();
            exchange.setProperty("disposition", "QUARANTINED");
            LOG.warnv("Resultado en cuarentena: topic={0}, partition={1}, offset={2}, reason={3}",
                    topic, partition, offset, rejected.getMessage());
            return;
        }
        String resultId = requiredText(result, "resultId");
        String eventKey = requiredText(result, "eventKey");
        String integrationType = requiredText(result, "integrationType");
        String status = requiredText(result, "status");

        openSearchClient.project(state.eventKey);

        /*
         * Confirmar Kafka únicamente después de que PostgreSQL y
         * OpenSearch hayan finalizado correctamente.
         */
        manualCommit.commit();

        LOG.infov(
                "Offset Kafka confirmado: resultId={0}",
                resultId
        );

        exchange.setProperty(
                "resultId",
                resultId
        );

        exchange.setProperty(
                "eventKey",
                eventKey
        );

        exchange.setProperty(
                "integrationType",
                integrationType
        );

        exchange.setProperty(
                "integrationStatus",
                status
        );

        exchange.setProperty(
                "consolidatedVersion",
                state.version
        );
    }

    private String requiredText(
            JsonNode node,
            String field
    ) {

        JsonNode value =
                node.get(field);

        if (value == null ||
                value.isNull() ||
                value.asText().isBlank()) {

            throw new IllegalArgumentException(
                    "Campo obligatorio ausente: " +
                    field
            );
        }

        return value.asText().trim();
    }
}
