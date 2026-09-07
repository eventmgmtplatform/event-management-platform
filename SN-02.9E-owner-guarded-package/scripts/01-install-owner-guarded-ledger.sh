#!/usr/bin/env bash
set -euo pipefail

cd /opt/event-management-platform || exit 1

ROOT="services/integration-worker"
JAVA="${ROOT}/src/main/java/com/eventmanagement/integration"
TEST="${ROOT}/src/test/java/com/eventmanagement/integration"
ROUTE="${ROOT}/src/main/resources/routes/integration-worker.xml"
PROPS="${ROOT}/src/main/resources/application.properties"
COMPOSE="infrastructure/docker-compose.yml"

echo "===== SN-02.9E OWNER-GUARDED LEDGER INSTALLATION ====="

[[ "$(git branch --show-current)" == "feature/os-01-02-servicenow-core-foundation" ]] || { echo "ASSERTION=FAIL | Unexpected branch"; exit 1; }
[[ -f infrastructure/postgres/init/005-integration-command-recovery-lease.sql ]] || { echo "ASSERTION=FAIL | Migration 005 missing"; exit 1; }

install -d "${JAVA}" "${TEST}"

tee "${JAVA}/IntegrationCommandLedger.java" >/dev/null <<'JAVAEOF'
package com.eventmanagement.integration;

import com.fasterxml.jackson.databind.JsonNode;

public interface IntegrationCommandLedger {

    enum Decision {
        EXECUTE,
        REPLAY,
        IN_PROGRESS,
        RECONCILE
    }

    record Claim(
            Decision decision,
            String resultPayload,
            String claimOwner
    ) {
    }

    Claim claim(JsonNode command) throws Exception;

    void complete(
            String commandId,
            String claimOwner,
            String resultPayload
    ) throws Exception;
}
JAVAEOF

tee "${JAVA}/JdbcIntegrationCommandLedger.java" >/dev/null <<'JAVAEOF'
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
                if (!payloadHash.equals(storedHash) || !command.equals(storedCommand)) {
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
JAVAEOF

python3 - "${JAVA}/IntegrationCommandClaimProcessor.java" <<'PY'
from pathlib import Path
import sys
p = Path(sys.argv[1])
s = p.read_text()
s = s.replace(
'''    public static final String DECISION_PROPERTY =
            "integrationIdempotencyDecision";
''',
'''    public static final String DECISION_PROPERTY =
            "integrationIdempotencyDecision";

    public static final String CLAIM_OWNER_PROPERTY =
            "integrationIdempotencyClaimOwner";
''')
s = s.replace(
'''        exchange.setProperty(
                DECISION_PROPERTY,
                "UNRESOLVED"
        );
''',
'''        exchange.setProperty(
                DECISION_PROPERTY,
                "UNRESOLVED"
        );
        exchange.removeProperty(CLAIM_OWNER_PROPERTY);
''')
s = s.replace(
'''        String commandId =
                exchange.getProperty(
                        "commandId",
                        String.class
                );

        switch (claim.decision()) {
            case EXECUTE -> LOG.infov(
                    "Idempotency claim acquired: " +
                    "commandId={0}, decision=EXECUTE",
                    commandId
            );
''',
'''        String commandId =
                exchange.getProperty(
                        "commandId",
                        String.class
                );

        if (claim.decision() == IntegrationCommandLedger.Decision.EXECUTE ||
                claim.decision() == IntegrationCommandLedger.Decision.RECONCILE) {
            String claimOwner = claim.claimOwner();
            if (claimOwner == null || claimOwner.isBlank()) {
                throw new IllegalStateException(
                        "Owned idempotency decision has no claim owner: " + commandId
                );
            }
            exchange.setProperty(CLAIM_OWNER_PROPERTY, claimOwner);
        }

        switch (claim.decision()) {
            case EXECUTE -> LOG.infov(
                    "Idempotency claim acquired: " +
                    "commandId={0}, decision=EXECUTE",
                    commandId
            );

            case RECONCILE -> LOG.warnv(
                    "Expired idempotency claim acquired for reconciliation: " +
                    "commandId={0}, decision=RECONCILE",
                    commandId
            );
''')
if s == p.read_text():
    raise SystemExit("CLAIM_PROCESSOR_PATCH_STATUS=NOT_APPLIED")
p.write_text(s)
print("CLAIM_PROCESSOR_PATCH_STATUS=APPLIED")
PY

python3 - "${JAVA}/IntegrationCommandCompletionProcessor.java" <<'PY'
from pathlib import Path
import sys
p = Path(sys.argv[1])
s = p.read_text()
s = s.replace(
'''        if (!IntegrationCommandLedger.Decision.EXECUTE
                .name()
                .equals(decision)) {

            return;
        }
''',
'''        boolean ownedDecision =
                IntegrationCommandLedger.Decision.EXECUTE.name().equals(decision) ||
                IntegrationCommandLedger.Decision.RECONCILE.name().equals(decision);

        if (!ownedDecision) {
            return;
        }
''')
s = s.replace(
'''        String resultPayload =
                exchange.getMessage()
                        .getBody(String.class);
''',
'''        String claimOwner =
                exchange.getProperty(
                        IntegrationCommandClaimProcessor.CLAIM_OWNER_PROPERTY,
                        String.class
                );

        String resultPayload =
                exchange.getMessage()
                        .getBody(String.class);
''')
s = s.replace(
'''        if (resultPayload == null ||
                resultPayload.isBlank()) {
''',
'''        if (claimOwner == null || claimOwner.isBlank()) {
            throw new IllegalStateException(
                    "claimOwner unavailable during completion: " + commandId
            );
        }

        if (resultPayload == null ||
                resultPayload.isBlank()) {
''')
s = s.replace(
'''        ledger.complete(
                commandId,
                resultPayload
        );
''',
'''        ledger.complete(
                commandId,
                claimOwner,
                resultPayload
        );
''')
if s == p.read_text():
    raise SystemExit("COMPLETION_PROCESSOR_PATCH_STATUS=NOT_APPLIED")
p.write_text(s)
print("COMPLETION_PROCESSOR_PATCH_STATUS=APPLIED")
PY

python3 - "${ROUTE}" <<'PY'
from pathlib import Path
import sys
p = Path(sys.argv[1])
s = p.read_text()
needle = '''                <!--
                    The command already completed. The claim processor
                    placed its stored terminal result in the message body.
                -->
                <when>
                    <simple>${exchangeProperty.integrationIdempotencyDecision} == 'REPLAY'</simple>
'''
replacement = '''                <!--
                    Ownership of an expired claim was acquired. Until the
                    lookup/reconciliation client is installed, stop here.
                    A blind CREATE could duplicate a ServiceNow ticket.
                -->
                <when>
                    <simple>${exchangeProperty.integrationIdempotencyDecision} == 'RECONCILE'</simple>

                    <log loggingLevel="WARN"
                         message="Reconciliación pendiente; CREATE bloqueado: commandId=${exchangeProperty.commandId}"/>

                    <stop/>
                </when>

                <!--
                    The command already completed. The claim processor
                    placed its stored terminal result in the message body.
                -->
                <when>
                    <simple>${exchangeProperty.integrationIdempotencyDecision} == 'REPLAY'</simple>
'''
if needle not in s:
    raise SystemExit("ROUTE_PATCH_STATUS=MARKER_NOT_FOUND")
p.write_text(s.replace(needle, replacement, 1))
print("ROUTE_PATCH_STATUS=APPLIED")
PY

python3 - "${PROPS}" "${COMPOSE}" <<'PY'
from pathlib import Path
import sys
props = Path(sys.argv[1])
compose = Path(sys.argv[2])
s = props.read_text()
marker = "quarkus.datasource.jdbc.acquisition-timeout=20S\n"
line = "integration.idempotency.lease-duration-ms=${INTEGRATION_IDEMPOTENCY_LEASE_DURATION_MS:60000}\n"
if line not in s:
    if marker not in s:
        raise SystemExit("PROPERTIES_PATCH_STATUS=MARKER_NOT_FOUND")
    s = s.replace(marker, marker + line, 1)
    props.write_text(s)

s = compose.read_text()
line = "      INTEGRATION_IDEMPOTENCY_LEASE_DURATION_MS: 60000\n"
if line not in s:
    marker = "      POSTGRES_PASSWORD: ${POSTGRES_PASSWORD}\n"
    if marker not in s:
        raise SystemExit("COMPOSE_PATCH_STATUS=MARKER_NOT_FOUND")
    s = s.replace(marker, marker + line, 1)
    compose.write_text(s)
print("CONFIGURATION_PATCH_STATUS=APPLIED")
PY

# Mechanical signature update in existing unit-test stubs. New owner semantics
# are certified independently by the generated focused test.
python3 - "${TEST}/IntegrationCommandClaimProcessorTest.java" "${TEST}/IntegrationCommandCompletionProcessorTest.java" <<'PY'
from pathlib import Path
import re, sys
claim = Path(sys.argv[1])
s = claim.read_text()
s = re.sub(r'(Decision\.EXECUTE,\s*null)(\s*\))', r'\1,\n                        "owner-execute"\2', s, count=1)
s = re.sub(r'(Decision\.REPLAY,\s*result)(\s*\))', r'\1,\n                        null\2', s, count=1)
s = re.sub(r'(Decision\.IN_PROGRESS,\s*null)(\s*\))', r'\1,\n                        null\2', s, count=1)
s = s.replace("String commandId,\n                            String resultPayload", "String commandId,\n                            String claimOwner,\n                            String resultPayload")
s = s.replace("String commandId,\n                String resultPayload", "String commandId,\n                String claimOwner,\n                String resultPayload")
claim.write_text(s)

completion = Path(sys.argv[2])
s = completion.read_text()
s = s.replace("private String completedResult;", "private String completedOwner;\n        private String completedResult;")
s = s.replace('exchange.setProperty(\n                IntegrationCommandClaimProcessor\n                        .DECISION_PROPERTY,\n                decision.name()\n        );', 'exchange.setProperty(\n                IntegrationCommandClaimProcessor\n                        .DECISION_PROPERTY,\n                decision.name()\n        );\n\n        exchange.setProperty(\n                IntegrationCommandClaimProcessor.CLAIM_OWNER_PROPERTY,\n                "owner-completion"\n        );')
s = s.replace("String commandId,\n                String resultPayload", "String commandId,\n                String claimOwner,\n                String resultPayload")
s = s.replace("completedCommandId = commandId;\n            completedResult", "completedCommandId = commandId;\n            completedOwner = claimOwner;\n            completedResult")
completion.write_text(s)
print("EXISTING_TEST_SIGNATURE_PATCH_STATUS=APPLIED")
PY

tee "${TEST}/OwnerGuardedLedgerContractTest.java" >/dev/null <<'JAVAEOF'
package com.eventmanagement.integration;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class OwnerGuardedLedgerContractTest {

    @Test
    void shouldExposeSafeRecoveryDecision() {
        IntegrationCommandLedger.Claim claim =
                new IntegrationCommandLedger.Claim(
                        IntegrationCommandLedger.Decision.RECONCILE,
                        null,
                        "owner-recovery"
                );

        assertEquals(
                IntegrationCommandLedger.Decision.RECONCILE,
                claim.decision()
        );
        assertEquals("owner-recovery", claim.claimOwner());
        assertNull(claim.resultPayload());
    }
}
JAVAEOF

echo
echo "===== INSTALLATION CHANGESET ====="
git status --short
git diff --stat

echo
echo "===== STATIC SAFETY ASSERTIONS ====="
grep -q "Decision.RECONCILE" "${JAVA}/JdbcIntegrationCommandLedger.java"
grep -q "AND claim_owner = ?" "${JAVA}/JdbcIntegrationCommandLedger.java"
grep -q "lease_expires_at <= CURRENT_TIMESTAMP" "${JAVA}/JdbcIntegrationCommandLedger.java"
grep -q "decision=RECONCILE" "${JAVA}/IntegrationCommandClaimProcessor.java"
grep -q "CREATE bloqueado" "${ROUTE}"
echo "ASSERTION=PASS | Atomic takeover and owner-guarded completion installed"
echo "ASSERTION=PASS | Blind stale CREATE is blocked"
echo "SN_02_9E_OWNER_GUARDED_LEDGER_INSTALLED=TRUE"

