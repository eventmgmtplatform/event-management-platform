package com.eventmanagement.integration.cacf;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.apache.camel.CamelContext;
import org.eclipse.microprofile.health.*;

@Readiness @ApplicationScoped
public class CacfReadinessCheck implements HealthCheck {
    @Inject CacfSettings settings;
    @Inject AutomationRepository repository;
    @Inject CamelContext camel;
    public HealthCheckResponse call(){
        if(!settings.enabled)return HealthCheckResponse.up("cacf-disabled");
        try {
            repository.metrics();
            var status=camel.getRouteController().getRouteStatus("cacf-command-admission");
            return HealthCheckResponse.named("cacf-ready").status(status!=null && status.isStarted()).build();
        }catch(Exception e){return HealthCheckResponse.down("cacf-ready");}
    }
}
