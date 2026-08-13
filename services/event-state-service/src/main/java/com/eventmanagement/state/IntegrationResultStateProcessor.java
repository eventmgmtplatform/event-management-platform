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
    private final OpenSearchStateClient openSearchClient;

    @Inject
    public IntegrationResultStateProcessor(
            ObjectMapper objectMapper,
            EventStateRepository repository,
            OpenSearchStateClient openSearchClient
    ) {
        this.objectMapper = objectMapper;
        this.repository = repository;
        this.openSearchClient = openSearchClient;
    }

    @Override
    public void process(Exchange exchange) throws Exception {

        String body =
                exchange.getMessage().getBody(String.class);

        if (body == null || body.isBlank()) {
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

        JsonNode result =
                objectMapper.readTree(body);

        String resultId =
                requiredText(result, "resultId");

        String eventKey =
                requiredText(result, "eventKey");

        String integrationType =
                requiredText(result, "integrationType");

        String status =
                requiredText(result, "status");

        LOG.infov(
                "Consolidando resultado: resultId={0}, " +
                "eventKey={1}, integration={2}, status={3}",
                resultId,
                eventKey,
                integrationType,
                status
        );

        /*
         * Primero PostgreSQL y después OpenSearch.
         *
         * Si OpenSearch falla, el procesamiento completo fallará.
         * Kafka podrá volver a entregar el mensaje.
         */
        ConsolidatedEventState state =
                repository.consolidate(result);

        openSearchClient.index(state);

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
