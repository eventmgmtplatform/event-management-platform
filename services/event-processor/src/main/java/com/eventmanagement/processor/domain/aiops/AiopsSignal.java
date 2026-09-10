package com.eventmanagement.processor.domain.aiops;

public record AiopsSignal(String resource, String summary, int severity) {
    public AiopsSignal {
        if(resource==null || resource.isBlank() || resource.length()>256 || summary==null
                || summary.isBlank() || summary.length()>1024 || severity<0 || severity>5)
            throw new IllegalArgumentException("INVALID_AIOPS_SIGNAL");
    }
}
