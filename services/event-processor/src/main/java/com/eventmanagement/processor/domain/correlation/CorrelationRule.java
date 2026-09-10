package com.eventmanagement.processor.domain.correlation;
import com.eventmanagement.processor.domain.rules.Rule;
import java.util.List;
public record CorrelationRule(List<Rule.Field> fields,int windowSeconds,int maxCandidates) {
    public CorrelationRule {fields=List.copyOf(fields);}
}
