package com.eventmanagement.processor.domain.aiops;

public record AiopsAssessment(String recommendation, double confidence) {
    public AiopsAssessment {
        if(!java.util.Set.of("OBSERVE","INVESTIGATE").contains(recommendation)
                || !Double.isFinite(confidence) || confidence<0 || confidence>1)
            throw new IllegalArgumentException("INVALID_AIOPS_ASSESSMENT");
    }
}
