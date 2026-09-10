package com.eventmanagement.processor.domain.rules;

import com.eventmanagement.processor.domain.Event;
import java.time.*;
import java.util.*;

/** Validated finite/indefinite instant window; recurrence remains an explicit unsupported capability. */
public record Blackout(String type, Map<String,String> scope, Instant from, Instant to, ZoneId timezone, String reason) {
    public Blackout {
        scope=Map.copyOf(scope);Objects.requireNonNull(from);Objects.requireNonNull(timezone);Objects.requireNonNull(reason);
        if(!Set.of("IMMEDIATE","SCHEDULED").contains(type) || (type.equals("SCHEDULED") && to==null)
                || (to!=null && !from.isBefore(to)))throw new IllegalArgumentException("INVALID_BLACKOUT_WINDOW");
    }
    public boolean windowMatches(Instant now) { return !now.isBefore(from) && (to==null || now.isBefore(to)); }
    public boolean scopeMatches(Event event) {
        return scope.entrySet().stream().allMatch(e->e.getValue().equals(e.getKey().equals("customerCode")?event.tenant():event.selectors().get(e.getKey())));
    }
}
