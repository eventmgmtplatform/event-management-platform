package com.eventmanagement.processor.domain.routing;
import java.util.Map;
public record RoutingAction(String integration,String operation,String configuration,String correlationRuleId,Map<String,String> lifecycle) {
    public RoutingAction(String i,String o,String c,String r){this(i,o,c,r,Map.of());}
    public RoutingAction { lifecycle=Map.copyOf(lifecycle); }
}
