package com.eventmanagement.gateway;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jakarta.enterprise.context.ApplicationScoped;
import java.util.*;

/** Bounded declarative operations only: no scripts, remote lookups or arbitrary paths. */
@ApplicationScoped
public class GatewayRuleEngine {
    public enum Stage { INGESTION, NORMALIZATION, VALIDATION, ENRICHMENT }
    private static final Set<String> FIELDS = Set.of("id", "stage", "priority", "enabled", "match", "set", "copy", "required", "reject");

    public void validate(JsonNode rule) {
        require(rule != null && rule.isObject(), "RULE_OBJECT_REQUIRED");
        rule.fieldNames().forEachRemaining(k -> require(FIELDS.contains(k), "UNKNOWN_RULE_FIELD"));
        require(rule.path("id").isTextual() && rule.path("id").asText().matches("[a-zA-Z0-9_-]{1,64}"), "INVALID_RULE_ID");
        Stage stage;
        try { stage = Stage.valueOf(rule.path("stage").asText()); }
        catch (Exception e) { throw new IllegalArgumentException("INVALID_RULE_STAGE"); }
        require(rule.path("enabled").isBoolean(), "ENABLED_REQUIRED");
        require(rule.path("priority").isIntegralNumber() && rule.path("priority").canConvertToInt()
                && rule.path("priority").intValue() >= 0 && rule.path("priority").intValue() <= 10000, "INVALID_PRIORITY");
        object(rule, "match");
        if (rule.has("set")) {
            require(stage == Stage.NORMALIZATION || stage == Stage.ENRICHMENT, "SET_NOT_ALLOWED");
            object(rule, "set");
        }
        if (rule.has("copy")) {
            require(stage == Stage.NORMALIZATION || stage == Stage.ENRICHMENT, "COPY_NOT_ALLOWED");
            object(rule, "copy");
            rule.get("copy").elements().forEachRemaining(v -> require(v.isTextual() && field(v.asText()), "INVALID_COPY_SOURCE"));
        }
        if (rule.has("required")) {
            require(stage == Stage.VALIDATION && rule.get("required").isArray() && rule.get("required").size() <= 64, "INVALID_REQUIRED");
            rule.get("required").forEach(v -> require(v.isTextual() && field(v.asText()), "INVALID_REQUIRED_FIELD"));
        }
        if (rule.has("reject")) require(stage == Stage.INGESTION && rule.get("reject").isBoolean(), "INVALID_REJECT");
        require(switch (stage) {
            case INGESTION -> rule.path("reject").asBoolean();
            case VALIDATION -> rule.has("required") && !rule.get("required").isEmpty();
            default -> (rule.has("set") && !rule.get("set").isEmpty()) || (rule.has("copy") && !rule.get("copy").isEmpty());
        }, "RULE_ACTION_REQUIRED");
    }

    private void object(JsonNode rule, String name) {
        require(rule.path(name).isObject() && rule.path(name).size() <= 64, "INVALID_" + name.toUpperCase(Locale.ROOT));
        rule.get(name).fields().forEachRemaining(e -> {
            require(field(e.getKey()), "INVALID_FIELD_NAME");
            require(e.getValue().isValueNode() && !e.getValue().isNull(), "SCALAR_VALUE_REQUIRED");
        });
    }
    private static boolean field(String value) { return value.matches("[a-zA-Z][a-zA-Z0-9_]{0,63}"); }
    private static void require(boolean valid, String message) { if (!valid) throw new IllegalArgumentException(message); }

    public List<JsonNode> ordered(List<JsonNode> rules) {
        rules.forEach(this::validate);
        return rules.stream().filter(r -> r.path("enabled").booleanValue())
                .sorted(Comparator.comparingInt((JsonNode r) -> r.path("priority").intValue()).thenComparing(r -> r.path("id").asText())).toList();
    }

    public void apply(Stage stage, ObjectNode input, ObjectNode target, List<JsonNode> rules, List<String> applied) {
        for (JsonNode rule : rules) {
            if (!stage.name().equals(rule.path("stage").asText())) continue;
            boolean matches = true;
            var conditions = rule.path("match").fields();
            while (conditions.hasNext()) {
                var entry = conditions.next();
                if (!entry.getValue().equals(input.get(entry.getKey()))) { matches = false; break; }
            }
            if (!matches) continue;
            String id = rule.path("id").asText();
            if (stage == Stage.INGESTION) throw new IllegalArgumentException("INGESTION_REJECTED:" + id);
            for (JsonNode field : rule.path("required")) {
                JsonNode value = input.get(field.asText());
                require(value != null && value.isValueNode() && !value.isNull() && !value.asText().isBlank(), "VALIDATION_REJECTED:" + id);
            }
            // Copy reads a snapshot; mappings never depend on JSON member order.
            ObjectNode source = input.deepCopy();
            rule.path("copy").fields().forEachRemaining(e -> {
                JsonNode value = source.get(e.getValue().asText());
                require(value != null && value.isValueNode() && !value.isNull(), "COPY_SOURCE_MISSING:" + id);
                target.set(e.getKey(), value.deepCopy());
            });
            rule.path("set").fields().forEachRemaining(e -> target.set(e.getKey(), e.getValue().deepCopy()));
            applied.add(id);
        }
    }
}
