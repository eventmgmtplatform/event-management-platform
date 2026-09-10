package com.eventmanagement.processor.domain.correlation;
import java.time.Instant;
import java.util.*;
/** Relationship lifecycle only; does not mutate or replace the Event State Service's event lifecycle. */
public record CorrelationGroup(String key,String groupId,int cycle,long revision,Instant updatedAt,boolean resolved,List<Member> members) {
    public record Member(String eventKey,String eventId,Instant observedAt,String status) {}
    public CorrelationGroup {members=List.copyOf(members);
        if(cycle<1 || revision<1 || members.size()>32 || members.stream().map(Member::eventKey).distinct().count()!=members.size())throw new IllegalArgumentException("INVALID_CORRELATION_GROUP");}
}
