package com.eventmanagement.processor.adapters.rules;
import com.eventmanagement.processor.application.*;
import com.eventmanagement.processor.domain.*;
import com.eventmanagement.processor.domain.rules.*;
import org.junit.jupiter.api.Test;
import java.time.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
public class RoutingTest {
    public static String definition(String id,String group) {
        return """
            {"id":"%s","version":1,"type":"ROUTING","enabled":true,"priority":10,
             "condition":{"field":"event.severity","operator":"GTE","value":2},
             "actions":[{"type":"CREATE_TICKET","target":"SERVICENOW","parameters":{"configuration":"default","correlationRuleId":"%s"}}],
             "metadata":{"owner":"operations"}}
            """.formatted(id,group);
    }
    public static Event event(String id,String key,long seconds) {
        var e=CorrelationTest.event(id,key,Event.Status.PROBLEM,seconds);
        return new Event(e.eventId(),e.eventKey(),e.tenant(),e.status(),e.severity(),e.receivedAt(),e.originalJson(),Map.of("node","router-1","summary","Router unavailable"));
    }
    private Rule compile(String s){return new RuleCompiler().compile(s).rule();}
    @Test void explicitGroupRoutesProduceIndependentCommandsAndDeduplicateSameAction() {
        var snapshot=new RuleSnapshot("tenant",List.of(compile(CorrelationTest.definition("a",4)),compile(CorrelationTest.definition("b",4)),
                compile(definition("route-a","a")),compile(definition("route-b","b")),compile(definition("route-a-duplicate","a"))));
        var context=EventProcessingPipeline.configured(t->snapshot).usingCorrelation(new SimulatedCorrelation()).simulate(event("event","key",0));
        assertEquals(2,context.candidates().size());assertNotEquals(context.candidates().get(0).commandId(),context.candidates().get(1).commandId());
        assertEquals(StageResult.Directive.GENERATE_COMMANDS,context.directive());
        assertEquals("Router unavailable",context.candidates().getFirst().payload().get("summary"));
        var existing=EventProcessingPipeline.configured(t->snapshot).usingCorrelation(new SimulatedCorrelation()).usingCommands(id->true).simulate(event("event","key",0));
        assertTrue(existing.candidates().isEmpty());assertTrue(existing.routing().decisions().stream().allMatch(d->d.reason().equals("EXISTING_SEMANTIC_COMMAND")));
    }
    @Test void glpiAndServiceNowUseDistinctCommandIdentities() {
        var snapshot=new RuleSnapshot("tenant",List.of(compile(CorrelationTest.definition("a",4)),
            compile(definition("snow-route","a")),compile(definition("glpi-route","a").replace("SERVICENOW","GLPI"))));
        var context=EventProcessingPipeline.configured(t->snapshot).simulate(event("event","key",0));
        assertEquals(2,context.candidates().size());
        assertEquals(2,context.candidates().stream().map(c->c.commandId()).distinct().count());
        assertEquals(Set.of("SERVICENOW","GLPI"),context.candidates().stream().map(c->c.integrationType()).collect(java.util.stream.Collectors.toSet()));
    }
    @Test void suppressionPreventsCommandsButPreservesCorrelation() {
        var snapshot=new RuleSnapshot("tenant",List.of(compile(CorrelationTest.definition("a",4)),compile(definition("route","a")),compile(SuppressionTest.definition("change","APPROVED"))));
        var context=EventProcessingPipeline.configured(t->snapshot,Clock.fixed(Instant.parse("2026-09-09T10:00:00Z"),ZoneOffset.UTC)).simulate(event("event","key",0));
        assertTrue(context.candidates().isEmpty());assertTrue(context.correlation().decisions().getFirst().changed());
        assertEquals(StageResult.Directive.SUPPRESS_INTEGRATIONS,context.directive());
    }
    @Test void missingPayloadFailsExplicitlyAndNoCycleProducesNoCommand() {
        var snapshot=new RuleSnapshot("tenant",List.of(compile(CorrelationTest.definition("a",4)),compile(definition("route","a"))));
        var missing=EventProcessingPipeline.configured(t->snapshot).simulate(CorrelationTest.event("e","key",Event.Status.PROBLEM,0));
        assertEquals(StageResult.Directive.DEAD_LETTER,missing.directive());assertTrue(missing.candidates().isEmpty());
        assertEquals(StageResult.Status.SUCCESS,missing.stages().get(11).status());
        var noCycle=EventProcessingPipeline.configured(t->new RuleSnapshot(t,List.of(compile(definition("route","absent"))))).simulate(event("event","key",0));
        assertTrue(noCycle.candidates().isEmpty());assertEquals("NO_CORRELATION_CYCLE",noCycle.routing().decisions().getFirst().reason());
    }
    @Test void unimplementedOperationsAndUnresolvedProviderProfilesCannotActivate() {
        String valid=definition("route","a");
        for(String invalid:List.of(valid.replace("CREATE_TICKET","CLOSE_TICKET"),valid.replace("SERVICENOW","GNM"),valid.replace("default","other-profile"),
                valid.replace("\"correlationRuleId\":\"a\"","\"password\":\"unexpected\"")))
            assertThrows(IllegalArgumentException.class,()->compile(invalid));
    }
}
