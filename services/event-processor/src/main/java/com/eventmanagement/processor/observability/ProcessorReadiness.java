package com.eventmanagement.processor.observability;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import javax.sql.DataSource;
import java.time.Duration;
import java.util.Map;
import org.apache.kafka.clients.admin.AdminClient;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.eclipse.microprofile.health.*;

@Readiness
@ApplicationScoped
public class ProcessorReadiness implements HealthCheck {
    @Inject DataSource dataSource;
    @ConfigProperty(name="processor.admin.enabled") boolean adminEnabled;
    @ConfigProperty(name="processor.kafka.brokers") String brokers;
    @Override public HealthCheckResponse call() {
        try(var connection=dataSource.getConnection(); var statement=connection.createStatement()) {
            statement.setQueryTimeout(3);
            statement.executeQuery("SELECT processing_id FROM event_processor.processing_record LIMIT 0");
            statement.executeQuery("SELECT message_id, dispatch_sequence, attempts, next_attempt_at FROM event_processor.output_outbox LIMIT 0");
            statement.executeQuery("SELECT tenant,rule_id,active_version,revision FROM event_processor.rule_definition LIMIT 0");
            statement.executeQuery("SELECT checksum,definition FROM event_processor.rule_version LIMIT 0");
            statement.executeQuery("SELECT tenant,correlation_key,revision,document FROM event_processor.correlation_group LIMIT 0");
            statement.executeQuery("SELECT command_id,tenant,envelope FROM event_processor.integration_command LIMIT 0");
            if(adminEnabled) {
                statement.executeQuery("SELECT request_id,request_hash,response FROM event_processor.admin_request LIMIT 0");
                statement.executeQuery("SELECT actor,outcome FROM event_processor.admin_audit LIMIT 0");
            }
            try(var admin=AdminClient.create(Map.of("bootstrap.servers",brokers,
                    "request.timeout.ms","3000","default.api.timeout.ms","3000"))) {
                admin.describeCluster().clusterId().get(3,java.util.concurrent.TimeUnit.SECONDS);
            }
            return HealthCheckResponse.up("processor-durable-boundary");
        } catch(Exception failure) { return HealthCheckResponse.down("processor-durable-boundary"); }
    }
}
