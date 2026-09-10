package com.eventmanagement.processor.adapters.rules;

import com.eventmanagement.processor.domain.*;
import com.eventmanagement.processor.domain.rules.*;
import com.eventmanagement.processor.application.EventProcessingPipeline;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import java.time.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

public class BlackoutTest {
    static final Instant START=Instant.parse("2026-09-09T10:00:00Z"), END=START.plusSeconds(3600);
    public static String definition(String id,String tenant) {
        return """
            {"id":"%s","version":1,"type":"SCHEDULED","enabled":true,"priority":10,
             "scope":{"customerCode":"%s","node":"router-1"},
             "schedule":{"timezone":"America/Mexico_City","validFrom":"2026-09-09T10:00:00Z","validTo":"2026-09-09T11:00:00Z"},
             "reason":"Maintenance","metadata":{"owner":"operations"}}
            """.formatted(id,tenant);
    }
    private Rule rule(String id) {return new RuleCompiler().compile(definition(id,"tenant")).rule();}
    private Event event(Event.Status status,String node) {return new Event("id","key","tenant",status,3,START,"{}",Map.of("node",node));}
    @Test void startInclusiveEndExclusiveAndMissingSelectorsDoNotMatch() {
        var snapshot=new RuleSnapshot("tenant",List.of(rule("window")));var event=event(Event.Status.PROBLEM,"router-1");
        assertEquals(StageResult.Match.NO_MATCH,snapshot.evaluateBlackouts(event,START.minusNanos(1)).match());
        assertEquals(StageResult.Match.MATCH,snapshot.evaluateBlackouts(event,START).match());
        assertEquals(StageResult.Match.MATCH,snapshot.evaluateBlackouts(event,END.minusNanos(1)).match());
        assertEquals(StageResult.Match.NO_MATCH,snapshot.evaluateBlackouts(event,END).match());
        assertEquals(StageResult.Match.NO_MATCH,snapshot.evaluateBlackouts(event(Event.Status.PROBLEM,"other"),START).match());
        assertEquals(StageResult.Match.NO_MATCH,snapshot.evaluateBlackouts(new Event("id","key","tenant",Event.Status.PROBLEM,3,START,"{}"),START).match());
    }
    @Test void overlapKeepsEveryReasonAndUsesStablePriorityTieBreaker() {
        var result=new RuleSnapshot("tenant",List.of(rule("z"),rule("a"))).evaluateBlackouts(event(Event.Status.PROBLEM,"router-1"),START);
        assertEquals("a",result.ruleId());assertEquals("2",result.evidence().get("matchCount"));
        assertEquals("z",result.evidence().get("blackout.1.id"));
    }
    @Test void recoveryPolicyAndAuditContinueAndSimulationUsesTheSameClock() {
        var compiler=new RuleCompiler();
        var policy=compiler.compile(RuleCompilerTest.definition("policy",1,50,RuleCompilerTest.leaf("event.severity","GTE","2"),"CONTINUE")).rule();
        var snapshot=new RuleSnapshot("tenant",List.of(rule("window"),policy));
        var pipeline=EventProcessingPipeline.configured(t->snapshot,Clock.fixed(START,ZoneOffset.UTC));
        for(var status:Event.Status.values()) {
            var event=event(status,"router-1");var live=pipeline.process(event);var simulation=pipeline.simulate(event);
            assertEquals(live.stages(),simulation.stages());assertSame(event,live.event());
            assertEquals(StageResult.Directive.SUPPRESS_INTEGRATIONS,live.directive());
            assertEquals(StageResult.Status.SUCCESS,live.stages().get(8).status());
            assertEquals("1",live.stages().get(8).evidence().get("ruleCount"));
            assertEquals(StageResult.Status.SUCCESS,live.stages().get(11).status());assertTrue(live.candidates().isEmpty());
        }
        assertEquals(StageResult.Directive.CONTINUE,snapshot.evaluate(event(Event.Status.PROBLEM,"router-1")).directive());
    }
    @Test void invalidSchedulesUnsupportedRecurrenceAndSelectorsCannotActivate()throws Exception {
        var mapper=new ObjectMapper();var compiler=new RuleCompiler();
        for(String variant:List.of("zone","reverse","missing-start","recurring","tags","recurrence","missing-end")) {
            var node=(com.fasterxml.jackson.databind.node.ObjectNode)mapper.readTree(definition("window","tenant"));
            var schedule=(com.fasterxml.jackson.databind.node.ObjectNode)node.path("schedule");
            switch(variant) {
                case "zone"->schedule.put("timezone","invalid/zone");
                case "reverse"->schedule.put("validTo",START.minusSeconds(1).toString());
                case "missing-start"->schedule.remove("validFrom");
                case "missing-end"->schedule.remove("validTo");
                case "recurring"->node.put("type","RECURRING");
                case "recurrence"->schedule.put("recurrence","FREQ=DAILY");
                case "tags"->((com.fasterxml.jackson.databind.node.ObjectNode)node.path("scope")).putArray("tags").add("maintenance");
            }
            assertThrows(IllegalArgumentException.class,()->compiler.compile(node.toString()),variant);
        }
    }
    @Test void immediateCanHaveNoEndAndTimezoneOffsetChangesDoNotChangeInstants()throws Exception {
        var mapper=new ObjectMapper();var node=(com.fasterxml.jackson.databind.node.ObjectNode)mapper.readTree(definition("window","tenant"));
        node.put("type","IMMEDIATE");var schedule=(com.fasterxml.jackson.databind.node.ObjectNode)node.path("schedule");
        schedule.remove("validTo");schedule.put("timezone","America/New_York");schedule.put("validFrom","2026-11-01T01:30:00-04:00");
        var rule=new RuleCompiler().compile(node.toString()).rule();
        assertFalse(rule.blackout().windowMatches(Instant.parse("2026-11-01T05:29:59Z")));
        assertTrue(rule.blackout().windowMatches(Instant.parse("2026-11-01T06:30:00Z")));
    }
}
