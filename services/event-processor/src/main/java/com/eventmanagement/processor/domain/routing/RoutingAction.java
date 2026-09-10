package com.eventmanagement.processor.domain.routing;
public record RoutingAction(String integration,String operation,String configuration,String correlationRuleId) {}
