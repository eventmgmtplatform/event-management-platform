package com.eventmanagement.processor.adapters.rules;
import com.eventmanagement.processor.domain.*;
import com.eventmanagement.processor.domain.rules.*;
import com.eventmanagement.processor.domain.enrichment.*;
import com.eventmanagement.processor.application.*;
import com.eventmanagement.processor.ports.out.InventoryPort;
import org.junit.jupiter.api.Test;
import java.time.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

public class EnrichmentTest {
    public static String plan(String id,boolean required) {
        return """
            {"id":"%s","version":1,"type":"ENRICHMENT","enabled":true,"priority":10,
             "condition":{"field":"resource.node","operator":"EXISTS"},
             "actions":[{"type":"LOOKUP_INVENTORY","parameters":{"required":%s}}],"metadata":{"owner":"operations"}}
            """.formatted(id,required);
    }
    public static String inventory(String id,int priority,String group) {
        return """
            {"id":"%s","version":1,"type":"INVENTORY","enabled":true,"priority":%d,
             "scope":{"node":"router-1"},"facts":{"assignment.group":"%s","resource.managed":true,"service.criticality":3},
             "metadata":{"owner":"operations"}}
            """.formatted(id,priority,group);
    }
    private Rule compile(String json){return new RuleCompiler().compile(json).rule();}
    private Event event(){return new Event("id","key","tenant",Event.Status.PROBLEM,3,Instant.EPOCH,"original",Map.of("node","router-1"));}
    @Test void inventoryFeedsPolicyWithoutChangingTheOriginalEventAndSimulationAgrees() {
        var policy=compile(RuleCompilerTest.definition("p",1,1,RuleCompilerTest.leaf("enrichment.resource.managed","EQ","true"),"STATE_ONLY"));
        var snapshot=new RuleSnapshot("tenant",List.of(compile(plan("lookup",true)),compile(inventory("ci-1",5,"network")),policy));
        var pipeline=EventProcessingPipeline.configured(t->snapshot,Clock.fixed(Instant.EPOCH,ZoneOffset.UTC));
        var event=event();var live=pipeline.process(event);var simulation=pipeline.simulate(event);
        assertSame(event,live.event());assertEquals("original",live.event().originalJson());
        assertEquals(live.enrichment(),simulation.enrichment());assertEquals(live.stages(),simulation.stages());
        assertEquals(EnrichmentResult.Status.SUCCESS,live.enrichment().status());
        assertEquals("network",live.enrichment().facts().get("assignment.group"));
        assertEquals(StageResult.Directive.STATE_ONLY,live.directive());assertEquals(3,live.enrichment().provenance().size());
        assertEquals("1",live.stages().get(8).evidence().get("ruleCount"));assertTrue(live.candidates().isEmpty());
        assertThrows(UnsupportedOperationException.class,()->live.enrichment().facts().clear());
    }
    @Test void conflictsHaveStableExplicitPrecedenceAndCompleteProvenance() {
        var snapshot=new RuleSnapshot("tenant",List.of(compile(plan("lookup",true)),compile(inventory("a",1,"low")),compile(inventory("z",10,"high"))));
        var result=new EnrichmentEngine().evaluate(event(),snapshot,Instant.EPOCH,new SnapshotInventory(snapshot));
        assertEquals("high",result.facts().get("assignment.group"));assertEquals(1,result.conflicts().size());
        assertEquals("z:1",result.conflicts().getFirst().selectedSource());
        assertEquals("a:1",result.conflicts().getFirst().rejectedSource());
    }
    @Test void missingAndUnavailableSourcesRespectLookupCriticalityAndAuditIsRetained() {
        for(boolean required:List.of(true,false)) {
            var snapshot=new RuleSnapshot("tenant",List.of(compile(plan("lookup",required))));
            var engine=new EnrichmentEngine();
            var absent=engine.evaluate(event(),snapshot,Instant.EPOCH,new SnapshotInventory(snapshot));
            assertEquals(required?EnrichmentResult.Status.FAILED:EnrichmentResult.Status.NOT_FOUND,absent.status());
            assertFalse(absent.degraded());assertTrue(absent.facts().isEmpty());
            var error=engine.evaluate(event(),snapshot,Instant.EPOCH,e->{throw new IllegalStateException("do not leak source details");});
            assertEquals(required?EnrichmentResult.Status.FAILED:EnrichmentResult.Status.PARTIAL,error.status());assertTrue(error.degraded());
            assertEquals("INVENTORY_UNAVAILABLE",error.lookups().getFirst().errorCode());
            var live=EventProcessingPipeline.configured(t->snapshot).process(event());
            assertEquals(required?StageResult.Directive.DEAD_LETTER:StageResult.Directive.CONTINUE,live.directive());
            assertEquals(StageResult.Status.SUCCESS,live.stages().get(11).status());
        }
    }
    @Test void skippedPlansNeverCallInventoryAndMultipleApplicablePlansShareOneLookup() {
        var snapshot=new RuleSnapshot("tenant",List.of(compile(plan("a",false)),compile(plan("b",true))));
        var calls=new java.util.concurrent.atomic.AtomicInteger();InventoryPort port=e->{calls.incrementAndGet();return new InventoryPort.Result(EnrichmentResult.LookupStatus.NOT_FOUND,List.of(),null);};
        var engine=new EnrichmentEngine();
        var absent=new Event("id","key","tenant",Event.Status.OK,0,Instant.EPOCH,"{}");
        var skipped=engine.evaluate(absent,snapshot,Instant.EPOCH,port);assertEquals(0,calls.get());
        assertTrue(skipped.lookups().stream().allMatch(l->l.status()==EnrichmentResult.LookupStatus.SKIPPED));
        engine.evaluate(event(),snapshot,Instant.EPOCH,port);assertEquals(1,calls.get());
        assertThrows(IllegalArgumentException.class,()->new SnapshotInventory(new RuleSnapshot("other",List.of())).lookup(event()));
    }
    @Test void unsupportedFactsWrongTypesAndCyclicPlansAreRejected() {
        for(String invalid:List.of(inventory("ci",1,"group").replace("assignment.group","password"),
                inventory("ci",1,"group").replace("\"resource.managed\":true","\"resource.managed\":\"true\""),
                inventory("ci",1,"group").replace("\"node\":\"router-1\"","\"customerCode\":\"tenant\""),
                plan("lookup",true).replace("resource.node","enrichment.resource.ciId"),
                plan("lookup",true).replace("LOOKUP_INVENTORY","RUN_SCRIPT")))
            assertThrows(IllegalArgumentException.class,()->compile(invalid));
        assertThrows(IllegalArgumentException.class,()->new InventoryPort.Record("id",1,1,"checksum",Map.of("resource.managed","wrong-type")));
    }
}
