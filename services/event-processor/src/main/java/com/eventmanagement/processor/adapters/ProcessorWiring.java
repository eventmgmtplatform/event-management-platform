package com.eventmanagement.processor.adapters;

import com.eventmanagement.processor.application.EventProcessingPipeline;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Singleton;

@ApplicationScoped
public class ProcessorWiring {
    @Produces @Singleton
    EventProcessingPipeline pipeline(com.eventmanagement.processor.ports.out.RuleSnapshots snapshots) {
        return EventProcessingPipeline.configured(snapshots);
    }
    @Produces @Singleton
    com.eventmanagement.processor.adapters.rules.RuleCompiler compiler() {
        return new com.eventmanagement.processor.adapters.rules.RuleCompiler();
    }
    @Produces @Singleton
    com.eventmanagement.processor.application.AdminService adminService(
            com.eventmanagement.processor.ports.out.RuleAdministration repository,
            com.eventmanagement.processor.ports.out.RuleValidation validator,
            com.eventmanagement.processor.ports.out.RuleSnapshots snapshots) {
        return new com.eventmanagement.processor.application.AdminService(repository,validator,snapshots);
    }
}
