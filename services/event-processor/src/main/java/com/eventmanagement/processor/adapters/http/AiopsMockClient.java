package com.eventmanagement.processor.adapters.http;
import com.eventmanagement.processor.domain.aiops.*;
import com.eventmanagement.processor.domain.admin.AdminFailure;
import com.eventmanagement.processor.ports.out.AiopsProvider;
import com.fasterxml.jackson.databind.*;
import jakarta.enterprise.context.ApplicationScoped;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import jakarta.inject.Inject;
import java.net.URI;
import java.net.http.*;
import java.time.Duration;

/** Internal mock adapter. Replace with a documented Bridge adapter when its contract is available. */
@ApplicationScoped
public class AiopsMockClient implements AiopsProvider {
    private final URI endpoint;
    private final HttpClient client=HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2))
            .followRedirects(HttpClient.Redirect.NEVER).build();
    private final ObjectMapper mapper=new ObjectMapper().enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS);
    @Inject public AiopsMockClient(@ConfigProperty(name="processor.aiops.mock-url") String baseUrl) {
        endpoint=URI.create(baseUrl+"/internal/aiops/assessments");
        if(!java.util.Set.of("http","https").contains(endpoint.getScheme()) || endpoint.getHost()==null
                || endpoint.getUserInfo()!=null || endpoint.getQuery()!=null || endpoint.getFragment()!=null)
            throw new IllegalArgumentException("INVALID_AIOPS_ENDPOINT");
    }
    @Override public AiopsAssessment assess(String tenant,AiopsSignal signal) {
        try {
            var request=HttpRequest.newBuilder(endpoint).timeout(Duration.ofSeconds(3))
                    .header("Content-Type","application/json").header("X-Tenant-Id",tenant)
                    .POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(signal))).build();
            var response=client.send(request,HttpResponse.BodyHandlers.ofByteArray());
            if(response.statusCode()!=200 || response.body().length>8192)
                throw new IllegalArgumentException("INVALID_PROVIDER_RESPONSE");
            var json=mapper.readTree(response.body());
            if(!json.isObject() || json.size()!=2 || !json.path("recommendation").isTextual() || !json.path("confidence").isNumber())
                throw new IllegalArgumentException("INVALID_PROVIDER_RESPONSE");
            return new AiopsAssessment(json.path("recommendation").textValue(),json.path("confidence").doubleValue());
        }catch(InterruptedException e){Thread.currentThread().interrupt();throw unavailable();}
        catch(Exception e){throw unavailable();}
    }
    private static AdminFailure unavailable(){return new AdminFailure(AdminFailure.Kind.UNAVAILABLE,"AIOPS_PROVIDER_UNAVAILABLE");}
}
