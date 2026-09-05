package com.eventmanagement.integration;

import jakarta.annotation.PreDestroy;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.apache.camel.CamelContext;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.ServiceStatus;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

@ApplicationScoped
public class PullRestartCoordinator {

    private static final Logger LOG = Logger.getLogger(
            PullRestartCoordinator.class
    );

    private static final String CORE_ENDPOINT =
            "direct:integration-command-core";

    private final DataSource dataSource;
    private final CamelContext camelContext;
    private final ProducerTemplate producerTemplate;
    private final IntegrationOperationalState state;
    private final long pollIntervalMs;
    private final int batchSize;
    private final AtomicBoolean running = new AtomicBoolean();
    private final ExecutorService executor =
            Executors.newSingleThreadExecutor(
                    Thread.ofPlatform()
                            .name("integration-pull-restart")
                            .daemon(true)
                            .factory()
            );

    @Inject
    public PullRestartCoordinator(
            DataSource dataSource,
            CamelContext camelContext,
            ProducerTemplate producerTemplate,
            IntegrationOperationalState state,
            @ConfigProperty(
                    name = "integration.pull-restart.poll-interval-ms",
                    defaultValue = "1000"
            )
            long pollIntervalMs,
            @ConfigProperty(
                    name = "integration.pull-restart.batch-size",
                    defaultValue = "100"
            )
            int batchSize
    ) {
        if (pollIntervalMs < 100) {
            throw new IllegalArgumentException(
                    "pull_restart poll interval must be at least 100 ms"
            );
        }
        if (batchSize < 1 || batchSize > 1000) {
            throw new IllegalArgumentException(
                    "pull_restart batch size must be between 1 and 1000"
            );
        }
        this.dataSource = dataSource;
        this.camelContext = camelContext;
        this.producerTemplate = producerTemplate;
        this.state = state;
        this.pollIntervalMs = pollIntervalMs;
        this.batchSize = batchSize;
    }

    public void start() {
        if (!running.compareAndSet(false, true)) {
            LOG.infov(
                    "pull_restart coordinator already active"
            );
            return;
        }
        executor.submit(this::recover);
    }

    public boolean running() {
        return running.get();
    }

    private void recover() {
        try {
            LOG.info(
                    "pull_restart recovery started; Kafka admission remains closed"
            );

            while (isPullRestartMode()) {
                List<String> expiredCommands = loadExpiredCommands();

                for (String commandPayload : expiredCommands) {
                    if (!isPullRestartMode()) {
                        return;
                    }
                    producerTemplate.requestBody(
                            CORE_ENDPOINT,
                            commandPayload
                    );
                }

                long pending = pendingCount();
                LOG.infov(
                        "pull_restart recovery pass: expiredProcessed={0}, pending={1}",
                        expiredCommands.size(),
                        pending
                );

                if (pending == 0) {
                    completeAndOpenAdmission();
                    return;
                }

                sleepPollInterval();
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            LOG.info("pull_restart coordinator interrupted");
        } catch (Exception exception) {
            state.failure(exception);
            persistFailure(exception);
            LOG.error(
                    "pull_restart recovery failed; Kafka admission remains closed",
                    exception
            );
        } finally {
            running.set(false);
        }
    }

    private List<String> loadExpiredCommands() throws Exception {
        String sql = """
                SELECT command_payload::text
                FROM event_management.integration_command_execution
                WHERE execution_status = 'IN_PROGRESS'
                  AND lease_expires_at <= CURRENT_TIMESTAMP
                ORDER BY lease_expires_at, claimed_at, command_id
                LIMIT ?
                """;

        List<String> commands = new ArrayList<>();
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, batchSize);
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    commands.add(resultSet.getString(1));
                }
            }
        }
        return commands;
    }

    private long pendingCount() throws Exception {
        String sql = """
                SELECT count(*)
                FROM event_management.integration_command_execution
                WHERE execution_status = 'IN_PROGRESS'
                """;
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet resultSet = statement.executeQuery()) {
            resultSet.next();
            return resultSet.getLong(1);
        }
    }

    private boolean isPullRestartMode() throws Exception {
        String sql = """
                SELECT operating_mode
                FROM event_management.integration_worker_control
                WHERE singleton_id = TRUE
                """;
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet resultSet = statement.executeQuery()) {
            return resultSet.next()
                    && IntegrationOperatingMode.PULL_RESTART.name()
                    .equals(resultSet.getString(1));
        }
    }

    private void completeAndOpenAdmission() throws Exception {
        String sql = """
                UPDATE event_management.integration_worker_control
                SET recovery_completed_at = CURRENT_TIMESTAMP,
                    last_error = NULL,
                    updated_at = CURRENT_TIMESTAMP
                WHERE singleton_id = TRUE
                  AND operating_mode = 'PULL_RESTART'
                """;

        int updated;
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            updated = statement.executeUpdate();
        }

        if (updated != 1 || !isPullRestartMode()) {
            LOG.info(
                    "pull_restart completion ignored because mode changed"
            );
            return;
        }

        ServiceStatus status = camelContext
                .getRouteController()
                .getRouteStatus(
                        IntegrationOperationalControl.INGRESS_ROUTE_ID
                );

        if (status == null) {
            throw new IllegalStateException(
                    "Kafka ingress route is unavailable"
            );
        }

        if (status.isSuspended()) {
            camelContext.getRouteController().resumeRoute(
                    IntegrationOperationalControl.INGRESS_ROUTE_ID
            );
        } else if (!status.isStarted()) {
            camelContext.getRouteController().startRoute(
                    IntegrationOperationalControl.INGRESS_ROUTE_ID
            );
        }

        state.pullRestartComplete();
        LOG.info(
                "pull_restart recovery completed; Kafka admission opened"
        );
    }

    private void persistFailure(Exception exception) {
        String sql = """
                UPDATE event_management.integration_worker_control
                SET last_error = ?,
                    updated_at = CURRENT_TIMESTAMP
                WHERE singleton_id = TRUE
                  AND operating_mode = 'PULL_RESTART'
                """;
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(
                    1,
                    exception.getClass().getSimpleName()
            );
            statement.executeUpdate();
        } catch (Exception persistenceException) {
            LOG.error(
                    "Unable to persist pull_restart failure",
                    persistenceException
            );
        }
    }

    private void sleepPollInterval() throws InterruptedException {
        Thread.sleep(Duration.ofMillis(pollIntervalMs));
    }

    @PreDestroy
    void shutdown() {
        executor.shutdownNow();
    }
}
