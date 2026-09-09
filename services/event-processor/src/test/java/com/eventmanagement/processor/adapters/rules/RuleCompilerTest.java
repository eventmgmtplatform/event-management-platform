package com.eventmanagement.processor.adapters.rules;

import com.eventmanagement.processor.domain.*;
import com.eventmanagement.processor.domain.rules.*;
import com.eventmanagement.processor.application.EventProcessingPipeline;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;

public class RuleCompilerTest {
    private final RuleCompiler compiler=new RuleCompiler();
    public static String definition(String id,int version,int priority,String condition,String action) {
        return "{\"id\":\""+id+"\",\"version\":"+version+",\"type\":\"POLICY\",\"enabled\":true,\"priority\":"+priority+
                ",\"condition\":"+condition+",\"actions\":[{\"type\":\""+action+"\"}],\"metadata\":{\"owner\":\"test\"}}";
    }
    public static String leaf(String field,String operator,String value) {
        return "{\"field\":\""+field+"\",\"operator\":\""+operator+"\""+(value==null?"":",\"value\":"+value)+"}";
    }
    private Event event() {return new Event("node-42","key","tenant",Event.Status.PROBLEM,3,Instant.parse("2026-01-01T00:00:00Z"),"{}");}
    private Rule compile(String condition) {return compiler.compile(definition("r",1,1,condition,"STATE_ONLY")).rule();}
    private boolean matches(String condition) {return compile(condition).condition().matches(event(),new ArrayList<>());}
    @Test void allFifteenOperatorsUseTypedSemantics() {
        for(String operator:List.of("EQ","GTE","LTE")) assertTrue(matches(leaf("event.severity",operator,"3")),operator);
        for(String operator:List.of("NE","GT"))assertTrue(matches(leaf("event.severity",operator,"2")),operator);
        assertTrue(matches(leaf("event.severity","LT","4")));
        assertFalse(matches(leaf("event.severity","GT","10")),"numeric not lexicographic");
        assertTrue(matches(leaf("event.severity","IN","[1,3]")));
        assertTrue(matches(leaf("event.severity","NOT_IN","[1,2]")));
        for(String operator:List.of("CONTAINS","STARTS_WITH"))assertTrue(matches(leaf("event.identifier",operator,"\"node\"")));
        assertTrue(matches(leaf("event.identifier","ENDS_WITH","\"42\"")));
        assertTrue(matches(leaf("event.identifier","REGEX","\"node-[0-9]+\"")));
        assertTrue(matches(leaf("event.identifier","EXISTS",null)));
        assertFalse(matches(leaf("event.identifier","NOT_EXISTS",null)));
        assertTrue(matches(leaf("event.severity","BETWEEN","[3,5]")));
        assertTrue(matches(leaf("event.receivedAt","EQ","\"2025-12-31T18:00:00-06:00\"")));
    }
    @Test void booleanTreesAndNoMatchPreserveEvidence() {
        String a=leaf("event.severity","EQ","3"), b=leaf("event.status","EQ","\"OK\"");
        assertTrue(matches("{\"all\":["+a+",{\"not\":"+b+"}]}"));
        assertTrue(matches("{\"any\":["+a+","+b+"]}"));
        var trace=new ArrayList<String>();assertFalse(compile(b).condition().matches(event(),trace));
        assertEquals(List.of("event.status:EQ:NO_MATCH"),trace);
    }
    @Test void rejectsUnknownFieldsTypesActionsAndAmbiguousJson() {
        for(String condition:List.of(leaf("class.classLoader","EQ","\"x\""),leaf("event.severity","EQ","\"3\""),
                leaf("event.severity","IN","3"),leaf("event.severity","BETWEEN","[5,2]"),
                leaf("event.severity","BETWEEN","[1]"),leaf("event.identifier","GT","\"a\""),
                leaf("event.receivedAt","EQ","\"yesterday\""),leaf("event.identifier","EXISTS","null"),
                leaf("event.identifier","EQ","null"),leaf("event.severity","BOGUS","3")))
            assertThrows(IllegalArgumentException.class,()->compile(condition));
        String valid=definition("r",1,1,leaf("event.severity","EQ","3"),"CONTINUE");
        for(String bad:List.of(valid+" {}",valid.replace("\"id\":\"r\"","\"id\":\"r\",\"id\":\"s\""),
                valid.replace("CONTINUE","RUN_SCRIPT"),valid.replace("POLICY","ROUTING"),valid.replace("\"owner\":\"test\"","\"owner\":\"test\",\"password\":\"hidden\"")))
            assertThrows(IllegalArgumentException.class,()->compiler.compile(bad));
    }
    @Test void rejectsDangerousRegexAndLargeTreesBeforeSchemaRecursion() {
        for(String regex:List.of("(a+)+$","a*a*","a|b","[","a{999999}","x\n(a+)")) {
            String quoted;try{quoted=new ObjectMapper().writeValueAsString(regex);}catch(Exception e){throw new RuntimeException(e);}
            String value=quoted;assertThrows(IllegalArgumentException.class,()->compile(leaf("event.identifier","REGEX",value)));
        }
        String tree=leaf("event.severity","EQ","3");for(int i=0;i<35;i++)tree="{\"not\":"+tree+"}";
        String deep=tree;assertThrows(IllegalArgumentException.class,()->compile(deep));
    }
    @Test void canonicalChecksumIgnoresObjectOrderAndEquivalentNumericRepresentation() throws Exception {
        String json=definition("r",1,1,leaf("event.severity","EQ","3.00"),"CONTINUE");
        var first=compiler.compile(json);var second=compiler.compile(json.replace("3.00","3e0"));
        assertEquals(first.rule().checksum(),second.rule().checksum());
        var reordered=new ObjectMapper().readTree(json);var copy=new ObjectMapper().createObjectNode();
        var keys=new ArrayList<String>();reordered.fieldNames().forEachRemaining(keys::add);Collections.reverse(keys);
        for(String key:keys)copy.set(key,reordered.get(key));
        assertEquals(first.rule().checksum(),compiler.compile(copy.toString()).rule().checksum());
        assertEquals(first.rule().checksum(),compiler.compile(first.canonicalJson()).rule().checksum());
    }
    @Test void deterministicMultipleMatchesAndRestrictiveConflictResolution() {
        String condition=leaf("event.severity","GTE","2");
        Rule a=compiler.compile(definition("a",1,10,condition,"SUPPRESS_INTEGRATIONS")).rule();
        Rule b=compiler.compile(definition("b",1,10,condition,"STATE_ONLY")).rule();
        var first=new RuleSnapshot("tenant",List.of(b,a));var second=new RuleSnapshot("tenant",List.of(a,b));
        assertEquals(first.checksum(),second.checksum());assertEquals(first.evaluate(event()),second.evaluate(event()));
        assertEquals("a",first.evaluate(event()).evidence().get("rule.0.id"));
        assertEquals(StageResult.Directive.STATE_ONLY,first.evaluate(event()).directive());
        assertEquals("2",first.evaluate(event()).evidence().get("ruleCount"));
    }
    @Test void oneSnapshotPerEventAndSimulationUsesSameDomain() {
        var snapshot=new RuleSnapshot("tenant",List.of(compile(leaf("event.severity","EQ","3"))));
        var calls=new AtomicInteger();var pipeline=EventProcessingPipeline.configured(tenant->{calls.incrementAndGet();return snapshot;});
        var live=pipeline.process(event());assertEquals(1,calls.get());
        var simulation=pipeline.simulate(event());assertEquals(2,calls.get());
        assertEquals(live.stages(),simulation.stages());assertTrue(simulation.candidates().isEmpty());
        assertEquals(snapshot.checksum(),live.stages().get(8).evidence().get("snapshotChecksum"));
        assertThrows(IllegalArgumentException.class,()->new RuleSnapshot("other",snapshot.rules()).evaluate(event()));
    }
    @Test void oversizedRegexInputFailsExplicitlyRatherThanSilentlyNotMatching() {
        var rule=compile(leaf("event.identifier","REGEX","\"a+\""));
        var event=new Event("a".repeat(4097),"key","tenant",Event.Status.PROBLEM,3,Instant.EPOCH,"{}");
        var result=new RuleSnapshot("tenant",List.of(rule)).evaluate(event);
        assertEquals(StageResult.Directive.DEAD_LETTER,result.directive());
        assertEquals(StageResult.Match.ERROR,result.match());
        assertEquals(rule.checksum(),result.evidence().get("failedRuleChecksum"));
    }
    @Test void legacyWithoutTenantNeverGetsCustomerRules() {
        var e=new Event("id","key","",Event.Status.OK,0,Instant.EPOCH,"{}");
        var c=EventProcessingPipeline.foundation().process(e);
        assertEquals("TENANT_REQUIRED_FOR_RULES",c.stages().get(8).reason());
        assertEquals(StageResult.Directive.CONTINUE,c.directive());
    }
}
