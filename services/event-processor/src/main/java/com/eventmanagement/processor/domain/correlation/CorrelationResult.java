package com.eventmanagement.processor.domain.correlation;
import java.util.*;
public record CorrelationResult(List<Decision> decisions,boolean failed) {
    public record Decision(String ruleId,int ruleVersion,String reason,int candidateCount,CorrelationGroup group,boolean changed) {}
    public CorrelationResult {decisions=List.copyOf(decisions);}
    public static CorrelationResult empty(){return new CorrelationResult(List.of(),false);}
}
