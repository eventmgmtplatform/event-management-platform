package com.eventmanagement.processor.application;

import com.eventmanagement.processor.domain.*;
import com.eventmanagement.processor.ports.in.*;
import java.util.*;

public final class EventProcessingPipeline implements ProcessEventUseCase, SimulateEventUseCase {
    private final List<ProcessingStage> stages;
    private final java.time.Clock clock;
    private com.eventmanagement.processor.ports.out.CorrelationPort correlationPort=new SimulatedCorrelation();
    private boolean explicitCorrelationSession;
    private com.eventmanagement.processor.ports.out.CommandHistory commandHistory=id->false;
    private final com.eventmanagement.processor.ports.out.RuleSnapshots snapshots;
    public EventProcessingPipeline(List<ProcessingStage> stages) {
        this(stages, tenant -> new com.eventmanagement.processor.domain.rules.RuleSnapshot(tenant, List.of()));
    }
    public EventProcessingPipeline(List<ProcessingStage> stages, com.eventmanagement.processor.ports.out.RuleSnapshots snapshots) {
        this(stages,snapshots,java.time.Clock.systemUTC());
    }
    public EventProcessingPipeline(List<ProcessingStage> stages,com.eventmanagement.processor.ports.out.RuleSnapshots snapshots,java.time.Clock clock) {
        this.clock=Objects.requireNonNull(clock);
        this.snapshots=Objects.requireNonNull(snapshots);
        this.stages = List.copyOf(stages);
        if (!this.stages.stream().map(ProcessingStage::stage).toList()
                .equals(List.of(StageResult.Stage.values())))
            throw new IllegalArgumentException("EXACT_STAGE_ORDER_REQUIRED");
    }
    public EventProcessingPipeline usingCorrelation(com.eventmanagement.processor.ports.out.CorrelationPort port) {return usingCorrelation(port,snapshots);}
    public EventProcessingPipeline usingCorrelation(com.eventmanagement.processor.ports.out.CorrelationPort port,com.eventmanagement.processor.ports.out.RuleSnapshots snapshots) {
        var pipeline=new EventProcessingPipeline(stages,snapshots,clock);pipeline.correlationPort=Objects.requireNonNull(port);pipeline.explicitCorrelationSession=true;return pipeline;
    }
    public EventProcessingPipeline usingCommands(com.eventmanagement.processor.ports.out.CommandHistory history) {
        this.commandHistory=Objects.requireNonNull(history);return this;
    }
    @Override public ProcessingContext process(Event event) {
        return evaluate(event, ProcessingContext.Mode.PRODUCTION);
    }
    @Override public ProcessingContext simulate(Event event) {
        return evaluate(event, ProcessingContext.Mode.SIMULATION);
    }
    private ProcessingContext evaluate(Event event, ProcessingContext.Mode mode) {
        var context = ProcessingContext.begin(event,
                StableIdentity.of("processing-v1", event.tenant(), event.eventId()), mode).withSnapshot(snapshots.snapshot(event.tenant())).at(clock.instant());
        if(mode==ProcessingContext.Mode.PRODUCTION && !explicitCorrelationSession && context.ruleSnapshot().rules().stream().anyMatch(r->r.correlationRule()!=null))
            throw new IllegalStateException("CORRELATION_TRANSACTION_REQUIRED");
        for (var stage : stages) {
            // Suppression never short-circuits recovery/state or hides later failures.
            if(stage.stage()==StageResult.Stage.ContextEnrichment && context.directive()!=StageResult.Directive.DEAD_LETTER) {
                var enrichment=new EnrichmentEngine().evaluate(event,context.ruleSnapshot(),context.evaluatedAt(),new SnapshotInventory(context.ruleSnapshot()));
                context=context.enriched(enrichment);
            }
            if(stage.stage()==StageResult.Stage.Correlation && context.directive()!=StageResult.Directive.DEAD_LETTER)
                context=context.correlated(new CorrelationEngine().evaluate(context,correlationPort));
            if(stage.stage()==StageResult.Stage.RoutingDecision && context.directive()!=StageResult.Directive.DEAD_LETTER)
                context=context.routed(new RoutingEngine().evaluate(context,commandHistory));
            if(stage.stage()==StageResult.Stage.CommandGeneration && context.directive()!=StageResult.Directive.DEAD_LETTER)
                context=context.generatedCommands();
            StageResult result = context.directive() == StageResult.Directive.DEAD_LETTER && stage.stage() != StageResult.Stage.ProcessingAudit
                    ? StageResult.pending(stage.stage(), "NOT_EXECUTED_AFTER_FAILURE")
                    : stage.evaluate(context);
            if (result.stage() != stage.stage()) throw new IllegalStateException("STAGE_ID_MISMATCH");
            context = context.append(result);
        }
        return context;
    }
    public static EventProcessingPipeline foundation() {
        return configured(tenant -> new com.eventmanagement.processor.domain.rules.RuleSnapshot(tenant, List.of()));
    }
    public static EventProcessingPipeline configured(com.eventmanagement.processor.ports.out.RuleSnapshots snapshots) {
        return configured(snapshots,java.time.Clock.systemUTC());
    }
    public static EventProcessingPipeline configured(com.eventmanagement.processor.ports.out.RuleSnapshots snapshots,java.time.Clock clock) {
        return new EventProcessingPipeline(Arrays.stream(StageResult.Stage.values())
                .map(stage -> (ProcessingStage) new ProcessingStage() {
                    public StageResult.Stage stage() { return stage; }
                    public StageResult evaluate(ProcessingContext context) {
                        return switch(stage) {
                            case ContractValidation -> StageResult.success(stage, "GATEWAY_CONTRACT_VALIDATED");
                            case Normalization -> StageResult.success(stage, "COMPATIBLE_INTERNAL_VIEW");
                            case ContextEnrichment -> new EnrichmentEngine().stage(context.enrichment(),context.ruleSnapshot().checksum());
                            case IdentityFingerprint -> StageResult.pending(stage, "GATEWAY_KEY_PRESERVED_FINGERPRINT_POLICY_PENDING");
                            case AutoSuppression -> context.ruleSnapshot().evaluateSuppressions(context.event(),context.evaluatedAt());
                            case Blackout -> context.ruleSnapshot().evaluateBlackouts(context.event(),context.evaluatedAt());
                            case Correlation -> new CorrelationEngine().stage(context.correlation());
                            case PolicyEvaluation -> context.ruleSnapshot().evaluate(context.event(),context.enrichment().facts());
                            case RoutingDecision, CommandGeneration -> new RoutingEngine().stage(context.routing(),stage);
                            case ProcessingAudit -> StageResult.success(stage, "ORDERED_EVIDENCE_PREPARED");
                            default -> StageResult.pending(stage, "FROZEN_ARTIFACT_OR_CAPABILITY_PENDING");
                        };
                    }
                }).toList(), snapshots,clock);
    }
}
