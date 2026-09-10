package com.eventmanagement.integration.cacf;

import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class NextHttpClientTest {
    private CacfSettings settings(int port,int limit) {
        return new CacfSettings(true,"http://127.0.0.1:"+port,Optional.of("fixture-user"),Optional.of("fixture-password"),Optional.of("fixture-token"),1,600,600,limit);
    }
    @Test void usesBasicAndTextXmlWithoutFollowingRedirects() throws Exception {
        HttpServer server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
        AtomicInteger redirected=new AtomicInteger();
        server.createContext("/tupix/api/v1/netcool/tickets",e->{
            assertEquals("POST",e.getRequestMethod());assertEquals("text/xml",e.getRequestHeaders().getFirst("Content-Type"));
            assertTrue(e.getRequestHeaders().getFirst("Authorization").startsWith("Basic "));
            e.getResponseHeaders().add("Location","/other");e.sendResponseHeaders(302,-1);e.close();
        });
        server.createContext("/other",e->{redirected.incrementAndGet();e.sendResponseHeaders(200,-1);e.close();});server.start();
        try {assertEquals(302,new NextHttpClient(settings(server.getAddress().getPort(),1024)).send("CREATE","<r/>").status());assertEquals(0,redirected.get());}
        finally {server.stop(0);}
    }
    @Test void boundsProviderResponseAndDoesNotRetry() throws Exception {
        HttpServer server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);AtomicInteger count=new AtomicInteger();
        server.createContext("/tupix/api/v1/netcool/incidents",e->{count.incrementAndGet();byte[] body="x".repeat(2048).getBytes(StandardCharsets.UTF_8);e.sendResponseHeaders(200,body.length);e.getResponseBody().write(body);e.close();});server.start();
        try {assertThrows(IllegalStateException.class,()->new NextHttpClient(settings(server.getAddress().getPort(),128)).send("TKTUPDATE","<r/>"));assertEquals(1,count.get());}
        finally {server.stop(0);}
    }
}
