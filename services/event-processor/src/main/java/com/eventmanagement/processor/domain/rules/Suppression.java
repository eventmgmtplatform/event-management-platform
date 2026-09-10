package com.eventmanagement.processor.domain.rules;
/** Imported maintenance/change eligibility, with no provider credentials or provider IO. */
public record Suppression(Blackout window,String source,String externalStatus,String externalReference) {
    public boolean eligible(){return externalStatus.equals("ACTIVE") || externalStatus.equals("APPROVED");}
}
