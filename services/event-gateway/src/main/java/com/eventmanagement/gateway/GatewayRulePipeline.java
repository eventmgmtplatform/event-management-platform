package com.eventmanagement.gateway;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.apache.camel.Exchange;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import java.util.*;

@ApplicationScoped
public class GatewayRulePipeline {
    @Inject GatewayRuleStore store;
    @Inject GatewayRuleEngine engine;
    @Inject EventValidationProcessor validator;
    @Inject ObjectMapper mapper;
    @ConfigProperty(name="event.gateway.rules.enabled", defaultValue="false") boolean enabled;

    public void process(Exchange exchange) throws Exception {
        if (!enabled) { validator.process(exchange); return; }
        evaluate(exchange, store.list());
    }

    public void evaluate(Exchange exchange, List<GatewayRuleStore.Entry> snapshot) throws Exception {
        JsonNode parsed;
        try {
            parsed = mapper.reader().with(com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
                    .readTree(exchange.getMessage().getBody(String.class));
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            throw new IllegalArgumentException("INVALID_EVENT_JSON");
        }
        if (parsed == null || !parsed.isObject()) throw new IllegalArgumentException("EVENT_OBJECT_REQUIRED");
        ObjectNode original = (ObjectNode) parsed;
        ObjectNode working = original.deepCopy();
        List<JsonNode> rules = engine.ordered(snapshot.stream().map(GatewayRuleStore.Entry::rule).toList());
        var applied = new ArrayList<String>();
        engine.apply(GatewayRuleEngine.Stage.INGESTION,working,working,rules,applied);
        engine.apply(GatewayRuleEngine.Stage.NORMALIZATION,working,working,rules,applied);
        engine.apply(GatewayRuleEngine.Stage.VALIDATION,working,working,rules,applied);
        exchange.getMessage().setBody(mapper.writeValueAsString(working));
        validator.process(exchange); // Existing v1.0/v1.1 contracts remain mandatory.
        ObjectNode normalized = (ObjectNode) mapper.readTree(exchange.getMessage().getBody(String.class));
        normalized.set("originalEvent",original.deepCopy());
        ObjectNode base = mapper.createObjectNode();
        engine.apply(GatewayRuleEngine.Stage.ENRICHMENT,working,base,rules,applied);
        if (!base.isEmpty()) normalized.putObject("enrichment").set("base",base);
        var metadata = normalized.putObject("gatewayRules");
        var used = metadata.putArray("applied");
        applied.forEach(used::add);
        var revisions = metadata.putObject("revisions");
        snapshot.forEach(e -> revisions.put(e.rule().path("id").asText(), e.revision()));
        exchange.getMessage().setBody(mapper.writeValueAsString(normalized));
    }
}
