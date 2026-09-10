package com.eventmanagement.processor.adapters.http;
import com.eventmanagement.processor.domain.aiops.*;
import com.eventmanagement.processor.domain.admin.AdminFailure;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import static org.junit.jupiter.api.Assertions.*;
class AiopsMockClientTest {
    @Test void malformedAndNonSuccessResponsesAreSanitized()throws Exception {
        for(String body:new String[]{"not-json","{\"recommendation\":\"EXECUTE\",\"confidence\":1}","{\"recommendation\":\"OBSERVE\",\"confidence\":2}","{\"recommendation\":\"OBSERVE\",\"confidence\":0.5,\"extra\":true}"}) {
            var server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
            try {
                server.createContext("/internal/aiops/assessments",x->{x.getRequestBody().readAllBytes();byte[] bytes=body.getBytes(StandardCharsets.UTF_8);x.sendResponseHeaders(200,bytes.length);x.getResponseBody().write(bytes);x.close();});server.start();
                var client=new AiopsMockClient("http://127.0.0.1:"+server.getAddress().getPort());
                var error=assertThrows(AdminFailure.class,()->client.assess("test",new AiopsSignal("node","summary",3)));
                assertEquals(AdminFailure.Kind.UNAVAILABLE,error.kind());assertEquals("AIOPS_PROVIDER_UNAVAILABLE",error.getMessage());
            }finally{server.stop(0);}
        }
    }
    @Test void providerTimeoutIsBounded()throws Exception {
        var server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
        try {
            server.createContext("/internal/aiops/assessments",x->{try{Thread.sleep(4500);}catch(InterruptedException e){Thread.currentThread().interrupt();}finally{x.close();}});server.start();
            var client=new AiopsMockClient("http://127.0.0.1:"+server.getAddress().getPort());
            assertTimeout(java.time.Duration.ofSeconds(4),()->assertThrows(AdminFailure.class,()->client.assess("test",new AiopsSignal("node","summary",2))));
        }finally{server.stop(0);}
    }
}
