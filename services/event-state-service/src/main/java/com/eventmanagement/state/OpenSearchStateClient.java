package com.eventmanagement.state;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

@ApplicationScoped
public class OpenSearchStateClient {

    private static final Logger LOG =
            Logger.getLogger(OpenSearchStateClient.class);

    private final ObjectMapper objectMapper;

    private HttpClient httpClient;

    @ConfigProperty(name = "opensearch.base-url")
    String baseUrl;

    @ConfigProperty(name = "opensearch.index.events-current")
    String indexName;

    @ConfigProperty(
            name = "opensearch.request-timeout-ms",
            defaultValue = "20000"
    )
    long requestTimeoutMs;

    @Inject
    public OpenSearchStateClient(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @PostConstruct
    void initialize() {

        httpClient = HttpClient.newBuilder()
                .connectTimeout(
                        Duration.ofMillis(requestTimeoutMs)
                )
                .build();

        ensureIndexExists();
    }

    public void index(ConsolidatedEventState state)
            throws Exception {

        ObjectNode document =
                objectMapper.createObjectNode();

        document.put("schemaVersion", "1.0");

        document.put("eventKey", state.eventKey);
        document.put("eventId", state.eventId);
        if (state.lastStateAt != null) {
            document.put("sourceSeverity", state.sourceSeverity);
            document.put("effectiveSeverity", state.effectiveSeverity);
            document.put("tally", state.tally);
            document.put("lastStateAt", state.lastStateAt.toString());
            document.set("stateRequest", state.statePayload);
        }
        document.put("tenant", state.tenant);

        document.put(
                "lifecycleStatus",
                state.lifecycleStatus
        );

        putNullable(
                document,
                "ticketNumber",
                state.ticketNumber
        );

        putNullable(
                document,
                "notificationId",
                state.notificationId
        );

        putNullable(
                document,
                "automationId",
                state.automationId
        );

        ObjectNode integrationStatus =
                document.putObject("integrationStatus");

        integrationStatus.put(
                "servicenow",
                state.servicenowStatus
        );

        integrationStatus.put(
                "gnm",
                state.gnmStatus
        );

        integrationStatus.put(
                "cacf",
                state.cacfStatus
        );

        JsonNode glpi = state.integrations.get("glpi");
        integrationStatus.put("glpi", glpi == null ? "NOT_REQUIRED" :
                "RESOLVED_CONFIRMED".equals(glpi.path("ticketLifecycleState").asText()) ? "RESOLVED" : glpi.path("status").asText());

        document.set(
                "integrations",
                objectMapper.valueToTree(
                        state.integrations
                )
        );

        document.put(
                "firstSeenAt",
                state.firstSeenAt.toString()
        );

        document.put(
                "lastUpdatedAt",
                state.lastUpdatedAt.toString()
        );

        document.put(
                "version",
                state.version
        );

        String encodedEventKey =
                URLEncoder.encode(
                        state.eventKey,
                        StandardCharsets.UTF_8
                );

        URI uri = URI.create(
                normalizedBaseUrl() +
                "/" + indexName +
                "/_doc/" + encodedEventKey +
                "?refresh=wait_for"
        );

        HttpRequest request =
                HttpRequest.newBuilder()
                        .uri(uri)
                        .timeout(
                                Duration.ofMillis(
                                        requestTimeoutMs
                                )
                        )
                        .header(
                                "Content-Type",
                                "application/json"
                        )
                        .PUT(
                                HttpRequest.BodyPublishers.ofString(
                                        objectMapper.writeValueAsString(
                                                document
                                        )
                                )
                        )
                        .build();

        HttpResponse<String> response =
                httpClient.send(
                        request,
                        HttpResponse.BodyHandlers.ofString()
                );

        if (response.statusCode() < 200 ||
                response.statusCode() >= 300) {

            throw new IllegalStateException(
                    "OpenSearch respondió HTTP " +
                    response.statusCode() +
                    ": " +
                    response.body()
            );
        }

        LOG.infov(
                "Documento actualizado en OpenSearch: " +
                "index={0}, eventKey={1}, version={2}",
                indexName,
                state.eventKey,
                state.version
        );
    }

    private void ensureIndexExists() {

        try {
            ObjectNode mapping =
                    createIndexMapping();

            URI uri = URI.create(
                    normalizedBaseUrl() +
                    "/" +
                    indexName
            );

            HttpRequest request =
                    HttpRequest.newBuilder()
                            .uri(uri)
                            .timeout(
                                    Duration.ofMillis(
                                            requestTimeoutMs
                                    )
                            )
                            .header(
                                    "Content-Type",
                                    "application/json"
                            )
                            .PUT(
                                    HttpRequest.BodyPublishers.ofString(
                                            objectMapper.writeValueAsString(
                                                    mapping
                                            )
                                    )
                            )
                            .build();

            HttpResponse<String> response =
                    httpClient.send(
                            request,
                            HttpResponse.BodyHandlers.ofString()
                    );

            if (response.statusCode() >= 200 &&
                    response.statusCode() < 300) {

                LOG.infov(
                        "Índice OpenSearch creado: {0}",
                        indexName
                );

                return;
            }

            if (response.statusCode() == 400 &&
                    response.body().contains(
                            "resource_already_exists_exception"
                    )) {

                LOG.infov(
                        "Índice OpenSearch existente: {0}",
                        indexName
                );

                return;
            }

            throw new IllegalStateException(
                    "No fue posible inicializar el índice. HTTP " +
                    response.statusCode() +
                    ": " +
                    response.body()
            );

        } catch (Exception exception) {

            throw new IllegalStateException(
                    "Error inicializando OpenSearch",
                    exception
            );
        }
    }

    private ObjectNode createIndexMapping() {

        ObjectNode root =
                objectMapper.createObjectNode();

        ObjectNode settings =
                root.putObject("settings");

        settings.put(
                "number_of_shards",
                1
        );

        settings.put(
                "number_of_replicas",
                0
        );

        ObjectNode properties =
                root
                        .putObject("mappings")
                        .putObject("properties");

        properties
                .putObject("schemaVersion")
                .put("type", "keyword");

        properties
                .putObject("eventKey")
                .put("type", "keyword");

        properties
                .putObject("eventId")
                .put("type", "keyword");

        properties
                .putObject("tenant")
                .put("type", "keyword");

        properties
                .putObject("lifecycleStatus")
                .put("type", "keyword");

        properties
                .putObject("ticketNumber")
                .put("type", "keyword");

        properties
                .putObject("notificationId")
                .put("type", "keyword");

        properties
                .putObject("automationId")
                .put("type", "keyword");

        properties
                .putObject("firstSeenAt")
                .put("type", "date");

        properties
                .putObject("lastUpdatedAt")
                .put("type", "date");

        properties
                .putObject("version")
                .put("type", "long");

        properties
                .putObject("integrationStatus")
                .put("type", "object");

        properties
                .putObject("integrations")
                .put("type", "object");

        return root;
    }

    private void putNullable(
            ObjectNode document,
            String field,
            String value
    ) {

        if (value == null || value.isBlank()) {
            document.putNull(field);
        } else {
            document.put(field, value);
        }
    }

    private String normalizedBaseUrl() {

        if (baseUrl.endsWith("/")) {

            return baseUrl.substring(
                    0,
                    baseUrl.length() - 1
            );
        }

        return baseUrl;
    }
}
