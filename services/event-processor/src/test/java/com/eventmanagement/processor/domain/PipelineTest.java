package com.eventmanagement.processor.domain;

import com.eventmanagement.processor.application.*;
import org.junit.jupiter.api.Test;
import java.time.Instant;
import java.util.*;
import java.nio.file.*;
import static org.junit.jupiter.api.Assertions.*;

class PipelineTest {
    private Event event(Event.Status status) {
        return new Event("id","key","tenant",status,3,Instant.EPOCH,"{\"original\":true}");
    }
    @Test void exactOrderAndImmutableOriginal() {
        var event=event(Event.Status.PROBLEM);
        var context=EventProcessingPipeline.foundation().process(event);
        assertEquals(List.of(StageResult.Stage.values()),context.stages().stream().map(StageResult::stage).toList());
        assertSame(event,context.event()); assertEquals("{\"original\":true}",event.originalJson());
        assertThrows(UnsupportedOperationException.class,()->context.stages().clear());
    }
    @Test void wrongStageOrderRejected() {
        assertThrows(IllegalArgumentException.class,()->new EventProcessingPipeline(List.of()));
    }
    @Test void simulationUsesSameDecisionsAndStableReplayIdentity() {
        var pipeline=EventProcessingPipeline.foundation(); var event=event(Event.Status.PROBLEM);
        var live=pipeline.process(event);var simulation=pipeline.simulate(event);
        assertEquals(live.stages(),simulation.stages());assertEquals(live.processingId(),simulation.processingId());
        assertEquals(ProcessingContext.Mode.SIMULATION,simulation.mode());assertTrue(simulation.candidates().isEmpty());
        assertEquals(live.processingId(),pipeline.process(event).processingId());
    }
    @Test void businessMatchAndRecoveryDoNotShortCircuit() {
        for(var status:List.of(Event.Status.OK,Event.Status.RESOLVED)) {
            var stages=Arrays.stream(StageResult.Stage.values()).map(stage->(ProcessingStage)new ProcessingStage(){
                public StageResult.Stage stage(){return stage;}
                public StageResult evaluate(ProcessingContext context){
                    return stage==StageResult.Stage.Blackout
                        ? new StageResult(stage,StageResult.Status.SUCCESS,StageResult.Match.MATCH,
                            StageResult.Directive.SUPPRESS_INTEGRATIONS,"WINDOW", "b1",1,0,Map.of())
                        : StageResult.success(stage,"EXECUTED");
                }
            }).toList();
            var result=new EventProcessingPipeline(stages).process(event(status));
            assertEquals(12,result.stages().size()); assertTrue(result.event().recovery());
            assertEquals(StageResult.Status.SUCCESS,result.stages().get(11).status());
            assertEquals(StageResult.Directive.SUPPRESS_INTEGRATIONS,result.directive());
        }
    }
    @Test void failureDominatesSuppressionInEitherOrder() {
        assertEquals(StageResult.Directive.DEAD_LETTER,DirectiveResolver.combine(
                StageResult.Directive.SUPPRESS_INTEGRATIONS,StageResult.Directive.DEAD_LETTER));
        assertEquals(StageResult.Directive.DEAD_LETTER,DirectiveResolver.combine(
                StageResult.Directive.DEAD_LETTER,StageResult.Directive.SUPPRESS_INTEGRATIONS));
    }
    @Test void identityIsTenantSeparatedAndUnambiguous() {
        assertNotEquals(StableIdentity.of("x","a","bc"),StableIdentity.of("x","ab","c"));
        assertNotEquals(StableIdentity.of("x","tenant1","id"),StableIdentity.of("x","tenant2","id"));
    }
    @Test void domainAndApplicationHaveNoInfrastructureOrProviderDependencies() throws Exception {
        for(String directory:List.of("domain","application")) {
            try(var files=Files.walk(Path.of("src/main/java/com/eventmanagement/processor",directory))) {
                for(var file:files.filter(f->f.toString().endsWith(".java")).toList()) {
                    String source=Files.readString(file);
                    for(String forbidden:List.of("org.apache.camel","org.apache.kafka","io.quarkus","jakarta.",
                            "java.sql","javax.sql","java.net","adapters.","org.opensearch"))
                        assertFalse(source.contains(forbidden),file+" imports "+forbidden);
                }
            }
        }
    }
    @Test void pendingAlgorithmsNeverClaimSuccess() {
        var context=EventProcessingPipeline.foundation().process(event(Event.Status.PROBLEM));
        assertEquals(StageResult.Status.SKIPPED,context.stages().get(4).status());
        assertEquals("PENDING",context.stages().get(4).evidence().get("capabilityStatus"));
    }
}
