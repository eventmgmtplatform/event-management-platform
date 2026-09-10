package com.eventmanagement.processor.domain.aiops;

/** Independent administrative model; not an event or rule contract. */
public record AiopsConfiguration(String id, String name, boolean enabled, long revision) {
    public AiopsConfiguration {
        if(id==null || !id.matches("[A-Za-z0-9][A-Za-z0-9._-]{0,63}") || name==null
                || name.isBlank() || name.length()>128 || revision<0)
            throw new IllegalArgumentException("INVALID_AIOPS_CONFIGURATION");
    }
}
