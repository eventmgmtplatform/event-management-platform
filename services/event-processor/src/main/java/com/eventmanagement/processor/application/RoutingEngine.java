package com.eventmanagement.processor.application;
import com.eventmanagement.processor.domain.*;
import com.eventmanagement.processor.domain.routing.*;
import com.eventmanagement.processor.ports.out.CommandHistory;
import java.util.*;
/** Group-cycle create commands only. Provider execution remains solely in Integration Worker. */
public final class RoutingEngine {
    public RoutingResult evaluate(ProcessingContext context,CommandHistory history) {
        var decisions=new ArrayList<RoutingResult.Decision>();var commands=new LinkedHashMap<String,ProcessingContext.CommandIntent>();boolean failed=false;
        for(var rule:context.ruleSnapshot().rules()) {
            if(rule.routes().isEmpty())continue;
            boolean match;
            try {match=rule.condition().matches(context.event(),context.enrichment().facts(),new ArrayList<>());}
            catch(IllegalArgumentException error){failed=true;decisions.add(new RoutingResult.Decision(rule.id(),rule.version(),"","ROUTE_INPUT_INVALID",null));continue;}
            for(var route:rule.routes()) {
                String reason=null;
                if(!match)reason="CONDITION_NO_MATCH";
                else if(context.directive()!=StageResult.Directive.CONTINUE && context.directive()!=StageResult.Directive.GENERATE_COMMANDS)reason="DIRECTIVE_"+context.directive();
                else if(context.event().recovery())reason="RECOVERY_OPERATION_NOT_IMPLEMENTED";
                var relationship=context.correlation().decisions().stream().filter(d->d.ruleId().equals(route.correlationRuleId()) && d.changed() && d.group()!=null && !d.group().resolved()).findFirst().orElse(null);
                if(reason==null && relationship==null)reason="NO_CORRELATION_CYCLE";
                if(reason!=null){decisions.add(new RoutingResult.Decision(rule.id(),rule.version(),route.correlationRuleId(),reason,null));continue;}
                String cycle=relationship.group().groupId();
                String id=StableIdentity.of("integration-intent-v1",context.event().tenant(),"correlation:"+cycle,cycle,route.configuration(),route.integration(),route.operation());
                if(commands.containsKey(id) || history.exists(id)){decisions.add(new RoutingResult.Decision(rule.id(),rule.version(),route.correlationRuleId(),"EXISTING_SEMANTIC_COMMAND",id));continue;}
                String resource=context.event().selectors().get("node"),summary=context.event().selectors().get("summary");
                if(resource==null || resource.isBlank() || resource.length()>4096 || summary==null || summary.isBlank() || summary.length()>4096) {
                    failed=true;decisions.add(new RoutingResult.Decision(rule.id(),rule.version(),route.correlationRuleId(),"COMMAND_PAYLOAD_FIELDS_REQUIRED",id));continue;
                }
                commands.put(id,new ProcessingContext.CommandIntent(id,id,route.integration(),route.operation(),route.configuration(),cycle,
                        Map.of("resource",resource,"summary",summary,"severity",context.event().severity())));
                decisions.add(new RoutingResult.Decision(rule.id(),rule.version(),route.correlationRuleId(),"COMMAND_ELIGIBLE",id));
            }
        }
        return new RoutingResult(failed?List.of():List.copyOf(commands.values()),decisions,failed);
    }
    public StageResult stage(RoutingResult result,StageResult.Stage stage) {
        return new StageResult(stage,result.failed()?StageResult.Status.FAILED:StageResult.Status.SUCCESS,
                result.failed()?StageResult.Match.ERROR:result.commands().isEmpty()?StageResult.Match.NO_MATCH:StageResult.Match.MATCH,
                result.failed()?StageResult.Directive.DEAD_LETTER:result.commands().isEmpty()?StageResult.Directive.CONTINUE:StageResult.Directive.GENERATE_COMMANDS,
                result.failed()?"ROUTING_FAILED":result.commands().isEmpty()?"NO_ELIGIBLE_COMMANDS":"COMMAND_INTENTS_PREPARED",null,null,0,
                Map.of("commandCount",Integer.toString(result.commands().size())));
    }
}
