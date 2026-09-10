package com.eventmanagement.gateway;

import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.apache.camel.Exchange;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.support.DefaultExchange;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class GatewayRulesTest {
    final ObjectMapper mapper = new ObjectMapper();
    final GatewayRuleEngine engine = new GatewayRuleEngine();
    JsonNode json(String value) throws Exception { return mapper.readTree(value); }
    JsonNode rule(String id, String stage, String actions) throws Exception {
        return json("{\"id\":\""+id+"\",\"stage\":\""+stage+"\",\"priority\":10,\"enabled\":true,\"match\":{},"+actions+"}");
    }
    GatewayRulePipeline pipeline() {
        var p = new GatewayRulePipeline(); p.mapper=mapper; p.engine=engine;
        p.validator=new EventValidationProcessor(mapper,new ZabbixMessageBusNormalizer(mapper)); return p;
    }
    Exchange exchange(String body) {
        var e = new DefaultExchange(new DefaultCamelContext()); e.getMessage().setBody(body); return e;
    }
    final String legacy = "{\"resource\":\"host\",\"summary\":\"alert\",\"severity\":5,\"status\":\"PROBLEM\"}";

    @Test void mapsBeforeValidationAndEnrichesWithoutChangingOriginalOrIdentity() throws Exception {
        String input="{\"host\":\"HOST\",\"summary\":\"alert\",\"severity\":5,\"status\":\"PROBLEM\"}";
        var rules=List.of(new GatewayRuleStore.Entry(2,rule("map","NORMALIZATION","\"copy\":{\"resource\":\"host\"}")),
                new GatewayRuleStore.Entry(1,rule("required","VALIDATION","\"required\":[\"resource\"]")),
                new GatewayRuleStore.Entry(3,rule("base","ENRICHMENT","\"set\":{\"customer\":\"SDC\",\"eventId\":\"cannot-overwrite\"}")));
        var e=exchange(input); pipeline().evaluate(e,rules);
        JsonNode result=json(e.getMessage().getBody(String.class));
        assertEquals(json(input),result.get("originalEvent"));
        assertEquals("host",result.at("/resource/name").asText());
        assertEquals("SDC",result.at("/enrichment/base/customer").asText());
        assertNotEquals("cannot-overwrite",result.path("eventId").asText());
        assertEquals(result.path("eventId").asText(),e.getMessage().getHeader("eventKey"));
        assertEquals(2,result.at("/gatewayRules/revisions/map").asInt());
        assertEquals(json("[\"map\",\"required\",\"base\"]"),result.at("/gatewayRules/applied"));
    }
    @Test void rejectsAdmissionOnlyOnExactMatch() throws Exception {
        ObjectNode r=(ObjectNode)rule("block","INGESTION","\"reject\":true");
        r.set("match",json("{\"source\":\"blocked\"}"));
        var p=pipeline();var rules=List.of(new GatewayRuleStore.Entry(1,r));
        p.evaluate(exchange(legacy),rules);
        assertThrows(IllegalArgumentException.class,()->p.evaluate(exchange(legacy.replace("{","{\"source\":\"blocked\",")),rules));
    }
    @Test void disabledRulesDoNotRunAndTieOrderingIsStable() throws Exception {
        var a=rule("a","NORMALIZATION","\"set\":{\"summary\":\"first\"}");
        var b=rule("b","NORMALIZATION","\"set\":{\"summary\":\"last\"}");
        var disabled=(ObjectNode)rule("blocked","INGESTION","\"reject\":true");disabled.put("enabled",false);
        var e=exchange(legacy);pipeline().evaluate(e,List.of(new GatewayRuleStore.Entry(1,b),new GatewayRuleStore.Entry(1,a),new GatewayRuleStore.Entry(1,disabled)));
        assertEquals("last",json(e.getMessage().getBody(String.class)).at("/alert/summary").asText());
    }
    @Test void dynamicValidationAndCanonicalValidationBothRemainMandatory() throws Exception {
        var p=pipeline(); var r=rule("required","VALIDATION","\"required\":[\"CustomerCode\"]");
        assertThrows(IllegalArgumentException.class,()->p.evaluate(exchange(legacy),List.of(new GatewayRuleStore.Entry(1,r))));
        assertThrows(IllegalArgumentException.class,()->p.evaluate(exchange("{}"),List.of()));
        assertThrows(IllegalArgumentException.class,()->p.evaluate(exchange("{broken"),List.of()));
    }
    @Test void preservesZabbixLifecycleAndKafkaKey() throws Exception {
        String input=java.nio.file.Files.readString(java.nio.file.Path.of("../../testing/fixtures/events/sdc/zabbix-messagebus-recovery.json"));
        var e=exchange(input);pipeline().evaluate(e,List.of(new GatewayRuleStore.Entry(1,rule("base","ENRICHMENT","\"set\":{\"location\":\"MX\"}"))));
        var n=json(e.getMessage().getBody(String.class));
        assertEquals("CLOSE",n.path("lifecycleAction").asText());assertEquals(0,n.path("effectiveSeverity").asInt());
        assertEquals(n.path("eventKey").asText(),e.getMessage().getHeader("eventKey"));assertEquals(json(input),n.get("originalEvent"));
    }
    @Test void rejectsUnknownActionsPathsAndInvalidRuleTypes() throws Exception {
        for (String action:List.of("\"set\":{\"x.y\":1}","\"set\":{\"x\":{}}","\"script\":\"run\"","\"copy\":{\"x\":\"/secret\"}")) {
            var r=rule("bad","NORMALIZATION",action);assertThrows(IllegalArgumentException.class,()->engine.validate(r));
        }
        var wrongStage=rule("bad","VALIDATION","\"set\":{\"x\":1}");
        assertThrows(IllegalArgumentException.class,()->engine.validate(wrongStage));
    }
    @Test void copyUsesSnapshotAndSetWinsWithinOneRule() throws Exception {
        var r=rule("map","NORMALIZATION","\"copy\":{\"a\":\"b\",\"b\":\"a\"},\"set\":{\"b\":\"fixed\"}");
        var input=(ObjectNode)json("{\"a\":\"A\",\"b\":\"B\"}");
        engine.apply(GatewayRuleEngine.Stage.NORMALIZATION,input,input,engine.ordered(List.of(r)),new ArrayList<>());
        assertEquals(json("{\"a\":\"B\",\"b\":\"fixed\"}"),input);
    }
    GatewayRulesApi api() {
        var api=new GatewayRulesApi(); api.engine=engine;api.pipeline=pipeline();api.adminKey=Optional.of("test-key");
        api.store=new GatewayRuleStore() {
            Entry current;
            @Override public List<Entry> list() { return current==null?List.of():List.of(current); }
            @Override public Entry save(JsonNode rule,long expected) {
                if ((current==null?0:current.revision())!=expected) throw new Conflict();
                return current=new Entry(expected+1,rule);
            }
        };return api;
    }
    Exchange request(String body,String method) {
        var e=exchange(body);e.getMessage().setHeader(Exchange.HTTP_METHOD,method);
        e.getMessage().setHeader("X-Gateway-Admin-Key","test-key");return e;
    }
    int status(Exchange e) { return e.getMessage().getHeader(Exchange.HTTP_RESPONSE_CODE,Integer.class); }
    @Test void adminIsClosedWithoutKeyAndRejectsWrongKey() {
        var api=api();var e=request("","GET");api.adminKey=Optional.empty();api.handle(e,"collection");assertEquals(503,status(e));
        api.adminKey=Optional.of("different");e=request("","GET");api.handle(e,"collection");assertEquals(401,status(e));
        assertNull(e.getMessage().getHeader("X-Gateway-Admin-Key"));
    }
    @Test void createsUpdatesAndRejectsStaleRevision() throws Exception {
        var api=api();String body=rule("base","ENRICHMENT","\"set\":{\"customer\":\"SDC\"}").toString();
        var e=request(body,"POST");api.handle(e,"collection");assertEquals(201,status(e));assertEquals("\"1\"",e.getMessage().getHeader("ETag"));
        for(int expectedStatus:List.of(200,409)) {
            e=request(body,"PUT");e.getMessage().setHeader("id","base");e.getMessage().setHeader("If-Match","\"1\"");
            api.handle(e,"item");assertEquals(expectedStatus,status(e));
        }
        e=request(body,"PUT");e.getMessage().setHeader("id","base");api.handle(e,"item");assertEquals(428,status(e));
    }
    @Test void simulationUsesCandidateRulesWithoutWritingCatalog() throws Exception {
        var api=api();var e=request("{\"event\":"+legacy+",\"rules\":["+rule("base","ENRICHMENT","\"set\":{\"site\":\"MX\"}")+ "]}","POST");
        api.handle(e,"simulate");assertEquals(200,status(e));
        assertEquals("MX",json(e.getMessage().getBody(String.class)).at("/event/enrichment/base/site").asText());assertTrue(api.store.list().isEmpty());
    }
    @Test void invalidJsonAndDuplicateMembersAreBadRequests() {
        var api=api();
        for(String body:List.of("{broken","{\"id\":\"a\",\"id\":\"b\"}","{} {}")) {
            var e=request(body,"POST");api.handle(e,"validate");assertEquals(400,status(e));
        }
    }
}
