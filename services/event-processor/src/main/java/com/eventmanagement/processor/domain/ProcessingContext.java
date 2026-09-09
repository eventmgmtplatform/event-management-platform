package com.eventmanagement.processor.domain;

import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Immutable, explicit inter-stage evidence, never arbitrary mutation of the event. */
public record ProcessingContext(Event event, String processingId, Mode mode,
                                List<StageResult> stages,
                                Map<String, String> enrichment,
                                List<CommandIntent> candidates,
                                StageResult.Directive directive) {
    public enum Mode { PRODUCTION, SIMULATION }
    public record CommandIntent(String commandId, String idempotencyKey,
                                String integrationType, String operation) {}
    public ProcessingContext {
        Objects.requireNonNull(event); Objects.requireNonNull(processingId);
        Objects.requireNonNull(mode); Objects.requireNonNull(directive);
        stages = List.copyOf(stages); enrichment = Map.copyOf(enrichment);
        candidates = List.copyOf(candidates);
    }
    public static ProcessingContext begin(Event event, String processingId, Mode mode) {
        return new ProcessingContext(event, processingId, mode, List.of(), Map.of(),
                List.of(), StageResult.Directive.CONTINUE);
    }
    public ProcessingContext append(StageResult result) {
        var next = new java.util.ArrayList<>(stages); next.add(result);
        return new ProcessingContext(event, processingId, mode, next, enrichment,
                candidates, DirectiveResolver.combine(directive, result.directive()));
    }
}
