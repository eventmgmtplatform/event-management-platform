package com.eventmanagement.processor.application;
import com.eventmanagement.processor.domain.aiops.*;
import com.eventmanagement.processor.domain.admin.AdminFailure;
import com.eventmanagement.processor.ports.out.*;

/** Explicit REST invocation only. No automatic event pipeline or Worker side effects. */
public class AiopsEngine {
    private final AiopsConfigurations configurations;
    private final AiopsProvider provider;
    public AiopsEngine(AiopsConfigurations configurations,AiopsProvider provider) {
        this.configurations=configurations;this.provider=provider;
    }
    public record Result(String configurationId, long revision, String provider, AiopsAssessment assessment) {}
    public Result assess(String tenant, String id, AiopsSignal signal) {
        var configuration=configurations.get(tenant,id);
        if(!configuration.enabled())throw new AdminFailure(AdminFailure.Kind.CONFLICT,"AIOPS_DISABLED");
        return new Result(id,configuration.revision(),"INTERNAL_MOCK",provider.assess(tenant,signal));
    }
}
