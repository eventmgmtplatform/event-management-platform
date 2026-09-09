package com.eventmanagement.processor.domain;

import java.util.Map;
import java.util.Objects;

public record StageResult(Stage stage, Status status, Match match, Directive directive,
                          String reason, String ruleId, Integer ruleVersion,
                          long durationNanos, Map<String, String> evidence) {
    public enum Stage {
        ContractValidation, Normalization, ContextEnrichment, IdentityFingerprint,
        Deduplication, AutoSuppression, Blackout, Correlation, PolicyEvaluation,
        RoutingDecision, CommandGeneration, ProcessingAudit
    }
    public enum Status { SUCCESS, SKIPPED, FAILED }
    public enum Match { MATCH, NO_MATCH, NOT_APPLICABLE, ERROR }
    public enum Directive {
        CONTINUE, SUPPRESS_INTEGRATIONS, STATE_ONLY, CORRELATE_ONLY,
        GENERATE_COMMANDS, DEAD_LETTER
    }
    public StageResult {
        Objects.requireNonNull(stage); Objects.requireNonNull(status);
        Objects.requireNonNull(match); Objects.requireNonNull(directive);
        Objects.requireNonNull(reason); evidence = Map.copyOf(evidence);
        if (durationNanos < 0) throw new IllegalArgumentException("NEGATIVE_DURATION");
    }
    public static StageResult success(Stage stage, String reason) {
        return new StageResult(stage, Status.SUCCESS, Match.NOT_APPLICABLE,
                Directive.CONTINUE, reason, null, null, 0, Map.of());
    }
    public static StageResult pending(Stage stage, String reason) {
        return new StageResult(stage, Status.SKIPPED, Match.NOT_APPLICABLE,
                Directive.CONTINUE, reason, null, null, 0, Map.of("capabilityStatus", "PENDING"));
    }
}
