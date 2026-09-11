package com.eventmanagement.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import java.net.URI;
import java.net.http.*;
import java.time.Duration;

/** GLPI REST V1 transport. Credentials never enter Kafka payloads or browser responses. */
@ApplicationScoped
public class GlpiHttpClient {
    private final ObjectMapper mapper;
    private final String base, appToken, userToken;
    private final Duration timeout;
    private final HttpClient client;
    @Inject
    public GlpiHttpClient(ObjectMapper mapper,
            @ConfigProperty(name="integration.glpi.base-url", defaultValue="http://glpi-mock:8080/apirest.php") String base,
            @ConfigProperty(name="integration.glpi.app-token", defaultValue="local-app") String appToken,
            @ConfigProperty(name="integration.glpi.user-token", defaultValue="local-user") String userToken,
            @ConfigProperty(name="integration.glpi.timeout-ms", defaultValue="5000") long timeout) {
        this.mapper=mapper; this.base=base.replaceAll("/+$", ""); this.appToken=appToken; this.userToken=userToken;
        this.timeout=Duration.ofMillis(timeout);
        this.client=HttpClient.newBuilder().connectTimeout(this.timeout).followRedirects(HttpClient.Redirect.NEVER).build();
    }
    public String openSession() throws Exception {
        String session=call("GET", "/initSession", null, null).path("session_token").asText();
        if(session.isBlank()) throw new IllegalStateException("GLPI session missing");
        return session;
    }
    public JsonNode call(String method, String path, JsonNode body, String session) throws Exception {
        for(int attempt=1;;attempt++) {
            try { return callOnce(method,path,body,session); }
            catch(Exception failure) {
                boolean retry=failure instanceof java.io.IOException || failure instanceof org.apache.camel.http.base.HttpOperationFailedException http
                    && (http.getStatusCode()==408 || http.getStatusCode()==429 || http.getStatusCode()>=500);
                if(!"GET".equals(method) || !retry || attempt>=3)throw failure;
                Thread.sleep(100L*attempt);
            }
        }
    }
    private JsonNode callOnce(String method,String path,JsonNode body,String session) throws Exception {
        HttpRequest.Builder request=HttpRequest.newBuilder(URI.create(base+path)).timeout(timeout)
            .header("Content-Type","application/json").header("App-Token",appToken);
        if(session==null) request.header("Authorization","user_token "+userToken);
        else request.header("Session-Token",session);
        request.method(method,body==null?HttpRequest.BodyPublishers.noBody():HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(body)));
        HttpResponse<String> response=client.send(request.build(),HttpResponse.BodyHandlers.ofString());
        if(response.body().length()>4_000_000)throw new IllegalStateException("GLPI response too large");
        if(response.statusCode()<200 || response.statusCode()>=300)
            throw new org.apache.camel.http.base.HttpOperationFailedException("GLPI",response.statusCode(),"GLPI request rejected",null,java.util.Map.of(),"");
        return mapper.readTree(response.body());
    }
    public void closeSession(String session) {
        try { call("GET","/killSession",null,session); } catch(Exception ignored) { /* Session expiry handles cleanup failures. */ }
    }
}
