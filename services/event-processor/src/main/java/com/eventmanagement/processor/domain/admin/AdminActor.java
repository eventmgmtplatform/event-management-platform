package com.eventmanagement.processor.domain.admin;

/** Caller-supplied audit context, not an authenticated identity. Tenant partitions business data. */
public record AdminActor(String subject, String tenant) {
    public AdminActor {
        if(subject==null || !subject.matches("[A-Za-z0-9][A-Za-z0-9._:@-]{0,127}")
                || tenant==null || !tenant.matches("[A-Za-z0-9][A-Za-z0-9._:-]{0,127}"))
            throw new IllegalArgumentException("INVALID_REQUEST_CONTEXT");
    }
}
