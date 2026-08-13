package com.eventmanagement.state;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.jboss.logging.Logger;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.OffsetDateTime;

@ApplicationScoped
public class EventStateRepository {

    private static final Logger LOG =
            Logger.getLogger(EventStateRepository.class);

    private final DataSource dataSource;
    private final ObjectMapper objectMapper;

    @Inject
    public EventStateRepository(
            DataSource dataSource,
            ObjectMapper objectMapper
    ) {
        this.dataSource = dataSource;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public ConsolidatedEventState consolidate(JsonNode result)
            throws Exception {

        String resultId = requiredText(result, "resultId");
        String eventKey = requiredText(result, "eventKey");
        String eventId = requiredText(result, "eventId");
        String tenant = requiredText(result, "tenant");

        String integrationType =
                requiredText(result, "integrationType").toUpperCase();

        String integrationStatus =
                requiredText(result, "status").toUpperCase();

        String externalId =
                nullableText(result, "externalId");

        try (Connection connection = dataSource.getConnection()) {

            boolean newResult = claimResult(
                    connection,
                    resultId,
                    eventKey,
                    eventId,
                    tenant,
                    integrationType,
                    result
            );

            if (!newResult) {
                validateExistingClaim(
                        connection,
                        resultId,
                        eventKey,
                        eventId,
                        tenant,
                        integrationType,
                        result
                );

                ConsolidatedEventState existingState =
                        findForUpdate(connection, eventKey);

                if (existingState == null) {
                    throw new IllegalStateException(
                            "Resultado procesado sin estado consolidado: " +
                            resultId
                    );
                }

                LOG.infov(
                        "Resultado duplicado reconocido sin incrementar " +
                        "versión: resultId={0}, eventKey={1}, version={2}",
                        resultId,
                        eventKey,
                        existingState.version
                );

                return existingState;
            }
           
	   ConsolidatedEventState state =
        findForUpdate(connection, eventKey);

if (state == null) {
    state = newState(
            eventKey,
            eventId,
            tenant
    );
}

state.eventId = eventId;
state.tenant = tenant;
state.lastUpdatedAt = OffsetDateTime.now();
state.version++;

state.integrations.put(
        integrationType.toLowerCase(),
        result.deepCopy()
);

applyIntegrationResult(
        state,
        integrationType,
        integrationStatus,
        externalId
);

upsert(
        connection,
        state,
        result
);

LOG.infov(
        "Estado consolidado en PostgreSQL: " +
        "eventKey={0}, integration={1}, " +
        "status={2}, version={3}",
        eventKey,
        integrationType,
        integrationStatus,
        state.version
);

return state;
        }
    }

    private boolean claimResult(
            Connection connection,
            String resultId,
            String eventKey,
            String eventId,
            String tenant,
            String integrationType,
            JsonNode result
    ) throws Exception {

        String sql = """
                INSERT INTO
                    event_management.processed_integration_result (
                        result_id,
                        event_key,
                        event_id,
                        tenant,
                        integration_type,
                        result_payload
                    )
                VALUES (?, ?, ?, ?, ?, ?::jsonb)
                ON CONFLICT (result_id) DO NOTHING
                RETURNING result_id
                """;

        try (PreparedStatement statement =
                     connection.prepareStatement(sql)) {

            statement.setString(1, resultId);
            statement.setString(2, eventKey);
            statement.setString(3, eventId);
            statement.setString(4, tenant);
            statement.setString(5, integrationType);
            statement.setString(
                    6,
                    objectMapper.writeValueAsString(result)
            );

            try (ResultSet resultSet =
                         statement.executeQuery()) {

                return resultSet.next();
            }
        }
    }

    private void validateExistingClaim(
            Connection connection,
            String resultId,
            String eventKey,
            String eventId,
            String tenant,
            String integrationType,
            JsonNode result
    ) throws Exception {

        String sql = """
                SELECT
                    event_key,
                    event_id,
                    tenant,
                    integration_type,
                    result_payload
                FROM
                    event_management.processed_integration_result
                WHERE result_id = ?
                """;

        try (PreparedStatement statement =
                     connection.prepareStatement(sql)) {

            statement.setString(1, resultId);

            try (ResultSet resultSet =
                         statement.executeQuery()) {

                if (!resultSet.next()) {
                    throw new IllegalStateException(
                            "Reclamación idempotente ausente: " +
                            resultId
                    );
                }

                JsonNode storedPayload =
                        objectMapper.readTree(
                                resultSet.getString(
                                        "result_payload"
                                )
                        );

                boolean identityMatches =
                        eventKey.equals(
                                resultSet.getString("event_key")
                        ) &&
                        eventId.equals(
                                resultSet.getString("event_id")
                        ) &&
                        tenant.equals(
                                resultSet.getString("tenant")
                        ) &&
                        integrationType.equals(
                                resultSet.getString(
                                        "integration_type"
                                )
                        );

                if (!identityMatches ||
                        !result.equals(storedPayload)) {

                    throw new IllegalStateException(
                            "Colisión de resultId con contenido " +
                            "diferente: " + resultId
                    );
                }
            }
        }
    }

    private ConsolidatedEventState findForUpdate(
            Connection connection,
            String eventKey
    ) throws Exception {

        String sql = """
                SELECT
                    event_key,
                    event_id,
                    tenant,
                    lifecycle_status,
                    ticket_number,
                    notification_id,
                    automation_id,
                    servicenow_status,
                    gnm_status,
                    cacf_status,
                    integration_state,
                    first_seen_at,
                    last_updated_at,
                    version
                FROM event_management.event_state
                WHERE event_key = ?
                FOR UPDATE
                """;

        try (PreparedStatement statement =
                     connection.prepareStatement(sql)) {

            statement.setString(1, eventKey);

            try (ResultSet rs = statement.executeQuery()) {

                if (!rs.next()) {
                    return null;
                }

                ConsolidatedEventState state =
                        new ConsolidatedEventState();

                state.eventKey =
                        rs.getString("event_key");

                state.eventId =
                        rs.getString("event_id");

                state.tenant =
                        rs.getString("tenant");

                state.lifecycleStatus =
                        rs.getString("lifecycle_status");

                state.ticketNumber =
                        rs.getString("ticket_number");

                state.notificationId =
                        rs.getString("notification_id");

                state.automationId =
                        rs.getString("automation_id");

                state.servicenowStatus =
                        rs.getString("servicenow_status");

                state.gnmStatus =
                        rs.getString("gnm_status");

                state.cacfStatus =
                        rs.getString("cacf_status");

                String integrationState =
                        rs.getString("integration_state");

                if (integrationState != null &&
                        !integrationState.isBlank()) {

                    JsonNode integrations =
                            objectMapper.readTree(integrationState);

                    integrations.fields().forEachRemaining(
                            entry -> state.integrations.put(
                                    entry.getKey(),
                                    entry.getValue()
                            )
                    );
                }

                state.firstSeenAt =
                        rs.getObject(
                                "first_seen_at",
                                OffsetDateTime.class
                        );

                state.lastUpdatedAt =
                        rs.getObject(
                                "last_updated_at",
                                OffsetDateTime.class
                        );

                state.version =
                        rs.getLong("version");

                return state;
            }
        }
    }

    private ConsolidatedEventState newState(
            String eventKey,
            String eventId,
            String tenant
    ) {

        OffsetDateTime now = OffsetDateTime.now();

        ConsolidatedEventState state =
                new ConsolidatedEventState();

        state.eventKey = eventKey;
        state.eventId = eventId;
        state.tenant = tenant;

        state.lifecycleStatus = "OPEN";

        state.servicenowStatus = "NOT_REQUIRED";
        state.gnmStatus = "NOT_REQUIRED";
        state.cacfStatus = "NOT_REQUIRED";

        state.firstSeenAt = now;
        state.lastUpdatedAt = now;

        /*
         * consolidate() incrementará la versión antes del UPSERT.
         */
        state.version = 0;

        return state;
    }

    private void applyIntegrationResult(
            ConsolidatedEventState state,
            String integrationType,
            String integrationStatus,
            String externalId
    ) {

        switch (integrationType) {

            case "SERVICENOW" -> {
                state.servicenowStatus = integrationStatus;

                if ("SUCCESS".equals(integrationStatus) &&
                        externalId != null) {

                    state.ticketNumber = externalId;
                }
            }

            case "GNM" -> {
                state.gnmStatus = integrationStatus;

                if ("SUCCESS".equals(integrationStatus) &&
                        externalId != null) {

                    state.notificationId = externalId;
                }
            }

            case "CACF" -> {
                state.cacfStatus = integrationStatus;

                if ("SUCCESS".equals(integrationStatus) &&
                        externalId != null) {

                    state.automationId = externalId;
                }
            }

            default -> throw new IllegalArgumentException(
                    "Tipo de integración no soportado: " +
                    integrationType
            );
        }
    }

    private void upsert(
            Connection connection,
            ConsolidatedEventState state,
            JsonNode lastResult
    ) throws Exception {

        String sql = """
                INSERT INTO event_management.event_state (
                    event_key,
                    event_id,
                    tenant,
                    lifecycle_status,
                    ticket_number,
                    notification_id,
                    automation_id,
                    servicenow_status,
                    gnm_status,
                    cacf_status,
                    integration_state,
                    last_result,
                    first_seen_at,
                    last_updated_at,
                    version
                )
                VALUES (
                    ?, ?, ?, ?, ?, ?, ?, ?, ?, ?,
                    ?::jsonb,
                    ?::jsonb,
                    ?, ?, ?
                )
                ON CONFLICT (event_key)
                DO UPDATE SET
                    event_id = EXCLUDED.event_id,
                    tenant = EXCLUDED.tenant,
                    lifecycle_status =
                        EXCLUDED.lifecycle_status,
                    ticket_number =
                        EXCLUDED.ticket_number,
                    notification_id =
                        EXCLUDED.notification_id,
                    automation_id =
                        EXCLUDED.automation_id,
                    servicenow_status =
                        EXCLUDED.servicenow_status,
                    gnm_status =
                        EXCLUDED.gnm_status,
                    cacf_status =
                        EXCLUDED.cacf_status,
                    integration_state =
                        EXCLUDED.integration_state,
                    last_result =
                        EXCLUDED.last_result,
                    last_updated_at =
                        EXCLUDED.last_updated_at,
                    version =
                        EXCLUDED.version
                """;

        try (PreparedStatement statement =
                     connection.prepareStatement(sql)) {

            statement.setString(1, state.eventKey);
            statement.setString(2, state.eventId);
            statement.setString(3, state.tenant);
            statement.setString(4, state.lifecycleStatus);

            statement.setString(5, state.ticketNumber);
            statement.setString(6, state.notificationId);
            statement.setString(7, state.automationId);

            statement.setString(
                    8,
                    state.servicenowStatus
            );

            statement.setString(
                    9,
                    state.gnmStatus
            );

            statement.setString(
                    10,
                    state.cacfStatus
            );

            statement.setString(
                    11,
                    objectMapper.writeValueAsString(
                            state.integrations
                    )
            );

            statement.setString(
                    12,
                    objectMapper.writeValueAsString(
                            lastResult
                    )
            );

            statement.setObject(
                    13,
                    state.firstSeenAt
            );

            statement.setObject(
                    14,
                    state.lastUpdatedAt
            );

            statement.setLong(
                    15,
                    state.version
            );

            statement.executeUpdate();
        }
    }

    private String requiredText(
            JsonNode node,
            String field
    ) {

        JsonNode value = node.get(field);

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

    private String nullableText(
            JsonNode node,
            String field
    ) {

        JsonNode value = node.get(field);

        if (value == null ||
                value.isNull() ||
                value.asText().isBlank()) {

            return null;
        }

        return value.asText().trim();
    }
}
