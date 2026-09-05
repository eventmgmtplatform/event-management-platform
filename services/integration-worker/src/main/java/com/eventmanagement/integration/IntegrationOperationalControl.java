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
    private final PullRestartCoordinator pullRestartCoordinator;

    @Inject
    public IntegrationOperationalControl(
            DataSource dataSource,
            CamelContext camelContext,
            IntegrationOperationalState state,
            PullRestartCoordinator pullRestartCoordinator
    ) {
        this.dataSource = dataSource;
        this.camelContext = camelContext;
        this.state = state;
        this.pullRestartCoordinator = pullRestartCoordinator;
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
        IntegrationOperatingMode current = readPersistedMode();
        if (current != requested) {
            persist(requested);
        }
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
                pullRestartCoordinator.start();
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
                    recovery_generation = CASE
                        WHEN ? = 'PULL_RESTART'
                            THEN recovery_generation + 1
                        ELSE recovery_generation
                    END,
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
            statement.setString(4, mode.name());
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
