package com.eventmanagement.integration;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.apache.camel.CamelContext;
import org.apache.camel.ServiceStatus;
import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.AdminClientConfig;
import org.apache.kafka.clients.admin.ConsumerGroupDescription;
import org.apache.kafka.clients.admin.MemberDescription;
import org.apache.kafka.common.GroupState;
import org.apache.kafka.common.TopicPartition;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.eclipse.microprofile.health.HealthCheck;
import org.eclipse.microprofile.health.HealthCheckResponse;
import org.eclipse.microprofile.health.Readiness;

import java.util.Collection;
import java.util.Properties;
import java.util.concurrent.TimeUnit;

@Readiness
@ApplicationScoped
public class IntegrationWorkerReadinessCheck
        implements HealthCheck {

    static final String ROUTE_ID =
            "integration-command-consumer";

    private final CamelContext camelContext;
    private final String bootstrapServers;
    private final String consumerGroupId;
    private final String commandTopic;
    private final long timeoutMs;
    private final long stabilizationMs;
    private final long startedAtNanos;

    @Inject
    public IntegrationWorkerReadinessCheck(
            CamelContext camelContext,
            @ConfigProperty(
                    name = "kafka.bootstrap.servers"
            )
            String bootstrapServers,
            @ConfigProperty(
                    name = "kafka.consumer.group.id"
            )
            String consumerGroupId,
            @ConfigProperty(
                    name = "kafka.topic.integration.commands"
            )
            String commandTopic,
            @ConfigProperty(
                    name = "integration.readiness.timeout-ms",
                    defaultValue = "5000"
            )
            long timeoutMs,
            @ConfigProperty(
                    name = "integration.readiness.stabilization-ms",
                    defaultValue = "15000"
            )
            long stabilizationMs
    ) {
        this.camelContext = camelContext;
        this.bootstrapServers = bootstrapServers;
        this.consumerGroupId = consumerGroupId;
        this.commandTopic = commandTopic;
        this.timeoutMs = timeoutMs;
        this.stabilizationMs = stabilizationMs;
        this.startedAtNanos = System.nanoTime();
    }

    @Override
    public HealthCheckResponse call() {

        ServiceStatus routeStatus =
                camelContext
                        .getRouteController()
                        .getRouteStatus(ROUTE_ID);

        boolean routeStarted =
                routeStatus != null &&
                routeStatus.isStarted();

        boolean kafkaAssigned = false;
        boolean consumerGroupStable = false;
        String errorType = "none";

        Properties properties = new Properties();

        properties.put(
                AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG,
                bootstrapServers
        );

        properties.put(
                AdminClientConfig.REQUEST_TIMEOUT_MS_CONFIG,
                Long.toString(timeoutMs)
        );

        properties.put(
                AdminClientConfig.DEFAULT_API_TIMEOUT_MS_CONFIG,
                Long.toString(timeoutMs)
        );

        try (AdminClient adminClient =
                     AdminClient.create(properties)) {

            ConsumerGroupDescription group =
                    adminClient
                            .describeConsumerGroups(
                                    java.util.List.of(
                                            consumerGroupId
                                    )
                            )
                            .describedGroups()
                            .get(consumerGroupId)
                            .get(timeoutMs, TimeUnit.MILLISECONDS);

            consumerGroupStable =
                    isConsumerGroupStable(
                            group.groupState()
                    );

            kafkaAssigned = hasTopicAssignment(
                    group.members(),
                    commandTopic
            );
        } catch (Exception exception) {
            errorType =
                    exception.getClass().getSimpleName();
        }

        long elapsedNanos =
                System.nanoTime() -
                startedAtNanos;

        boolean stabilizationComplete =
                hasStabilized(
                        elapsedNanos,
                        stabilizationMs
                );

        boolean ready =
                routeStarted &&
                consumerGroupStable &&
                kafkaAssigned &&
                stabilizationComplete;

        return HealthCheckResponse
                .named("integration-worker-readiness")
                .status(ready)
                .withData(
                        "routeStarted",
                        routeStarted
                )
                .withData(
                        "kafkaConsumerAssigned",
                        kafkaAssigned
                )
                .withData(
                        "consumerGroupStable",
                        consumerGroupStable
                )
                .withData(
                        "stabilizationComplete",
                        stabilizationComplete
                )
                .withData(
                        "stabilizationMs",
                        stabilizationMs
                )
                .withData(
                        "consumerGroup",
                        consumerGroupId
                )
                .withData(
                        "commandTopic",
                        commandTopic
                )
                .withData(
                        "errorType",
                        errorType
                )
                .build();
    }

    static boolean isConsumerGroupStable(
            GroupState groupState
    ) {

        return GroupState.STABLE.equals(
                groupState
        );
    }

    static boolean hasStabilized(
            long elapsedNanos,
            long requiredMilliseconds
    ) {

        if (elapsedNanos < 0 ||
                requiredMilliseconds < 0) {

            return false;
        }

        long elapsedMilliseconds =
                TimeUnit.NANOSECONDS
                        .toMillis(elapsedNanos);

        return elapsedMilliseconds >=
                requiredMilliseconds;
    }

    static boolean hasTopicAssignment(
            Collection<MemberDescription> members,
            String expectedTopic
    ) {

        if (members == null ||
                members.isEmpty() ||
                expectedTopic == null ||
                expectedTopic.isBlank()) {

            return false;
        }

        return members.stream()
                .flatMap(
                        member -> member
                                .assignment()
                                .topicPartitions()
                                .stream()
                )
                .map(TopicPartition::topic)
                .anyMatch(expectedTopic::equals);
    }
}
