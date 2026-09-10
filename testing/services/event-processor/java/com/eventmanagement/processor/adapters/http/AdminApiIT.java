package com.eventmanagement.processor.adapters.http;

import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.common.http.TestHTTPResource;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.junit.jupiter.api.Test;
import jakarta.inject.Inject;
import javax.sql.DataSource;
import java.net.*;
import java.net.http.*;
import java.time.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

@QuarkusTest
@QuarkusTestResource(value=AdminApiEnvironment.class,restrictToAnnotatedClass=true)
class AdminApiIT {
    @TestHTTPResource URI root;
    @Inject DataSource dataSource;
    private final ObjectMapper mapper=new ObjectMapper();
    private final HttpClient client=HttpClient.newHttpClient();
    private HttpResponse<String> request(String method,String path,String tenant,String key,Long revision,JsonNode body)throws Exception {
        var builder=HttpRequest.newBuilder(root.resolve(path)).timeout(Duration.ofSeconds(15));
        if(tenant!=null)builder.header("X-Tenant-Id",tenant);
        if(key!=null)builder.header("Idempotency-Key",key);
        if(revision!=null)builder.header("If-Match","\""+revision+"\"");
        builder.header("Content-Type","application/json");
        return client.send(builder.method(method,body==null?HttpRequest.BodyPublishers.noBody():HttpRequest.BodyPublishers.ofString(body.toString())).build(),HttpResponse.BodyHandlers.ofString());
    }
    private ObjectNode create(String id,int version)throws Exception {
        String rule=com.eventmanagement.processor.adapters.rules.RuleCompilerTest.definition(id,version,5,
                com.eventmanagement.processor.adapters.rules.RuleCompilerTest.leaf("event.severity","GTE","2"),"STATE_ONLY");
        return mapper.createObjectNode().put("reason","HTTP test").set("rule",mapper.readTree(rule));
    }
    private ObjectNode transition(){return mapper.createObjectNode().put("version",1).put("reason","HTTP activation");}
    private String unique(){return "api-"+UUID.randomUUID();}
    @Test void tenantIsRequiredButAuthenticationIsNot()throws Exception {
        assertEquals(400,request("GET","/api/v1/rules",null,null,null,null).statusCode());
        assertEquals(200,request("GET","/api/v1/rules","tenant-a",null,null,null).statusCode());
    }
    @Test void lifecycleIdempotencyConcurrencyAndHistoryUseTheDatabaseTransaction()throws Exception {
        String id=unique(),tenant=unique(),admin=tenant,key=unique();var body=create(id,1);
        var first=request("POST","/api/v1/rules",admin,key,0L,body);assertEquals(201,first.statusCode(),first.body());
        assertEquals("\"1\"",first.headers().firstValue("ETag").orElseThrow());
        var replay=request("POST","/api/v1/rules",admin,key,0L,body);assertEquals(first.body(),replay.body());assertEquals(201,replay.statusCode());
        assertEquals(409,request("POST","/api/v1/rules",admin,key,0L,create(id,2)).statusCode());
        assertEquals(428,request("POST","/api/v1/rules/"+id+"/enable",admin,unique(),null,transition()).statusCode());
        assertEquals(409,request("POST","/api/v1/rules/"+id+"/enable",admin,unique(),0L,transition()).statusCode());
        assertEquals(200,request("POST","/api/v1/rules/"+id+"/enable",admin,unique(),1L,transition()).statusCode());
        var get=request("GET","/api/v1/rules/"+id,admin,null,null,null);assertEquals(200,get.statusCode());assertEquals("ENABLED",mapper.readTree(get.body()).path("status").asText());
        assertEquals(409,request("POST","/api/v1/rules",admin,unique(),1L,create(id,2)).statusCode());
        assertEquals(200,request("POST","/api/v1/rules/"+id+"/disable",admin,unique(),2L,transition()).statusCode());
        assertEquals(200,request("POST","/api/v1/rules/"+id+"/retire",admin,unique(),3L,transition()).statusCode());
        var history=request("GET","/api/v1/rules/"+id+"/history?limit=2",admin,null,null,null);
        assertEquals(2,mapper.readTree(history.body()).path("items").size());assertEquals(2,mapper.readTree(history.body()).path("next").asInt());
        assertEquals(4,count("admin_audit",tenant,"outcome='SUCCESS'"));assertEquals(1,count("rule_version",tenant,"true"));
    }
    @Test void tenantPartitionAppliesToRulesAndExplain()throws Exception {
        String tenant=unique(),id=unique(),a=tenant,b="other-tenant";
        assertEquals(201,request("POST","/api/v1/rules",a,unique(),0L,create(id,1)).statusCode());
        assertEquals(404,request("GET","/api/v1/rules/"+id+"?tenant="+tenant,b,null,null,null).statusCode());
        assertEquals(404,request("POST","/api/v1/rules/"+id+"/enable",b,unique(),1L,transition()).statusCode());
        String processingId="a".repeat(64);
        try(var c=dataSource.getConnection();var s=c.prepareStatement("INSERT INTO event_processor.processing_record(processing_id,input_hash,event_id,tenant,evidence) VALUES (?,'hash','event',?,'{\"recorded\":true}')")){
            s.setString(1,processingId);s.setString(2,tenant);s.executeUpdate();
        }
        assertEquals(200,request("GET","/api/v1/explain/"+processingId,a,null,null,null).statusCode());
        assertEquals(404,request("GET","/api/v1/explain/"+processingId,b,null,null,null).statusCode());
    }
    @Test void simulationUsesProductionEvaluatorWithoutWritingStateOrOutbox()throws Exception {
        String tenant=unique(),operator=tenant;var event=mapper.createObjectNode().put("schemaVersion","1.1").put("eventId",unique())
                .put("eventKey",unique()).put("lifecycleAction","OPEN").put("effectiveSeverity",3);
        event.putObject("tenant").put("code",tenant);event.putObject("timestamps").put("receivedAt",Instant.now().toString());
        var body=mapper.createObjectNode().set("event",event);((ObjectNode)body).set("candidateRule",create("candidate",1).path("rule"));
        ((ObjectNode)body.path("candidateRule")).put("enabled",false);
        var response=request("POST","/api/v1/simulations",operator,null,null,body);assertEquals(200,response.statusCode(),response.body());
        assertEquals("SIMULATION",mapper.readTree(response.body()).path("mode").asText());
        assertEquals("STATE_ONLY",mapper.readTree(response.body()).path("directive").asText());
        assertEquals(0,count("processing_record",tenant,"true"));assertEquals(0,count("rule_definition",tenant,"true"));
        assertEquals(0,count("admin_request",tenant,"true"));
        event.withObject("tenant").put("code","other-tenant");
        assertEquals(422,request("POST","/api/v1/simulations",operator,null,null,body).statusCode());
    }
    @Test void simultaneousRetriesProduceOneVersionAndOneSuccessAudit()throws Exception {
        String tenant=unique(),admin=tenant,key=unique();var body=create(unique(),1);
        try(var pool=java.util.concurrent.Executors.newFixedThreadPool(2)) {
            var start=new java.util.concurrent.CountDownLatch(1);
            java.util.concurrent.Callable<HttpResponse<String>> call=()->{start.await();return request("POST","/api/v1/rules",admin,key,0L,body);};
            var first=pool.submit(call);var second=pool.submit(call);start.countDown();
            var a=first.get(20,java.util.concurrent.TimeUnit.SECONDS);var b=second.get(20,java.util.concurrent.TimeUnit.SECONDS);
            assertEquals(201,a.statusCode());assertEquals(201,b.statusCode());assertEquals(a.body(),b.body());
        }
        assertEquals(1,count("rule_version",tenant,"true"));assertEquals(1,count("admin_request",tenant,"true"));
        assertEquals(1,count("admin_audit",tenant,"outcome='SUCCESS'"));
    }
    @Test void legacyStatusRemainsPublic()throws Exception {
        assertEquals(200,request("GET","/api/v1/enrichment",null,null,null,null).statusCode());
    }
    @Test void invalidRulesAndUnboundedReadsNeverBecomeWrites()throws Exception {
        String tenant=unique(),admin=tenant;var body=create(unique(),1);
        ((ObjectNode)body.path("rule").path("condition")).put("field","class.classLoader");
        assertEquals(422,request("POST","/api/v1/rules",admin,unique(),0L,body).statusCode());
        assertEquals(0,count("rule_definition",tenant,"true"));
        assertEquals(1,count("admin_audit",tenant,"outcome='REJECTED'"));
        assertEquals(422,request("GET","/api/v1/rules?limit=101",admin,null,null,null).statusCode());
    }
    @Test void versionedBlackoutIsAppliedByActiveSimulationAndDisableRemovesItsEffect()throws Exception {
        String tenant=unique(),id=unique();
        var body=mapper.createObjectNode().put("reason","Maintenance configuration");
        body.set("rule",mapper.readTree(com.eventmanagement.processor.adapters.rules.BlackoutTest.definition(id,tenant)));
        assertEquals(201,request("POST","/api/v1/rules",tenant,unique(),0L,body).statusCode());
        assertEquals(200,request("POST","/api/v1/rules/"+id+"/enable",tenant,unique(),1L,transition()).statusCode());
        var event=mapper.createObjectNode().put("schemaVersion","1.1").put("eventId",unique()).put("eventKey",unique())
                .put("lifecycleAction","CLOSE").put("effectiveSeverity",0);
        event.putObject("tenant").put("code",tenant);event.putObject("timestamps").put("receivedAt","2026-09-09T10:00:00Z");
        event.putObject("resource").put("name","router-1");
        var simulation=mapper.createObjectNode().set("event",event);
        var response=request("POST","/api/v1/simulations",tenant,null,null,simulation);
        assertEquals(200,response.statusCode(),response.body());
        assertEquals("SUPPRESS_INTEGRATIONS",mapper.readTree(response.body()).path("directive").asText());
        assertEquals("ACTIVE",mapper.readTree(response.body()).path("configurationSource").asText());
        assertEquals(0,count("processing_record",tenant,"true"));
        ((ObjectNode)simulation).put("evaluatedAt","2026-09-09T11:00:00Z");
        assertEquals("CONTINUE",mapper.readTree(request("POST","/api/v1/simulations",tenant,null,null,simulation).body()).path("directive").asText());
        ((ObjectNode)simulation).remove("evaluatedAt");
        assertEquals(200,request("POST","/api/v1/rules/"+id+"/disable",tenant,unique(),2L,transition()).statusCode());
        assertEquals("CONTINUE",mapper.readTree(request("POST","/api/v1/simulations",tenant,null,null,simulation).body()).path("directive").asText());
    }
    @Test void blackoutCannotTargetAnotherTenantOrReplacePolicyCapability()throws Exception {
        String tenant=unique(),id=unique();var body=mapper.createObjectNode().put("reason","Invalid scope");
        body.set("rule",mapper.readTree(com.eventmanagement.processor.adapters.rules.BlackoutTest.definition(id,"other-tenant")));
        assertEquals(422,request("POST","/api/v1/rules",tenant,unique(),0L,body).statusCode());
        assertEquals(0,count("rule_definition",tenant,"true"));
        assertEquals(201,request("POST","/api/v1/rules",tenant,unique(),0L,create(id,1)).statusCode());
        body.set("rule",mapper.readTree(com.eventmanagement.processor.adapters.rules.BlackoutTest.definition(id,tenant)));
        ((ObjectNode)body.path("rule")).put("version",2);
        assertEquals(422,request("POST","/api/v1/rules",tenant,unique(),1L,body).statusCode());
        assertEquals(1,count("rule_version",tenant,"true"));
    }
    @Test void inventoryPlanAndPolicyShareVersionedSnapshotAndSimulateWithoutWrites()throws Exception {
        String tenant=unique();
        var definitions=List.of(com.eventmanagement.processor.adapters.rules.EnrichmentTest.inventory("ci-1",10,"network"),
                com.eventmanagement.processor.adapters.rules.EnrichmentTest.plan("lookup",true),
                com.eventmanagement.processor.adapters.rules.RuleCompilerTest.definition("policy",1,1,
                        com.eventmanagement.processor.adapters.rules.RuleCompilerTest.leaf("enrichment.assignment.group","EQ","\"network\""),"STATE_ONLY"));
        var candidates=mapper.createArrayNode();
        for(String definition:definitions) {
            var rule=mapper.readTree(definition);candidates.add(rule);
            var body=mapper.createObjectNode().put("reason","Inventory integration test").set("rule",rule);
            assertEquals(201,request("POST","/api/v1/rules",tenant,unique(),0L,body).statusCode());
            assertEquals(200,request("POST","/api/v1/rules/"+rule.path("id").asText()+"/enable",tenant,unique(),1L,transition()).statusCode());
        }
        var event=mapper.createObjectNode().put("schemaVersion","1.1").put("eventId",unique()).put("eventKey",unique())
                .put("lifecycleAction","OPEN").put("effectiveSeverity",3);
        event.putObject("tenant").put("code",tenant);event.putObject("timestamps").put("receivedAt","2026-09-09T10:00:00Z");
        event.putObject("resource").put("name","router-1");
        var body=mapper.createObjectNode().set("event",event);
        var active=request("POST","/api/v1/simulations",tenant,null,null,body);assertEquals(200,active.statusCode(),active.body());
        var result=mapper.readTree(active.body());assertEquals("STATE_ONLY",result.path("directive").asText());
        assertEquals("SUCCESS",result.path("enrichment").path("status").asText());
        assertEquals("inventory:ci-1",result.path("enrichment").path("provenance").get(0).path("source").asText());
        // Canonical enrichment obeys the supplied DA-05 schema, including its closed root.
        try(var schemaFile=getClass().getResourceAsStream("/contracts/enrichment-result-v1.schema.json")) {
            var schema=com.networknt.schema.JsonSchemaFactory.getInstance(com.networknt.schema.SpecVersion.VersionFlag.V202012).getSchema(mapper.readTree(schemaFile));
            assertTrue(schema.validate(result.path("enrichment")).isEmpty());
        }
        ((ObjectNode)body).set("candidateRules",candidates);
        var simulation=request("POST","/api/v1/simulations",tenant,null,null,body);assertEquals(200,simulation.statusCode(),simulation.body());
        assertEquals(result.path("enrichment"),mapper.readTree(simulation.body()).path("enrichment"));
        assertEquals(0,count("processing_record",tenant,"true"));assertEquals(3,count("rule_version",tenant,"true"));
        // Activation controls inventory visibility; an unmet required plan produces an explicit failure.
        assertEquals(200,request("POST","/api/v1/rules/ci-1/disable",tenant,unique(),2L,transition()).statusCode());
        ((ObjectNode)body).remove("candidateRules");
        var missing=mapper.readTree(request("POST","/api/v1/simulations",tenant,null,null,body).body());
        assertEquals("FAILED",missing.path("enrichment").path("status").asText());assertEquals("DEAD_LETTER",missing.path("directive").asText());
        assertEquals("SUCCESS",missing.path("stages").get(11).path("status").asText());
    }
    @Test void sequenceSimulationCorrelatesSuppressesAndDeduplicatesCommandsWithoutProductionWrites()throws Exception {
        String tenant=unique();var body=mapper.createObjectNode();var events=body.putArray("events");
        for(int i=0;i<2;i++) {
            var event=events.addObject().put("schemaVersion","1.1").put("eventId","sequence-"+i).put("eventKey","key-"+i)
                    .put("lifecycleAction","OPEN").put("effectiveSeverity",3).put("summary","Router unavailable");
            event.putObject("tenant").put("code",tenant);event.putObject("timestamps").put("receivedAt","2026-09-09T10:00:0"+i+"Z");
            event.putObject("resource").put("name","router-1");
        }
        var candidates=body.putArray("candidateRules");
        candidates.add(mapper.readTree(com.eventmanagement.processor.adapters.rules.CorrelationTest.definition("group",4)));
        candidates.add(mapper.readTree(com.eventmanagement.processor.adapters.rules.RoutingTest.definition("route","group")));
        var response=request("POST","/api/v1/simulations",tenant,null,null,body);assertEquals(200,response.statusCode(),response.body());
        var result=mapper.readTree(response.body()).path("results");assertEquals(1,result.get(0).path("candidates").size());assertEquals(0,result.get(1).path("candidates").size());
        var first=result.get(0).path("correlation").path("decisions").get(0).path("group");
        var second=result.get(1).path("correlation").path("decisions").get(0).path("group");
        assertEquals(first.path("groupId"),second.path("groupId"));assertEquals(2,second.path("members").size());
        assertEquals(0,count("correlation_group",tenant,"true"));assertEquals(0,count("integration_command",tenant,"true"));assertEquals(0,count("processing_record",tenant,"true"));
        candidates.add(mapper.readTree(com.eventmanagement.processor.adapters.rules.SuppressionTest.definition("change","APPROVED").replace("\"customerCode\":\"tenant\"","\"customerCode\":\""+tenant+"\"")));
        var suppressed=mapper.readTree(request("POST","/api/v1/simulations",tenant,null,null,body).body()).path("results");
        assertEquals("SUPPRESS_INTEGRATIONS",suppressed.get(0).path("directive").asText());assertEquals(0,suppressed.get(0).path("candidates").size());
        assertEquals(2,suppressed.get(1).path("correlation").path("decisions").get(0).path("group").path("members").size());
    }
    private long count(String table,String tenant,String extra)throws Exception {
        try(var c=dataSource.getConnection();var s=c.prepareStatement("SELECT count(*) FROM event_processor."+table+" WHERE tenant=? AND "+extra)){
            s.setString(1,tenant);try(var r=s.executeQuery()){r.next();return r.getLong(1);}
        }
    }
}
