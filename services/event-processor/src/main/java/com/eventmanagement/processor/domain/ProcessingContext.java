package com.eventmanagement.processor.domain;

import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Immutable, explicit inter-stage evidence, never arbitrary mutation of the event. */
public record ProcessingContext(Event event, String processingId, Mode mode,
                                List<StageResult> stages,
                                com.eventmanagement.processor.domain.enrichment.EnrichmentResult enrichment,
                                List<CommandIntent> candidates,
                                StageResult.Directive directive,
                                com.eventmanagement.processor.domain.rules.RuleSnapshot ruleSnapshot, java.time.Instant evaluatedAt) {
    public enum Mode { PRODUCTION, SIMULATION }
    public record CommandIntent(String commandId, String idempotencyKey,
                                String integrationType, String operation) {}
    public ProcessingContext {
        Objects.requireNonNull(event); Objects.requireNonNull(processingId);
        Objects.requireNonNull(mode); Objects.requireNonNull(directive);
        stages = List.copyOf(stages); Objects.requireNonNull(enrichment);
        candidates = List.copyOf(candidates);
    }
    public static ProcessingContext begin(Event event, String processingId, Mode mode) {
        return new ProcessingContext(event, processingId, mode, List.of(), com.eventmanagement.processor.domain.enrichment.EnrichmentResult.empty(),
                List.of(), StageResult.Directive.CONTINUE, new com.eventmanagement.processor.domain.rules.RuleSnapshot(event.tenant(), List.of()),event.receivedAt());
    }
    public ProcessingContext withSnapshot(com.eventmanagement.processor.domain.rules.RuleSnapshot snapshot) {
        if(!event.tenant().equals(snapshot.tenant())) throw new IllegalArgumentException("SNAPSHOT_TENANT_MISMATCH");
        return new ProcessingContext(event,processingId,mode,stages,enrichment,candidates,directive,snapshot,evaluatedAt);
    }
    public ProcessingContext at(java.time.Instant instant) {
        return new ProcessingContext(event,processingId,mode,stages,enrichment,candidates,directive,ruleSnapshot,Objects.requireNonNull(instant));
    }
    public ProcessingContext enriched(com.eventmanagement.processor.domain.enrichment.EnrichmentResult result) {
        return new ProcessingContext(event,processingId,mode,stages,result,candidates,directive,ruleSnapshot,evaluatedAt);
    }
    public ProcessingContext append(StageResult result) {
        var next = new java.util.ArrayList<>(stages); next.add(result);
        return new ProcessingContext(event, processingId, mode, next, enrichment,
                candidates, DirectiveResolver.combine(directive, result.directive()), ruleSnapshot,evaluatedAt);
    }
}
