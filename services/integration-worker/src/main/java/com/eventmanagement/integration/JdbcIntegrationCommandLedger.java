package com.eventmanagement.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import javax.sql.DataSource;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.HexFormat;

@ApplicationScoped
public class JdbcIntegrationCommandLedger
        implements IntegrationCommandLedger {

    private final DataSource dataSource;
    private final ObjectMapper objectMapper;

    @Inject
    public JdbcIntegrationCommandLedger(
            DataSource dataSource,
            ObjectMapper objectMapper
    ) {
        this.dataSource = dataSource;
        this.objectMapper = objectMapper;
    }

    @Override
    @Transactional
    public Claim claim(JsonNode command) throws Exception {

        String commandId = requiredText(command, "commandId");
        String eventId = requiredText(command, "eventId");
        String eventKey = requiredText(command, "eventKey");
        String tenant = requiredText(command, "tenant");

        String integrationType =
                requiredText(command, "integrationType")
                        .toUpperCase();

        String operation =
                requiredText(command, "operation")
                        .toUpperCase();

        String commandPayload =
                objectMapper.writeValueAsString(command);

        String payloadHash =
                sha256(commandPayload);

        try (Connection connection =
                     dataSource.getConnection()) {

            boolean inserted = insertClaim(
                    connection,
                    commandId,
                    payloadHash,
                    eventId,
                    eventKey,
                    tenant,
                    integrationType,
                    operation,
                    commandPayload
            );

            if (inserted) {
                return new Claim(
                        Decision.EXECUTE,
                        null
                );
            }

            return resolveExistingClaim(
                    connection,
                    commandId,
                    payloadHash,
                    command
            );
        }
    }

    @Override
    @Transactional
    public void complete(
            String commandId,
            String resultPayload
    ) throws Exception {

        if (commandId == null || commandId.isBlank()) {
            throw new IllegalArgumentException(
                    "commandId is required to complete the ledger"
            );
        }

        JsonNode result = objectMapper.readTree(resultPayload);

        if (result == null || !result.isObject()) {
            throw new IllegalArgumentException(
                    "The terminal result must be a JSON object"
            );
        }

        String resultCommandId =
                requiredText(result, "commandId");

        if (!commandId.equals(resultCommandId)) {
            throw new IllegalStateException(
                    "Terminal result commandId does not match claim: " +
                    commandId
            );
        }

        String sql = """
                UPDATE
                    event_management.integration_command_execution
                SET
                    execution_status = 'COMPLETED',
                    result_payload = ?::jsonb,
                    completed_at = CURRENT_TIMESTAMP,
                    updated_at = CURRENT_TIMESTAMP
                WHERE command_id = ?
                  AND execution_status = 'IN_PROGRESS'
                """;

        try (Connection connection =
                     dataSource.getConnection();
             PreparedStatement statement =
                     connection.prepareStatement(sql)) {

            statement.setString(1, resultPayload);
            statement.setString(2, commandId);

            int updated = statement.executeUpdate();

            if (updated != 1) {
                throw new IllegalStateException(
                        "No IN_PROGRESS claim found for commandId: " +
                        commandId
                );
            }
        }
    }

    private boolean insertClaim(
            Connection connection,
            String commandId,
            String payloadHash,
            String eventId,
            String eventKey,
            String tenant,
            String integrationType,
            String operation,
            String commandPayload
    ) throws Exception {

        String sql = """
                INSERT INTO
                    event_management.integration_command_execution (
                        command_id,
                        payload_hash,
                        event_id,
                        event_key,
                        tenant,
                        integration_type,
                        operation,
                        execution_status,
                        command_payload
                    )
                VALUES (?, ?, ?, ?, ?, ?, ?, 'IN_PROGRESS', ?::jsonb)
                ON CONFLICT (command_id) DO NOTHING
                RETURNING command_id
                """;

        try (PreparedStatement statement =
                     connection.prepareStatement(sql)) {

            statement.setString(1, commandId);
            statement.setString(2, payloadHash);
            statement.setString(3, eventId);
            statement.setString(4, eventKey);
            statement.setString(5, tenant);
            statement.setString(6, integrationType);
            statement.setString(7, operation);
            statement.setString(8, commandPayload);

            try (ResultSet resultSet =
                         statement.executeQuery()) {

                return resultSet.next();
            }
        }
    }

    private Claim resolveExistingClaim(
            Connection connection,
            String commandId,
            String payloadHash,
            JsonNode command
    ) throws Exception {

        String sql = """
                SELECT
                    payload_hash,
                    command_payload,
                    execution_status,
                    result_payload
                FROM
                    event_management.integration_command_execution
                WHERE command_id = ?
                """;

        try (PreparedStatement statement =
                     connection.prepareStatement(sql)) {

            statement.setString(1, commandId);

            try (ResultSet resultSet =
                         statement.executeQuery()) {

                if (!resultSet.next()) {
                    throw new IllegalStateException(
                            "Idempotency claim disappeared: " +
                            commandId
                    );
                }

                String storedHash =
                        resultSet.getString("payload_hash")
                                .trim();

                JsonNode storedCommand =
                        objectMapper.readTree(
                                resultSet.getString(
                                        "command_payload"
                                )
                        );

                if (!payloadHash.equals(storedHash) ||
                        !command.equals(storedCommand)) {

                    throw new IllegalStateException(
                            "commandId collision with different payload: " +
                            commandId
                    );
                }

                String status =
                        resultSet.getString(
                                "execution_status"
                        );

                if ("IN_PROGRESS".equals(status)) {
                    return new Claim(
                            Decision.IN_PROGRESS,
                            null
                    );
                }

                if ("COMPLETED".equals(status)) {
                    String resultPayload =
                            resultSet.getString(
                                    "result_payload"
                            );

                    if (resultPayload == null ||
                            resultPayload.isBlank()) {

                        throw new IllegalStateException(
                                "Completed command has no result: " +
                                commandId
                        );
                    }

                    return new Claim(
                            Decision.REPLAY,
                            resultPayload
                    );
                }

                throw new IllegalStateException(
                        "Unsupported execution status " +
                        status +
                        " for commandId: " +
                        commandId
                );
            }
        }
    }

    private String sha256(String payload)
            throws Exception {

        MessageDigest digest =
                MessageDigest.getInstance("SHA-256");

        byte[] hash = digest.digest(
                payload.getBytes(StandardCharsets.UTF_8)
        );

        return HexFormat.of().formatHex(hash);
    }

    private String requiredText(
            JsonNode node,
            String fieldName
    ) {

        JsonNode value = node.get(fieldName);

        if (value == null ||
                value.isNull() ||
                value.asText().isBlank()) {

            throw new IllegalArgumentException(
                    "Required command field is missing: " +
                    fieldName
            );
        }

        return value.asText().trim();
    }
}
