package com.eventmanagement.gateway;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.StreamReadFeature;
import com.fasterxml.jackson.databind.*;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.support.DefaultExchange;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;

/** Administrative routes have their own failure handling, separate from receipt ingestion. */
@ApplicationScoped
public class GatewayRulesApi extends RouteBuilder {
    @Inject GatewayRuleStore store;
    @Inject GatewayRuleEngine engine;
    @Inject GatewayRulePipeline pipeline;
    @ConfigProperty(name="event.gateway.rules.admin-key") Optional<String> adminKey;
    private final ObjectMapper json = new ObjectMapper(JsonFactory.builder()
            .enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION).build())
            .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS);

    @Override public void configure() {
        from("platform-http:/api/v1/gateway/rules?httpMethodRestrict=GET,POST")
                .routeId("gateway-rules-collection").process(e -> handle(e,"collection"));
        from("platform-http:/api/v1/gateway/rules/{id}?httpMethodRestrict=GET,PUT")
                .routeId("gateway-rules-item").process(e -> handle(e,"item"));
        from("platform-http:/api/v1/gateway/rules/{id}/history?httpMethodRestrict=GET")
                .routeId("gateway-rules-history").process(e -> handle(e,"history"));
        from("platform-http:/api/v1/gateway/rules/validate?httpMethodRestrict=POST")
                .routeId("gateway-rules-validate").process(e -> handle(e,"validate"));
        from("platform-http:/api/v1/gateway/rules/simulate?httpMethodRestrict=POST")
                .routeId("gateway-rules-simulate").process(e -> handle(e,"simulate"));
    }

    void handle(Exchange exchange, String operation) {
        try {
            String configured = adminKey.orElse("");
            if (configured.isBlank()) throw new Failure(503,"RULE_ADMIN_NOT_CONFIGURED");
            String supplied = exchange.getMessage().getHeader("X-Gateway-Admin-Key",String.class);
            if (supplied == null || !MessageDigest.isEqual(configured.getBytes(StandardCharsets.UTF_8),supplied.getBytes(StandardCharsets.UTF_8)))
                throw new Failure(401,"UNAUTHORIZED");
            String method = exchange.getMessage().getHeader(Exchange.HTTP_METHOD,String.class);
            String id = exchange.getMessage().getHeader("id",String.class);
            if (operation.equals("history") || operation.equals("item")) {
                if (id == null || !id.matches("[a-zA-Z0-9_-]{1,64}")) throw new Failure(400,"INVALID_RULE_ID");
            }
            if (operation.equals("history")) {
                var history = store.history(id);
                if (history.isEmpty()) throw new Failure(404,"RULE_NOT_FOUND");
                respond(exchange,200,Map.of("items",history),null); return;
            }
            if ("GET".equals(method)) {
                var entries = store.list();
                if (operation.equals("collection")) respond(exchange,200,Map.of("items",entries),null);
                else {
                    final String ruleId = id;
                    var entry = entries.stream().filter(e -> e.rule().path("id").asText().equals(ruleId)).findFirst()
                            .orElseThrow(() -> new Failure(404,"RULE_NOT_FOUND"));
                    respond(exchange,200,entry,entry.revision());
                }
                return;
            }
            String body = exchange.getMessage().getBody(String.class);
            if (body == null || body.length() > 262144) throw new Failure(413,"REQUEST_SIZE_LIMIT");
            JsonNode parsed = json.readTree(body);
            if (parsed == null || !parsed.isObject()) throw new Failure(400,"OBJECT_REQUIRED");
            if (operation.equals("simulate")) {
                parsed.fieldNames().forEachRemaining(k -> { if (!Set.of("event","rules").contains(k)) throw new Failure(400,"UNKNOWN_REQUEST_FIELD"); });
                if (!parsed.path("event").isObject()) throw new Failure(400,"EVENT_OBJECT_REQUIRED");
                List<GatewayRuleStore.Entry> snapshot;
                if (parsed.has("rules")) {
                    if (!parsed.get("rules").isArray() || parsed.get("rules").size() > 256) throw new Failure(400,"INVALID_RULE_SET");
                    snapshot = new ArrayList<>(); var ids = new HashSet<String>();
                    for (JsonNode rule : parsed.get("rules")) {
                        engine.validate(rule);
                        if (!ids.add(rule.path("id").asText())) throw new Failure(400,"DUPLICATE_RULE_ID");
                        snapshot.add(new GatewayRuleStore.Entry(0,rule));
                    }
                } else snapshot = store.list();
                Exchange simulation = new DefaultExchange(exchange.getContext());
                simulation.getMessage().setBody(parsed.get("event").toString());
                pipeline.evaluate(simulation,snapshot);
                respond(exchange,200,Map.of("mode","SIMULATION","event",json.readTree(simulation.getMessage().getBody(String.class))),null);
                return;
            }
            engine.validate(parsed);
            if (operation.equals("validate")) { respond(exchange,200,Map.of("valid",true),null); return; }
            long expected = 0;
            if (operation.equals("item")) {
                if (!id.equals(parsed.path("id").asText())) throw new Failure(400,"RULE_ID_MISMATCH");
                String match = exchange.getMessage().getHeader("If-Match",String.class);
                if (match == null) throw new Failure(428,"IF_MATCH_REQUIRED");
                if (!match.matches("\"[1-9][0-9]{0,17}\"")) throw new Failure(400,"INVALID_IF_MATCH");
                expected = Long.parseLong(match.substring(1,match.length()-1));
            }
            var result = store.save(parsed,expected);
            respond(exchange,operation.equals("collection") ? 201 : 200,result,result.revision());
        } catch (Failure e) { respond(exchange,e.status,Map.of("errorCode",e.getMessage()),null); }
        catch (GatewayRuleStore.Conflict e) { respond(exchange,409,Map.of("errorCode","RULE_REVISION_CONFLICT"),null); }
        catch (IllegalArgumentException | com.fasterxml.jackson.core.JsonProcessingException e) {
            respond(exchange,400,Map.of("errorCode","INVALID_RULE_OR_EVENT"),null);
        } catch (Exception e) { respond(exchange,503,Map.of("errorCode","RULE_STORE_UNAVAILABLE"),null); }
    }
    private void respond(Exchange e,int status,Object entity,Long revision) {
        e.getMessage().removeHeaders("*");
        e.getMessage().setHeader(Exchange.HTTP_RESPONSE_CODE,status);
        e.getMessage().setHeader(Exchange.CONTENT_TYPE,"application/json");
        e.getMessage().setHeader("Cache-Control","no-store");
        if (revision != null) e.getMessage().setHeader("ETag","\"" + revision + "\"");
        try { e.getMessage().setBody(json.writeValueAsString(entity)); }
        catch (Exception ex) { throw new IllegalStateException(ex); }
    }
    private static class Failure extends RuntimeException {
        final int status;
        Failure(int status,String code) { super(code); this.status=status; }
    }
}
