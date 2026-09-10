package com.eventmanagement.processor.domain.routing;
import com.eventmanagement.processor.domain.ProcessingContext.CommandIntent;
import java.util.*;
public record RoutingResult(List<CommandIntent> commands,List<Decision> decisions,boolean failed) {
    public record Decision(String ruleId,int ruleVersion,String correlationRuleId,String reason,String commandId) {}
    public RoutingResult {commands=List.copyOf(commands);decisions=List.copyOf(decisions);}
    public static RoutingResult empty(){return new RoutingResult(List.of(),List.of(),false);}
}
