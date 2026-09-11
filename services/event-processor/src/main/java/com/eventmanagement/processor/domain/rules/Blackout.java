package com.eventmanagement.processor.domain.rules;

import com.eventmanagement.processor.domain.Event;
import java.time.*;
import java.util.*;

/** Validated blackout window. Recurring windows use a bounded DAILY/WEEKLY rule. */
public record Blackout(String type, Map<String,String> scope, Instant from, Instant to, ZoneId timezone, String reason, String recurrence) {
    public Blackout {
        scope=Map.copyOf(scope);Objects.requireNonNull(from);Objects.requireNonNull(timezone);Objects.requireNonNull(reason);
        if(!Set.of("IMMEDIATE","SCHEDULED","RECURRING").contains(type) || (type.equals("SCHEDULED") && to==null) || (type.equals("RECURRING") && (recurrence==null || recurrence.isBlank() || to==null))
                || (to!=null && !from.isBefore(to)))throw new IllegalArgumentException("INVALID_BLACKOUT_WINDOW");
    }
    public boolean windowMatches(Instant now) {
        if(!"RECURRING".equals(type)) return !now.isBefore(from) && (to==null || now.isBefore(to));
        if(now.isBefore(from)) return false;
        long interval=1; boolean weekly=false; Set<DayOfWeek> days=EnumSet.noneOf(DayOfWeek.class);
        for(String part:recurrence.split(";")){String[] pair=part.split("=",2);if(pair.length!=2)continue;switch(pair[0]){case "FREQ"->weekly="WEEKLY".equals(pair[1]);case "INTERVAL"->interval=Math.max(1,Long.parseLong(pair[1]));case "BYDAY"-> {for(String d:pair[1].split(",")){try{days.add(DayOfWeek.valueOf(d));}catch(IllegalArgumentException ignored){}}}}}
        if(weekly&&!days.isEmpty()&&!days.contains(now.atZone(timezone).getDayOfWeek()))return false;
        long units=weekly?java.time.Duration.between(from,now).toDays()/7:java.time.Duration.between(from,now).toDays();
        if(units<0 || units%interval!=0)return false;
        Instant occurrence=from.plus(weekly?java.time.Duration.ofDays(units*7):java.time.Duration.ofDays(units));
        long duration=to==null?0:java.time.Duration.between(from,to).toMillis();
        return !now.isBefore(occurrence) && (duration==0 || now.isBefore(occurrence.plusMillis(duration)));
    }
    public boolean scopeMatches(Event event) {
        return scope.entrySet().stream().allMatch(e->e.getValue().equals(e.getKey().equals("customerCode")?event.tenant():event.selectors().get(e.getKey())));
    }
}
