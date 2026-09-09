package com.eventmanagement.processor.application;

import com.eventmanagement.processor.domain.*;
import com.eventmanagement.processor.ports.in.*;
import java.util.*;

public final class EventProcessingPipeline implements ProcessEventUseCase, SimulateEventUseCase {
    private final List<ProcessingStage> stages;
    public EventProcessingPipeline(List<ProcessingStage> stages) {
        this.stages = List.copyOf(stages);
        if (!this.stages.stream().map(ProcessingStage::stage).toList()
                .equals(List.of(StageResult.Stage.values())))
            throw new IllegalArgumentException("EXACT_STAGE_ORDER_REQUIRED");
    }
    @Override public ProcessingContext process(Event event) {
        return evaluate(event, ProcessingContext.Mode.PRODUCTION);
    }
    @Override public ProcessingContext simulate(Event event) {
        return evaluate(event, ProcessingContext.Mode.SIMULATION);
    }
    private ProcessingContext evaluate(Event event, ProcessingContext.Mode mode) {
        var context = ProcessingContext.begin(event,
                StableIdentity.of("processing-v1", event.tenant(), event.eventId()), mode);
        for (var stage : stages) {
            // Suppression never short-circuits recovery/state or hides later failures.
            StageResult result = context.directive() == StageResult.Directive.DEAD_LETTER
                    ? StageResult.pending(stage.stage(), "NOT_EXECUTED_AFTER_FAILURE")
                    : stage.evaluate(context);
            if (result.stage() != stage.stage()) throw new IllegalStateException("STAGE_ID_MISMATCH");
            context = context.append(result);
        }
        return context;
    }
    public static EventProcessingPipeline foundation() {
        return new EventProcessingPipeline(Arrays.stream(StageResult.Stage.values())
                .map(stage -> (ProcessingStage) new ProcessingStage() {
                    public StageResult.Stage stage() { return stage; }
                    public StageResult evaluate(ProcessingContext context) {
                        return switch(stage) {
                            case ContractValidation -> StageResult.success(stage, "GATEWAY_CONTRACT_VALIDATED");
                            case Normalization -> StageResult.success(stage, "COMPATIBLE_INTERNAL_VIEW");
                            case IdentityFingerprint -> StageResult.pending(stage, "GATEWAY_KEY_PRESERVED_FINGERPRINT_POLICY_PENDING");
                            case ProcessingAudit -> StageResult.success(stage, "ORDERED_EVIDENCE_PREPARED");
                            default -> StageResult.pending(stage, "FROZEN_ARTIFACT_OR_CAPABILITY_PENDING");
                        };
                    }
                }).toList());
    }
}
