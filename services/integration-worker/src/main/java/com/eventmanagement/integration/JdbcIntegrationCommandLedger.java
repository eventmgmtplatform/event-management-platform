package com.eventmanagement.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import javax.sql.DataSource;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.HexFormat;
import java.util.UUID;

@ApplicationScoped
public class JdbcIntegrationCommandLedger
        implements IntegrationCommandLedger {

    private final DataSource dataSource;
    private final ObjectMapper objectMapper;
    private final long leaseDurationMs;

    @Inject
    public JdbcIntegrationCommandLedger(
            DataSource dataSource,
            ObjectMapper objectMapper,
            @ConfigProperty(
                    name = "integration.idempotency.lease-duration-ms",
                    defaultValue = "60000"
            ) long leaseDurationMs
    ) {
        if (leaseDurationMs <= 0) {
            throw new IllegalArgumentException(
                    "leaseDurationMs must be greater than zero"
            );
        }
        this.dataSource = dataSource;
        this.objectMapper = objectMapper;
        this.leaseDurationMs = leaseDurationMs;
    }

    @Override
    @Transactional
    public Claim claim(JsonNode command) throws Exception {
        String commandId = requiredText(command, "commandId");
        String eventId = requiredText(command, "eventId");
        String eventKey = requiredText(command, "eventKey");
        String tenant = requiredText(command, "tenant");
        String integrationType = requiredText(command, "integrationType").toUpperCase();
        String operation = requiredText(command, "operation").toUpperCase();
        String commandPayload = objectMapper.writeValueAsString(command);
        String payloadHash = sha256(commandPayload);
        String candidateOwner = UUID.randomUUID().toString();

        try (Connection connection = dataSource.getConnection()) {
            if (insertClaim(
                    connection,
                    commandId,
                    payloadHash,
                    eventId,
                    eventKey,
                    tenant,
                    integrationType,
                    operation,
                    commandPayload,
                    candidateOwner
            )) {
                return new Claim(
                        Decision.EXECUTE,
                        null,
                        candidateOwner
                );
            }

            return resolveExistingClaim(
                    connection,
                    commandId,
                    payloadHash,
                    command,
                    candidateOwner
            );
        }
    }

    @Override
    @Transactional
    public void complete(
            String commandId,
            String claimOwner,
            String resultPayload
    ) throws Exception {
        requireNonBlank(commandId, "commandId is required to complete the ledger");
        requireNonBlank(claimOwner, "claimOwner is required to complete the ledger");

        JsonNode result = objectMapper.readTree(resultPayload);
        if (result == null || !result.isObject()) {
            throw new IllegalArgumentException(
                    "The terminal result must be a JSON object"
            );
        }

        String resultCommandId = requiredText(result, "commandId");
        if (!commandId.equals(resultCommandId)) {
            throw new IllegalStateException(
                    "Terminal result commandId does not match claim: " + commandId
            );
        }

        String sql = """
                UPDATE event_management.integration_command_execution
                SET execution_status = 'COMPLETED',
                    result_payload = ?::jsonb,
                    completed_at = CURRENT_TIMESTAMP,
                    updated_at = CURRENT_TIMESTAMP,
                    lease_expires_at = NULL
                WHERE command_id = ?
                  AND execution_status = 'IN_PROGRESS'
                  AND claim_owner = ?
                """;

        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, resultPayload);
            statement.setString(2, commandId);
            statement.setString(3, claimOwner);
            int updated = statement.executeUpdate();
            if (updated != 1) {
                throw new IllegalStateException(
                        "Claim ownership lost before completion: " + commandId
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
            String commandPayload,
            String claimOwner
    ) throws Exception {
        String sql = """
                INSERT INTO event_management.integration_command_execution (
                    command_id, payload_hash, event_id, event_key,
                    tenant, integration_type, operation, execution_status,
                    command_payload, claim_owner, lease_expires_at
                )
                VALUES (?, ?, ?, ?, ?, ?, ?, 'IN_PROGRESS', ?::jsonb, ?,
                        CURRENT_TIMESTAMP + (? * INTERVAL '1 millisecond'))
                ON CONFLICT (command_id) DO NOTHING
                RETURNING command_id
                """;

        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, commandId);
            statement.setString(2, payloadHash);
            statement.setString(3, eventId);
            statement.setString(4, eventKey);
            statement.setString(5, tenant);
            statement.setString(6, integrationType);
            statement.setString(7, operation);
            statement.setString(8, commandPayload);
            statement.setString(9, claimOwner);
            statement.setLong(10, leaseDurationMs);
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next();
            }
        }
    }

    private Claim resolveExistingClaim(
            Connection connection,
            String commandId,
            String payloadHash,
            JsonNode command,
            String candidateOwner
    ) throws Exception {
        String sql = """
                SELECT payload_hash,
                       command_payload,
                       execution_status,
                       result_payload,
                       lease_expires_at <= CURRENT_TIMESTAMP AS lease_expired
                FROM event_management.integration_command_execution
                WHERE command_id = ?
                """;

        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, commandId);
            try (ResultSet resultSet = statement.executeQuery()) {
                if (!resultSet.next()) {
                    throw new IllegalStateException(
                            "Idempotency claim disappeared: " + commandId
                    );
                }

                String storedHash = resultSet.getString("payload_hash").trim();
                JsonNode storedCommand = objectMapper.readTree(
                        resultSet.getString("command_payload")
                );

                // PostgreSQL jsonb normalizes object property order. The durable
                // command identity is therefore semantic JSON equality, not the
                // byte-level hash of either serialization. payload_hash remains
                // an audit value for the originally accepted representation.
                if (!command.equals(storedCommand)) {
                    throw new CommandIdCollisionException(commandId);
                }

                String status = resultSet.getString("execution_status");
                if ("COMPLETED".equals(status)) {
                    String resultPayload = resultSet.getString("result_payload");
                    if (resultPayload == null || resultPayload.isBlank()) {
                        throw new IllegalStateException(
                                "Completed command has no result: " + commandId
                        );
                    }
                    return new Claim(Decision.REPLAY, resultPayload, null);
                }

                if (!"IN_PROGRESS".equals(status)) {
                    throw new IllegalStateException(
                            "Unsupported execution status " + status +
                            " for commandId: " + commandId
                    );
                }

                if (!resultSet.getBoolean("lease_expired")) {
                    return new Claim(Decision.IN_PROGRESS, null, null);
                }
            }
        }

        if (takeOverExpiredClaim(connection, commandId, candidateOwner)) {
            return new Claim(Decision.RECONCILE, null, candidateOwner);
        }

        return new Claim(Decision.IN_PROGRESS, null, null);
    }

    private boolean takeOverExpiredClaim(
            Connection connection,
            String commandId,
            String claimOwner
    ) throws Exception {
        String sql = """
                UPDATE event_management.integration_command_execution
                SET claim_owner = ?,
                    lease_expires_at = CURRENT_TIMESTAMP +
                        (? * INTERVAL '1 millisecond'),
                    recovery_count = recovery_count + 1,
                    updated_at = CURRENT_TIMESTAMP
                WHERE command_id = ?
                  AND execution_status = 'IN_PROGRESS'
                  AND lease_expires_at <= CURRENT_TIMESTAMP
                RETURNING command_id
                """;

        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, claimOwner);
            statement.setLong(2, leaseDurationMs);
            statement.setString(3, commandId);
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next();
            }
        }
    }

    private String sha256(String payload) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        byte[] hash = digest.digest(payload.getBytes(StandardCharsets.UTF_8));
        return HexFormat.of().formatHex(hash);
    }

    private String requiredText(JsonNode node, String fieldName) {
        JsonNode value = node.get(fieldName);
        if (value == null || value.isNull() || value.asText().isBlank()) {
            throw new IllegalArgumentException(
                    "Required command field is missing: " + fieldName
            );
        }
        return value.asText().trim();
    }

    private void requireNonBlank(String value, String message) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(message);
        }
    }
}
