#!/usr/bin/env bash
set -euo pipefail

cd /opt/event-management-platform || exit 1

EXPECTED_HEAD="74d75b0f4dec94a2b1d0aaaa06ec18aa2d61385e"
ROOT="services/integration-worker"
JAVA="${ROOT}/src/main/java/com/eventmanagement/integration"
TEST="${ROOT}/src/test/java/com/eventmanagement/integration"
POM="${ROOT}/pom.xml"
PROPS="${ROOT}/src/main/resources/application.properties"
ROUTE="${ROOT}/src/main/resources/routes/integration-worker.xml"
COMPOSE="infrastructure/docker-compose.yml"
MIGRATION="infrastructure/postgres/init/006-integration-worker-control.sql"

echo "===== SN-02.9G PERSISTENT OPERATIONAL CONTROL ====="

[[ "$(git branch --show-current)" == "feature/os-01-02-servicenow-core-foundation" ]] || {
  echo "ASSERTION=FAIL | Unexpected branch"
  exit 1
}

[[ "$(git rev-parse HEAD)" == "${EXPECTED_HEAD}" ]] || {
  echo "ASSERTION=FAIL | Unexpected baseline commit"
  exit 1
}

[[ -z "$(git status --porcelain --untracked-files=no)" ]] || {
  echo "ASSERTION=FAIL | Tracked worktree is not clean"
  exit 1
}

echo "ASSERTION=PASS | Certified baseline clean"

tee "${MIGRATION}" >/dev/null <<'SQLEOF'
\set ON_ERROR_STOP on

CREATE TABLE IF NOT EXISTS
    event_management.integration_worker_control (
        singleton_id BOOLEAN PRIMARY KEY DEFAULT TRUE,
        operating_mode VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
        recovery_generation BIGINT NOT NULL DEFAULT 0,
        recovery_started_at TIMESTAMPTZ,
        recovery_completed_at TIMESTAMPTZ,
        last_error TEXT,
        updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
        CONSTRAINT integration_worker_control_singleton
            CHECK (singleton_id),
        CONSTRAINT integration_worker_control_mode
            CHECK (
                operating_mode IN (
                    'STANDBY',
                    'ACTIVE',
                    'PULL_RESTART'
                )
            ),
        CONSTRAINT integration_worker_control_generation
            CHECK (recovery_generation >= 0)
    );

INSERT INTO event_management.integration_worker_control (
    singleton_id,
    operating_mode
)
VALUES (TRUE, 'ACTIVE')
ON CONFLICT (singleton_id) DO NOTHING;
SQLEOF

python3 - "${POM}" <<'PY'
from pathlib import Path
import sys
p = Path(sys.argv[1])
s = p.read_text()
if '<artifactId>quarkus-rest-jackson</artifactId>' not in s:
    marker = '''        <dependency>
            <groupId>io.quarkus</groupId>
            <artifactId>quarkus-smallrye-health</artifactId>
        </dependency>
'''
    addition = marker + '''        <dependency>
            <groupId>io.quarkus</groupId>
            <artifactId>quarkus-rest-jackson</artifactId>
        </dependency>
'''
    if marker not in s:
        raise SystemExit('POM_CONTROL_PATCH_STATUS=MARKER_NOT_FOUND')
    p.write_text(s.replace(marker, addition, 1))
print('POM_CONTROL_PATCH_STATUS=APPLIED')
PY

tee "${JAVA}/IntegrationOperatingMode.java" >/dev/null <<'JAVAEOF'
package com.eventmanagement.integration;

import java.util.Locale;

public enum IntegrationOperatingMode {
    STANDBY,
    ACTIVE,
    PULL_RESTART;

    public static IntegrationOperatingMode parse(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(
                    "operatingMode is required"
            );
        }
        try {
            return valueOf(
                    value.trim().toUpperCase(Locale.ROOT)
            );
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException(
                    "Unsupported operatingMode: " + value,
                    exception
            );
        }
    }
}
JAVAEOF

tee "${JAVA}/IntegrationOperationalState.java" >/dev/null <<'JAVAEOF'
package com.eventmanagement.integration;

import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class IntegrationOperationalState {

    private volatile IntegrationOperatingMode mode =
            IntegrationOperatingMode.STANDBY;
    private volatile boolean admissionOpen;
    private volatile boolean recoveryComplete;
    private volatile String errorType = "none";

    public IntegrationOperatingMode mode() {
        return mode;
    }

    public boolean admissionOpen() {
        return admissionOpen;
    }

    public boolean recoveryComplete() {
        return recoveryComplete;
    }

    public String errorType() {
        return errorType;
    }

    public synchronized void active() {
        mode = IntegrationOperatingMode.ACTIVE;
        recoveryComplete = true;
        admissionOpen = true;
        errorType = "none";
    }

    public synchronized void standby() {
        mode = IntegrationOperatingMode.STANDBY;
        admissionOpen = false;
        recoveryComplete = false;
        errorType = "none";
    }

    public synchronized void pullRestartPending() {
        mode = IntegrationOperatingMode.PULL_RESTART;
        admissionOpen = false;
        recoveryComplete = false;
        errorType = "none";
    }

    public synchronized void failure(Throwable exception) {
        admissionOpen = false;
        recoveryComplete = false;
        errorType = exception == null
                ? "Unknown"
                : exception.getClass().getSimpleName();
    }
}
JAVAEOF

tee "${JAVA}/IntegrationOperationalControl.java" >/dev/null <<'JAVAEOF'
package com.eventmanagement.integration;

import io.quarkus.runtime.StartupEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.apache.camel.CamelContext;
import org.apache.camel.ServiceStatus;
import org.jboss.logging.Logger;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.OffsetDateTime;

@ApplicationScoped
public class IntegrationOperationalControl {

    public static final String INGRESS_ROUTE_ID =
            "integration-command-consumer";

    private static final Logger LOG = Logger.getLogger(
            IntegrationOperationalControl.class
    );

    private final DataSource dataSource;
    private final CamelContext camelContext;
    private final IntegrationOperationalState state;

    @Inject
    public IntegrationOperationalControl(
            DataSource dataSource,
            CamelContext camelContext,
            IntegrationOperationalState state
    ) {
        this.dataSource = dataSource;
        this.camelContext = camelContext;
        this.state = state;
    }

    void onStart(@Observes StartupEvent ignored) {
        try {
            apply(readPersistedMode());
        } catch (Exception exception) {
            state.failure(exception);
            LOG.error("Failed to apply persisted integration mode", exception);
        }
    }

    public synchronized ControlSnapshot snapshot()
            throws Exception {
        IntegrationOperatingMode persisted = readPersistedMode();
        ServiceStatus routeStatus = camelContext
                .getRouteController()
                .getRouteStatus(INGRESS_ROUTE_ID);
        return new ControlSnapshot(
                persisted,
                state.admissionOpen(),
                state.recoveryComplete(),
                routeStatus == null ? "UNKNOWN" : routeStatus.name(),
                state.errorType(),
                readUpdatedAt()
        );
    }

    @Transactional
    public synchronized ControlSnapshot changeMode(
            IntegrationOperatingMode requested
    ) throws Exception {
        persist(requested);
        apply(requested);
        LOG.infov(
                "Integration operating mode changed: mode={0}, admissionOpen={1}",
                requested,
                state.admissionOpen()
        );
        return snapshot();
    }

    private void apply(IntegrationOperatingMode mode)
            throws Exception {
        switch (mode) {
            case ACTIVE -> {
                startOrResumeIngress();
                state.active();
            }
            case STANDBY -> {
                suspendOrStopIngress();
                state.standby();
            }
            case PULL_RESTART -> {
                suspendOrStopIngress();
                state.pullRestartPending();
            }
        }
    }

    private void startOrResumeIngress() throws Exception {
        ServiceStatus status = camelContext
                .getRouteController()
                .getRouteStatus(INGRESS_ROUTE_ID);
        if (status == null) {
            throw new IllegalStateException(
                    "Kafka ingress route is unavailable"
            );
        }
        if (status.isStarted()) {
            return;
        }
        if (status.isSuspended()) {
            camelContext.getRouteController().resumeRoute(INGRESS_ROUTE_ID);
            return;
        }
        camelContext.getRouteController().startRoute(INGRESS_ROUTE_ID);
    }

    private void suspendOrStopIngress() throws Exception {
        ServiceStatus status = camelContext
                .getRouteController()
                .getRouteStatus(INGRESS_ROUTE_ID);
        if (status == null || status.isStopped()) {
            return;
        }
        if (status.isStarted()) {
            camelContext.getRouteController().suspendRoute(INGRESS_ROUTE_ID);
        }
    }

    private IntegrationOperatingMode readPersistedMode()
            throws Exception {
        String sql = """
                SELECT operating_mode
                FROM event_management.integration_worker_control
                WHERE singleton_id = TRUE
                """;
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet resultSet = statement.executeQuery()) {
            if (!resultSet.next()) {
                throw new IllegalStateException(
                        "Integration worker control row is missing"
                );
            }
            return IntegrationOperatingMode.parse(resultSet.getString(1));
        }
    }

    private OffsetDateTime readUpdatedAt() throws Exception {
        String sql = """
                SELECT updated_at
                FROM event_management.integration_worker_control
                WHERE singleton_id = TRUE
                """;
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet resultSet = statement.executeQuery()) {
            if (!resultSet.next()) {
                return null;
            }
            return resultSet.getObject(1, OffsetDateTime.class);
        }
    }

    private void persist(IntegrationOperatingMode mode)
            throws Exception {
        String sql = """
                UPDATE event_management.integration_worker_control
                SET operating_mode = ?,
                    recovery_started_at = CASE
                        WHEN ? = 'PULL_RESTART' THEN CURRENT_TIMESTAMP
                        ELSE recovery_started_at
                    END,
                    recovery_completed_at = CASE
                        WHEN ? = 'PULL_RESTART' THEN NULL
                        ELSE recovery_completed_at
                    END,
                    last_error = NULL,
                    updated_at = CURRENT_TIMESTAMP
                WHERE singleton_id = TRUE
                """;
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, mode.name());
            statement.setString(2, mode.name());
            statement.setString(3, mode.name());
            if (statement.executeUpdate() != 1) {
                throw new IllegalStateException(
                        "Integration worker control row was not updated"
                );
            }
        }
    }

    public record ControlSnapshot(
            IntegrationOperatingMode operatingMode,
            boolean admissionOpen,
            boolean recoveryComplete,
            String ingressRouteStatus,
            String errorType,
            OffsetDateTime updatedAt
    ) {
    }
}
JAVAEOF

tee "${JAVA}/IntegrationOperationalControlResource.java" >/dev/null <<'JAVAEOF'
package com.eventmanagement.integration;

import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.HeaderParam;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.MediaType;
import org.eclipse.microprofile.config.inject.ConfigProperty;

@Path("/integration/control")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class IntegrationOperationalControlResource {

    private final IntegrationOperationalControl control;
    private final String controlToken;

    @Inject
    public IntegrationOperationalControlResource(
            IntegrationOperationalControl control,
            @ConfigProperty(
                    name = "integration.control.token"
            ) String controlToken
    ) {
        this.control = control;
        this.controlToken = controlToken;
    }

    @GET
    public IntegrationOperationalControl.ControlSnapshot get(
            @HeaderParam("X-Integration-Control-Token") String token
    ) throws Exception {
        authorize(token);
        return control.snapshot();
    }

    @PUT
    public IntegrationOperationalControl.ControlSnapshot put(
            @HeaderParam("X-Integration-Control-Token") String token,
            ModeRequest request
    ) throws Exception {
        authorize(token);
        if (request == null) {
            throw new WebApplicationException("Request body is required", 400);
        }
        return control.changeMode(
                IntegrationOperatingMode.parse(request.operatingMode())
        );
    }

    private void authorize(String token) {
        if (controlToken == null || controlToken.isBlank()) {
            throw new WebApplicationException(
                    "Control token is not configured",
                    503
            );
        }
        if (!constantTimeEquals(controlToken, token)) {
            throw new WebApplicationException("Unauthorized", 401);
        }
    }

    static boolean constantTimeEquals(String expected, String actual) {
        if (expected == null || actual == null) {
            return false;
        }
        byte[] left = expected.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        byte[] right = actual.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        return java.security.MessageDigest.isEqual(left, right);
    }

    public record ModeRequest(String operatingMode) {
    }
}
JAVAEOF

python3 - "${ROUTE}" <<'PY'
from pathlib import Path
import sys
p = Path(sys.argv[1])
s = p.read_text()
old = '<route id="integration-command-consumer">'
new = '<route id="integration-command-consumer" autoStartup="false">'
if old not in s:
    raise SystemExit('ROUTE_CONTROL_PATCH_STATUS=MARKER_NOT_FOUND')
p.write_text(s.replace(old, new, 1))
print('ROUTE_CONTROL_PATCH_STATUS=APPLIED')
PY

python3 - "${JAVA}/IntegrationWorkerReadinessCheck.java" <<'PY'
from pathlib import Path
import sys
p = Path(sys.argv[1])
s = p.read_text()
s = s.replace(
'''    private final long stabilizationMs;
    private final long startedAtNanos;
''',
'''    private final long stabilizationMs;
    private final long startedAtNanos;
    private final IntegrationOperationalState operationalState;
''')
s = s.replace(
'''            long stabilizationMs
    ) {
''',
'''            long stabilizationMs,
            IntegrationOperationalState operationalState
    ) {
''')
s = s.replace(
'''        this.stabilizationMs = stabilizationMs;
        this.startedAtNanos = System.nanoTime();
''',
'''        this.stabilizationMs = stabilizationMs;
        this.operationalState = operationalState;
        this.startedAtNanos = System.nanoTime();
''')
s = s.replace(
'''                kafkaAssigned &&
                stabilizationComplete;
''',
'''                kafkaAssigned &&
                stabilizationComplete &&
                operationalState.admissionOpen();
''')
s = s.replace(
'''                .withData(
                        "stabilizationMs",
                        stabilizationMs
                )
''',
'''                .withData(
                        "stabilizationMs",
                        stabilizationMs
                )
                .withData(
                        "operatingMode",
                        operationalState.mode().name()
                )
                .withData(
                        "admissionOpen",
                        operationalState.admissionOpen()
                )
                .withData(
                        "recoveryComplete",
                        operationalState.recoveryComplete()
                )
''')
if s == p.read_text():
    raise SystemExit('READINESS_CONTROL_PATCH_STATUS=NOT_APPLIED')
p.write_text(s)
print('READINESS_CONTROL_PATCH_STATUS=APPLIED')
PY

python3 - "${PROPS}" "${COMPOSE}" <<'PY'
from pathlib import Path
import sys
props = Path(sys.argv[1])
compose = Path(sys.argv[2])
s = props.read_text()
line = 'integration.control.token=${INTEGRATION_CONTROL_TOKEN:local-control-token}\n'
marker = 'integration.idempotency.lease-duration-ms=${INTEGRATION_IDEMPOTENCY_LEASE_DURATION_MS:60000}\n'
if line not in s:
    if marker not in s:
        raise SystemExit('CONTROL_PROPERTIES_PATCH_STATUS=MARKER_NOT_FOUND')
    props.write_text(s.replace(marker, marker + line, 1))

s = compose.read_text()
line = '      INTEGRATION_CONTROL_TOKEN: ${INTEGRATION_CONTROL_TOKEN:-local-control-token}\n'
marker = '      INTEGRATION_IDEMPOTENCY_LEASE_DURATION_MS: 60000\n'
if line not in s:
    if marker not in s:
        raise SystemExit('CONTROL_COMPOSE_PATCH_STATUS=MARKER_NOT_FOUND')
    s = s.replace(marker, marker + line, 1)

ready_probe = (
    'curl --fail --silent http://localhost:8083/health/ready '
    '>/dev/null || exit 1'
)
live_probe = (
    'curl --fail --silent http://localhost:8083/health/live '
    '>/dev/null || exit 1'
)
if ready_probe not in s:
    raise SystemExit('CONTROL_LIVENESS_PATCH_STATUS=MARKER_NOT_FOUND')
s = s.replace(ready_probe, live_probe, 1)
compose.write_text(s)
print('CONTROL_CONFIGURATION_PATCH_STATUS=APPLIED')
print('CONTROL_LIVENESS_PATCH_STATUS=APPLIED')
PY

tee "${TEST}/IntegrationOperationalStateTest.java" >/dev/null <<'JAVAEOF'
package com.eventmanagement.integration;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class IntegrationOperationalStateTest {

    @Test
    void shouldOpenAdmissionOnlyInActiveMode() {
        IntegrationOperationalState state = new IntegrationOperationalState();
        state.active();
        assertEquals(IntegrationOperatingMode.ACTIVE, state.mode());
        assertTrue(state.admissionOpen());
        assertTrue(state.recoveryComplete());

        state.standby();
        assertEquals(IntegrationOperatingMode.STANDBY, state.mode());
        assertFalse(state.admissionOpen());
        assertFalse(state.recoveryComplete());
    }

    @Test
    void shouldCloseAdmissionDuringPullRestart() {
        IntegrationOperationalState state = new IntegrationOperationalState();
        state.active();
        state.pullRestartPending();
        assertEquals(IntegrationOperatingMode.PULL_RESTART, state.mode());
        assertFalse(state.admissionOpen());
        assertFalse(state.recoveryComplete());
    }

    @Test
    void shouldParseModesCaseInsensitively() {
        assertEquals(
                IntegrationOperatingMode.PULL_RESTART,
                IntegrationOperatingMode.parse("pull_restart")
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> IntegrationOperatingMode.parse("invalid")
        );
    }

    @Test
    void shouldCompareControlTokenWithoutPlainEquality() {
        assertTrue(
                IntegrationOperationalControlResource.constantTimeEquals(
                        "secret",
                        "secret"
                )
        );
        assertFalse(
                IntegrationOperationalControlResource.constantTimeEquals(
                        "secret",
                        "wrong"
                )
        );
    }
}
JAVAEOF

echo
echo "===== APPLY MIGRATION 006 ====="
docker exec -i event-postgres \
  psql -U eventmanager -d eventmanagement \
  < "${MIGRATION}"
docker exec -i event-postgres \
  psql -U eventmanager -d eventmanagement \
  < "${MIGRATION}"
echo "ASSERTION=PASS | Migration 006 applied and repeated"

echo
echo "===== BUILD AND TEST ====="
(cd "${ROOT}" && mvn clean test)

echo
echo "===== COMPOSE VALIDATION ====="
docker compose --env-file .env -f "${COMPOSE}" config --quiet
echo "ASSERTION=PASS | Compose configuration valid"

echo
echo "===== STATIC CONTRACT ====="
grep -q 'autoStartup="false"' "${ROUTE}"
grep -q 'PULL_RESTART' "${JAVA}/IntegrationOperationalControl.java"
grep -q 'admissionOpen' "${JAVA}/IntegrationWorkerReadinessCheck.java"
grep -q '@Path("/integration/control")' "${JAVA}/IntegrationOperationalControlResource.java"
grep -q 'http://localhost:8083/health/live' "${COMPOSE}"
echo "ASSERTION=PASS | Kafka ingress starts under persistent control"
echo "ASSERTION=PASS | Readiness is gated by operational admission"
echo "ASSERTION=PASS | Standby preserves container liveness"
echo "ASSERTION=PASS | Authenticated control API installed"

echo
echo "===== DATABASE CONTROL ROW ====="
docker exec event-postgres \
  psql -U eventmanager -d eventmanagement -P pager=off \
  -c "SELECT * FROM event_management.integration_worker_control;"

echo
echo "===== CHANGESET ====="
git status --short
git diff --stat

if ! git diff --cached --quiet; then
  echo "ASSERTION=FAIL | Staging area must remain empty"
  exit 1
fi

echo
echo "===== FINAL RESULT ====="
echo "CONTROL_STATE_STORE=POSTGRES"
echo "MODES=STANDBY,ACTIVE,PULL_RESTART"
echo "DEFAULT_MIGRATION_MODE=ACTIVE"
echo "KAFKA_INGRESS_AUTOSTART=FALSE"
echo "CONTROL_API=/integration/control"
echo "PULL_RESTART_COORDINATOR=NEXT_CHECKPOINT"
echo "TECHNICAL_DEBT_SN_006=IN_PROGRESS"
echo "SN_02_9G_PERSISTENT_OPERATIONAL_CONTROL=PASS"
