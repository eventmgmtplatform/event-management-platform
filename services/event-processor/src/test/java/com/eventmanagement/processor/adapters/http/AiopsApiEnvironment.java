package com.eventmanagement.processor.adapters.http;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.util.*;
public class AiopsApiEnvironment extends AdminApiEnvironment {
    private HttpServer server;
    @Override public Map<String,String> start() {
        var properties=new HashMap<>(super.start());
        try {
            server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
            server.createContext("/internal/aiops/assessments",exchange->{
                String body=new String(exchange.getRequestBody().readAllBytes(),java.nio.charset.StandardCharsets.UTF_8);
                byte[] response="{\"recommendation\":\"INVESTIGATE\",\"confidence\":0.75}".getBytes();
                int status=body.contains("provider-down")?503:200;
                if(exchange.getRequestHeaders().getFirst("X-Tenant-Id")==null)status=400;
                exchange.getResponseHeaders().add("Content-Type","application/json");
                exchange.sendResponseHeaders(status,response.length);exchange.getResponseBody().write(response);exchange.close();
            });server.start();
            properties.put("processor.aiops.mock-url","http://127.0.0.1:"+server.getAddress().getPort());return properties;
        }catch(Exception e){stop();throw new IllegalStateException(e);}
    }
    @Override public void stop(){if(server!=null)server.stop(0);super.stop();}
}
