package com.eventmanagement.processor.adapters.rules;
import com.eventmanagement.processor.application.*;
import com.eventmanagement.processor.domain.*;
import com.eventmanagement.processor.domain.rules.*;
import org.junit.jupiter.api.Test;
import java.time.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
public class SuppressionTest {
    public static String definition(String id,String status) {
        return BlackoutTest.definition(id,"tenant").replace("\"type\":\"SCHEDULED\"","\"type\":\"SUPPRESSION\",\"source\":\"CHANGE\",\"externalStatus\":\""+status+"\"");
    }
    @Test void approvedWindowsAndBlackoutsBothKeepEvidenceAndDoNotSkipRecovery() {
        var compiler=new RuleCompiler();var snapshot=new RuleSnapshot("tenant",List.of(compiler.compile(definition("change","APPROVED")).rule(),compiler.compile(BlackoutTest.definition("blackout","tenant")).rule()));
        var now=Instant.parse("2026-09-09T10:00:00Z");var event=new Event("close","key","tenant",Event.Status.OK,0,now,"{}",Map.of("node","router-1"));
        var result=EventProcessingPipeline.configured(t->snapshot,Clock.fixed(now,ZoneOffset.UTC)).simulate(event);
        assertEquals(StageResult.Match.MATCH,result.stages().get(5).match());assertEquals(StageResult.Match.MATCH,result.stages().get(6).match());
        assertEquals(StageResult.Directive.SUPPRESS_INTEGRATIONS,result.directive());assertTrue(result.event().recovery());
        assertEquals(StageResult.Status.SUCCESS,result.stages().get(7).status());assertEquals(StageResult.Status.SUCCESS,result.stages().get(11).status());
        assertEquals(StageResult.Match.NO_MATCH,snapshot.evaluateSuppressions(event,now.plusSeconds(3600)).match());
    }
    @Test void cancelledAndCompletedChangesNeverSuppressAndUnboundedWindowsAreRejected() {
        var event=new Event("event","key","tenant",Event.Status.PROBLEM,3,Instant.EPOCH,"{}",Map.of("node","router-1"));
        for(String status:List.of("CANCELLED","COMPLETED")) {
            var snapshot=new RuleSnapshot("tenant",List.of(new RuleCompiler().compile(definition("change",status)).rule()));
            assertEquals(StageResult.Match.NO_MATCH,snapshot.evaluateSuppressions(event,Instant.parse("2026-09-09T10:00:00Z")).match());
        }
        assertThrows(IllegalArgumentException.class,()->new RuleCompiler().compile(definition("change","APPROVED").replace("\"validTo\":\"2026-09-09T11:00:00Z\"","\"validTo\":null")));
    }
}
