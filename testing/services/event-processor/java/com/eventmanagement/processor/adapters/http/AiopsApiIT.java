package com.eventmanagement.processor.adapters.http;
import com.fasterxml.jackson.databind.*;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.common.http.TestHTTPResource;
import jakarta.inject.Inject;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import java.net.*;
import java.net.http.*;
import java.time.Duration;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

@QuarkusTest
@QuarkusTestResource(value=AiopsApiEnvironment.class,restrictToAnnotatedClass=true)
class AiopsApiIT {
    @TestHTTPResource URI root;
    @Inject DataSource source;
    final HttpClient client=HttpClient.newHttpClient();
    final ObjectMapper mapper=new ObjectMapper();
    HttpResponse<String> request(String method,String path,String tenant,Long revision,String body)throws Exception {
        var b=HttpRequest.newBuilder(root.resolve("/api/v1/aiops"+path)).timeout(Duration.ofSeconds(10)).header("Content-Type","application/json");
        if(tenant!=null)b.header("X-Tenant-Id",tenant);
        if(revision!=null)b.header("If-Match","\""+revision+"\"");
        return client.send(b.method(method,body==null?HttpRequest.BodyPublishers.noBody():HttpRequest.BodyPublishers.ofString(body)).build(),HttpResponse.BodyHandlers.ofString());
    }
    String tenant(){return "aiops-"+UUID.randomUUID();}
    String create(boolean enabled){return "{\"id\":\"bridge\",\"name\":\"Bridge prototype\",\"enabled\":"+enabled+"}";}
    String signal(String resource){return "{\"resource\":\""+resource+"\",\"summary\":\"Synthetic signal\",\"severity\":3}";}
    @Test void crudRevisionTenantIsolationAndImmutableAudit()throws Exception {
        String t=tenant();
        var created=request("POST","",t,0L,create(true));assertEquals(201,created.statusCode(),created.body());
        assertEquals("\"1\"",created.headers().firstValue("ETag").orElseThrow());
        assertEquals(409,request("POST","",t,0L,create(true)).statusCode());
        assertEquals(404,request("GET","/bridge",tenant(),null,null).statusCode());
        assertEquals(1,mapper.readTree(request("GET","",t,null,null).body()).size());
        assertEquals(428,request("PUT","/bridge",t,null,"{\"name\":\"Updated\",\"enabled\":false}").statusCode());
        assertEquals(200,request("PUT","/bridge",t,1L,"{\"name\":\"Updated\",\"enabled\":false}").statusCode());
        assertEquals(409,request("DELETE","/bridge",t,1L,null).statusCode());
        assertEquals(204,request("DELETE","/bridge",t,2L,null).statusCode());
        assertEquals(404,request("GET","/bridge",t,null,null).statusCode());
        assertEquals(0,mapper.readTree(request("GET","",t,null,null).body()).size());
        assertEquals(409,request("POST","",t,0L,create(true)).statusCode());
        try(var c=source.getConnection();var s=c.prepareStatement("SELECT count(*) FROM event_processor.aiops_change WHERE tenant=?")) {
            s.setString(1,t);try(var r=s.executeQuery()){r.next();assertEquals(3,r.getInt(1));}
            try(var mutation=c.prepareStatement("DELETE FROM event_processor.aiops_change WHERE tenant=?")) {
                mutation.setString(1,t);assertThrows(java.sql.SQLException.class,mutation::executeUpdate);
            }
        }
    }
    @Test void enabledConfigurationConsumesProviderWithoutEventSideEffects()throws Exception {
        String t=tenant();assertEquals(201,request("POST","",t,0L,create(true)).statusCode());
        var response=request("POST","/bridge/assessments",t,null,signal("node-1"));assertEquals(200,response.statusCode(),response.body());
        var json=mapper.readTree(response.body());assertEquals("INTERNAL_MOCK",json.path("provider").asText());
        assertEquals("INVESTIGATE",json.path("assessment").path("recommendation").asText());assertEquals(1,json.path("revision").asLong());
        try(var c=source.getConnection();var s=c.createStatement();var r=s.executeQuery("SELECT (SELECT count(*) FROM event_processor.processing_record)+(SELECT count(*) FROM event_processor.output_outbox)+(SELECT count(*) FROM event_processor.rule_definition)")) {
            r.next();assertEquals(0,r.getInt(1));
        }
    }
    @Test void disabledAndUnavailableAreExplicit()throws Exception {
        String t=tenant();assertEquals(201,request("POST","",t,0L,create(false)).statusCode());
        assertEquals(409,request("POST","/bridge/assessments",t,null,signal("node-1")).statusCode());
        assertEquals(200,request("PUT","/bridge",t,1L,"{\"name\":\"Bridge\",\"enabled\":true}").statusCode());
        var unavailable=request("POST","/bridge/assessments",t,null,signal("provider-down"));assertEquals(503,unavailable.statusCode());
        assertEquals("AIOPS_PROVIDER_UNAVAILABLE",mapper.readTree(unavailable.body()).path("code").asText());
    }
    @Test void invalidInputsDoNotCreateConfigurations()throws Exception {
        String t=tenant();assertEquals(400,request("GET","",null,null,null).statusCode());
        assertEquals(400,request("POST","",t,0L,create(true).replace("}",",\"endpoint\":\"http://external\"}")).statusCode());
        assertEquals(400,request("POST","",t,0L,"{\"id\":\"x\",\"id\":\"y\"}").statusCode());
        assertEquals(422,request("POST","",t,0L,create(true).replace("Bridge prototype","")).statusCode());
        assertEquals(422,request("GET","?limit=101",t,null,null).statusCode());
        assertEquals(0,mapper.readTree(request("GET","",t,null,null).body()).size());
    }
}
