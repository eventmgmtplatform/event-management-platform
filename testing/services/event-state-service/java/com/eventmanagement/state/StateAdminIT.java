package com.eventmanagement.state;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.common.http.TestHTTPResource;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.net.http.*;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

@QuarkusTest
@QuarkusTestResource(value=StateTestEnvironment.class,restrictToAnnotatedClass=true)
class StateAdminIT {
    @TestHTTPResource URI base;
    @Inject EventStateRepository repository;
    @Inject StateTransitionRepository transitions;
    @Inject ObjectMapper json;
    HttpResponse<String> get(String path,String tenant,String token)throws Exception {
        return HttpClient.newHttpClient().send(HttpRequest.newBuilder(base.resolve("/api/v1/state/"+path))
            .header("X-Tenant-Id",tenant).header("X-ESS-Admin-Token",token).GET().build(),HttpResponse.BodyHandlers.ofString());
    }
    String seed(String tenant)throws Exception {
        String key="admin:"+UUID.randomUUID();
        var input=IntegrationResultContractTest.fixture().put("tenant",tenant).put("eventKey",key).put("resultId",UUID.randomUUID().toString());
        repository.consolidate(input);return key;
    }
    @Test void missingTokenAndTenantSpoofingAreRejected()throws Exception {
        assertEquals(401,get("events","test-a","").statusCode());
        assertEquals(401,get("events","test-b","token-a").statusCode());
        assertEquals(401,get("events","test-a","operator-only").statusCode());
    }
    @Test void crossTenantGetDoesNotRevealExistence()throws Exception {
        String key=seed("test-b");
        assertEquals(404,get("event?eventKey="+key,"test-a","token-a").statusCode());
        var own=get("event?eventKey="+key,"test-b","token-b");
        assertEquals(200,own.statusCode());assertFalse(own.body().contains("last_result"));
        assertEquals("no-store",own.headers().firstValue("Cache-Control").orElseThrow());
    }
    @Test void listIsBoundedAndTenantScoped()throws Exception {
        seed("test-a");seed("test-a");seed("test-b");
        var data=json.readTree(get("events?limit=1","test-a","token-a").body());
        assertEquals(1,data.path("items").size());assertEquals("test-a",data.path("items").get(0).path("tenant").asText());
        String cursor=data.path("nextCursor").asText();assertFalse(cursor.isBlank());
        var next=json.readTree(get("events?limit=1&after="+URLEncoder.encode(cursor,StandardCharsets.UTF_8),"test-a","token-a").body());
        assertNotEquals(cursor,next.path("items").get(0).path("event_key").asText());
        assertEquals(400,get("events?limit=101","test-a","token-a").statusCode());
        assertEquals(400,get("history?eventKey=x&afterVersion=-1","test-a","token-a").statusCode());
    }
    @Test void historyIsTenantScopedAndOmitsPayload()throws Exception {
        String key="admin:"+UUID.randomUUID();
        var input=StateRequestTest.fixture().put("messageId",UUID.randomUUID().toString()).put("eventKey",key).put("tenantId","test-b");
        transitions.apply(StateRequest.parse(input));
        var denied=json.readTree(get("history?eventKey="+key,"test-a","token-a").body());assertEquals(0,denied.path("items").size());
        var own=get("history?eventKey="+key,"test-b","token-b");assertEquals(1,json.readTree(own.body()).path("items").size());assertFalse(own.body().contains("payload"));
    }
    @Test void quarantineRequiresOperatorAndNeverReturnsBodies()throws Exception {
        assertEquals(401,get("quarantine","test-a","token-a").statusCode());
        var response=get("quarantine","","operator-only");assertEquals(200,response.statusCode());
        assertFalse(response.body().contains("payload"));assertTrue(response.body().contains("operator-global"));
    }
}
