package com.eventmanagement.processor.adapters.rules;
import com.eventmanagement.processor.domain.*;
import com.eventmanagement.processor.domain.rules.*;
import com.eventmanagement.processor.application.*;
import org.junit.jupiter.api.Test;
import java.time.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
public class CorrelationTest {
    public static String definition(String id,int max) {
        return """
            {"id":"%s","version":1,"enabled":true,"priority":1,"strategy":"ATTRIBUTE",
             "scope":{"field":"resource.node","operator":"EXISTS"},
             "candidateSelection":{"windowSeconds":60,"maxCandidates":%d,"activeOnly":true},
             "match":{"fields":["resource.node"]},"relationship":{"type":"GROUP"},"metadata":{"owner":"operations"}}
            """.formatted(id,max);
    }
    public static Event event(String id,String key,Event.Status status,long seconds) {
        return new Event(id,key,"tenant",status,status==Event.Status.PROBLEM?3:0,Instant.EPOCH.plusSeconds(seconds),id+":"+status+":"+seconds,Map.of("node","router-1"));
    }
    private ProcessingContext evaluate(SimulatedCorrelation state,Event event,int max) {
        var snapshot=new RuleSnapshot("tenant",List.of(new RuleCompiler().compile(definition("group",max)).rule()));
        var result=EventProcessingPipeline.configured(t->snapshot).usingCorrelation(state).simulate(event);
        if(result.directive()!=StageResult.Directive.DEAD_LETTER)state.apply("tenant",result.correlation());return result;
    }
    @Test void groupsResolveReoccurAndIgnoreLateOrTiedReopening() {
        var state=new SimulatedCorrelation();var a=evaluate(state,event("a","a",Event.Status.PROBLEM,0),4);
        var b=evaluate(state,event("b","b",Event.Status.PROBLEM,1),4);
        assertEquals(a.correlation().decisions().getFirst().group().groupId(),b.correlation().decisions().getFirst().group().groupId());
        evaluate(state,event("ar","a",Event.Status.OK,2),4);
        var closed=evaluate(state,event("br","b",Event.Status.RESOLVED,3),4);assertTrue(closed.correlation().decisions().getFirst().group().resolved());
        assertEquals("TIED_EVENT_IGNORED",evaluate(state,event("tied","a",Event.Status.PROBLEM,3),4).correlation().decisions().getFirst().reason());
        assertEquals("LATE_EVENT_IGNORED",evaluate(state,event("late","a",Event.Status.PROBLEM,1),4).correlation().decisions().getFirst().reason());
        var reopened=evaluate(state,event("again","a",Event.Status.PROBLEM,4),4);
        assertEquals(2,reopened.correlation().decisions().getFirst().group().cycle());
        assertNotEquals(a.correlation().decisions().getFirst().group().groupId(),reopened.correlation().decisions().getFirst().group().groupId());
    }
    @Test void candidateBoundsDoNotLoseExistingGroupAndRecoveryStillWorks() {
        var state=new SimulatedCorrelation();evaluate(state,event("a","a",Event.Status.PROBLEM,0),1);
        var rejected=evaluate(state,event("b","b",Event.Status.PROBLEM,1),1);assertEquals(StageResult.Directive.DEAD_LETTER,rejected.directive());
        assertEquals("CANDIDATE_LIMIT",rejected.correlation().decisions().getFirst().reason());
        var closed=evaluate(state,event("ar","a",Event.Status.OK,2),1);assertTrue(closed.correlation().decisions().getFirst().group().resolved());
    }
    @Test void expiryAndOrphanRecoveryHaveExplicitOutcomes() {
        var state=new SimulatedCorrelation();assertEquals("ORPHAN_RECOVERY",evaluate(state,event("orphan","a",Event.Status.OK,0),4).correlation().decisions().getFirst().reason());
        evaluate(state,event("a","a",Event.Status.PROBLEM,0),4);
        assertEquals(1,evaluate(state,event("b","b",Event.Status.PROBLEM,60),4).correlation().decisions().getFirst().group().cycle());
        assertEquals(2,evaluate(state,event("c","c",Event.Status.PROBLEM,121),4).correlation().decisions().getFirst().group().cycle());
    }
    @Test void configurationVersionChangesDoNotRecreateAnEquivalentGroupCycle() {
        var state=new SimulatedCorrelation();var first=evaluate(state,event("a","a",Event.Status.PROBLEM,0),4);
        var rule=new RuleCompiler().compile(definition("group",4).replace("\"version\":1","\"version\":2")).rule();
        var next=EventProcessingPipeline.configured(t->new RuleSnapshot(t,List.of(rule))).usingCorrelation(state).simulate(event("b","b",Event.Status.PROBLEM,1));
        assertEquals(first.correlation().decisions().getFirst().group().groupId(),next.correlation().decisions().getFirst().group().groupId());
        assertEquals(2,next.correlation().decisions().getFirst().ruleVersion());
    }
    @Test void unsupportedStrategiesUnsafeScopesAndLimitsAreRejected() {
        var compiler=new RuleCompiler();String valid=definition("group",4);
        for(String invalid:List.of(valid.replace("ATTRIBUTE","TOPOLOGICAL"),valid.replace("GROUP","PARENT_CHILD"),
                definition("group",33),valid.replace("\"windowSeconds\":60","\"windowSeconds\":0"),
                valid.replace("\"resource.node\"]","\"event.key\"]"),valid.replace("\"activeOnly\":true","\"activeOnly\":false"),
                valid.replace("\"operator\":\"EXISTS\"","\"operator\":\"EXISTS\",\"unsafe\":true")))
            assertThrows(IllegalArgumentException.class,()->compiler.compile(invalid));
    }
}
