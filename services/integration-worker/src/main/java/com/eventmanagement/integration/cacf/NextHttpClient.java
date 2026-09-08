package com.eventmanagement.integration.cacf;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.net.URI;
import java.net.http.*;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.Flow;

@ApplicationScoped
public class NextHttpClient {
    private final CacfSettings settings;
    private final HttpClient client;
    @Inject public NextHttpClient(CacfSettings settings) {
        this.settings=settings;
        client=HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(settings.httpTimeout)).followRedirects(HttpClient.Redirect.NEVER).build();
    }
    public record Response(int status,byte[] body) {}
    public Response send(String operation,String xml) throws Exception {
        if(!settings.enabled)throw new IllegalStateException("CACF is disabled");
        String path=switch(operation){case "CREATE"->"/tupix/api/v1/netcool/tickets";case "TKTUPDATE"->"/tupix/api/v1/netcool/incidents";default->throw new IllegalArgumentException("Unknown NEXT operation");};
        URI base=URI.create(settings.baseUrl);
        if(base.getHost()==null || base.getUserInfo()!=null || base.getQuery()!=null || base.getFragment()!=null
                || !(base.getScheme().equals("http") || base.getScheme().equals("https")))throw new IllegalArgumentException("Invalid NEXT base URL");
        String auth=Base64.getEncoder().encodeToString((settings.username+":"+settings.password).getBytes(StandardCharsets.UTF_8));
        HttpRequest request=HttpRequest.newBuilder(base.resolve(path)).timeout(Duration.ofSeconds(settings.httpTimeout))
                .header("Content-Type","text/xml").header("Authorization","Basic "+auth)
                .POST(HttpRequest.BodyPublishers.ofString(xml,StandardCharsets.UTF_8)).build();
        // A total deadline also bounds response body consumption; no automatic mutation retries.
        var future=client.sendAsync(request,info->new BoundedBody(settings.maxXmlBytes));
        try {
            var response=future.get(settings.httpTimeout,TimeUnit.SECONDS);
            return new Response(response.statusCode(),response.body());
        } catch(Exception failure) {future.cancel(true);throw new IllegalStateException("NEXT request outcome is uncertain");}
    }
    private static final class BoundedBody implements HttpResponse.BodySubscriber<byte[]> {
        private final HttpResponse.BodySubscriber<byte[]> delegate=HttpResponse.BodySubscribers.ofByteArray();
        private final int limit;private long size;private Flow.Subscription subscription;
        BoundedBody(int limit){this.limit=limit;}
        public CompletionStage<byte[]> getBody(){return delegate.getBody();}
        public void onSubscribe(Flow.Subscription s){subscription=s;delegate.onSubscribe(s);}
        public void onNext(List<ByteBuffer> chunks){
            for(ByteBuffer b:chunks)size+=b.remaining();
            if(size>limit){subscription.cancel();delegate.onError(new IllegalArgumentException("NEXT response exceeds XML limit"));return;}
            delegate.onNext(chunks);
        }
        public void onError(Throwable t){delegate.onError(t);}
        public void onComplete(){delegate.onComplete();}
    }
}
