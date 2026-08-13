package com.eventmanagement.gateway;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.List;

@ApplicationScoped
public class ZabbixMessageBusNormalizer {

    private static final List<String> REQUIRED_FIELDS = List.of(
            "source",
            "InstanceSituation",
            "AlertKey",
            "InstanceValue",
            "hostname",
            "ClassName",
            "Type",
            "origin",
            "msg",
            "severity",
            "Component",
            "InstanceId",
            "Node",
            "NodeAlias",
            "ApplId",
            "CustomerCode",
            "SubComponent",
            "ExpireTime"
    );

    private final ObjectMapper objectMapper;

    @Inject
    public ZabbixMessageBusNormalizer(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public boolean supports(JsonNode event) {
        return event.has("CustomerCode")
                && event.has("AlertKey")
                && event.has("InstanceId")
                && event.has("Type");
    }

    public ObjectNode normalize(
            JsonNode inputEvent,
            String eventId,
            String receivedAt
    ) {

        validateRequiredFields(inputEvent);

        String source = requiredText(inputEvent, "source");
        String customerCode = requiredText(inputEvent, "CustomerCode");
        String alertKey = requiredText(inputEvent, "AlertKey");
        String node = requiredText(inputEvent, "Node");
        String instanceId = requiredText(inputEvent, "InstanceId");
        String sourceType = requiredText(inputEvent, "Type");
        String instanceValue = requiredText(inputEvent, "InstanceValue");

        int sourceSeverity = integerValue(
                inputEvent,
                "severity",
                0,
                5
        );

        int expirationSeconds = integerValue(
                inputEvent,
                "ExpireTime",
                0,
                Integer.MAX_VALUE
        );

        String lifecycleAction =
                lifecycleAction(sourceType, instanceValue);

        int effectiveSeverity =
                "CLOSE".equals(lifecycleAction)
                        ? 0
                        : sourceSeverity;

        String legacyEventKey = String.join(
                ":",
                alertKey,
                node,
                instanceId,
                source
        );

        String eventKey = String.join(
                ":",
                customerCode,
                source,
                alertKey,
                node,
                instanceId
        );

        ObjectNode normalized =
                objectMapper.createObjectNode();

        normalized.put("schemaVersion", "1.1");
        normalized.put("eventId", eventId);
        normalized.put("eventKey", eventKey);
        normalized.put("legacyEventKey", legacyEventKey);

        ObjectNode tenant = normalized.putObject("tenant");
        tenant.put("code", customerCode);

        ObjectNode sourceNode = normalized.putObject("source");
        sourceNode.put("system", source);
        sourceNode.put(
                "className",
                requiredText(inputEvent, "ClassName")
        );
        sourceNode.put(
                "originAddress",
                requiredText(inputEvent, "origin")
        );

        ObjectNode resource = normalized.putObject("resource");
        resource.put("name", node);
        resource.put(
                "address",
                requiredText(inputEvent, "NodeAlias")
        );
        resource.put(
                "hostname",
                requiredText(inputEvent, "hostname")
        );
        resource.put(
                "component",
                requiredText(inputEvent, "Component")
        );
        resource.put(
                "applicationId",
                requiredText(inputEvent, "ApplId")
        );

        ArrayNode subcomponents =
                resource.putArray("subcomponents");

        String rawSubcomponents =
                requiredTextAllowEmpty(
                        inputEvent,
                        "SubComponent"
                );

        if (!rawSubcomponents.isBlank()) {
            for (String value : rawSubcomponents.split(",")) {
                String normalizedValue = value.trim();

                if (!normalizedValue.isEmpty()) {
                    subcomponents.add(normalizedValue);
                }
            }
        }

        ObjectNode condition =
                normalized.putObject("condition");

        condition.put("alertKey", alertKey);
        condition.put(
                "situation",
                requiredText(
                        inputEvent,
                        "InstanceSituation"
                )
        );
        condition.put("instanceId", instanceId);
        condition.put("instanceValue", instanceValue);

        normalized.put(
                "summary",
                requiredText(inputEvent, "msg")
        );

        normalized.put(
                "sourceSeverity",
                sourceSeverity
        );

        normalized.put(
                "effectiveSeverity",
                effectiveSeverity
        );

        normalized.put("sourceType", sourceType);
        normalized.put(
                "lifecycleAction",
                lifecycleAction
        );

        normalized.put(
                "expirationSeconds",
                expirationSeconds
        );

        ObjectNode timestamps =
                normalized.putObject("timestamps");

        timestamps.put("receivedAt", receivedAt);
        timestamps.put("lastUpdatedAt", receivedAt);

        normalized.set(
                "originalEvent",
                inputEvent.deepCopy()
        );

        return normalized;
    }

    private void validateRequiredFields(JsonNode event) {
        for (String field : REQUIRED_FIELDS) {
            if (!event.has(field) || event.get(field).isNull()) {
                throw new IllegalArgumentException(
                        "Campo Zabbix obligatorio ausente: " +
                                field
                );
            }
        }
    }

    private String lifecycleAction(
            String sourceType,
            String instanceValue
    ) {

        if ("1".equals(sourceType)
                && "0".equals(instanceValue)) {
            return "OPEN";
        }

        if ("0".equals(sourceType)
                && "1".equals(instanceValue)) {
            return "CLOSE";
        }

        throw new IllegalArgumentException(
                "Combinación Zabbix inválida: Type=" +
                        sourceType +
                        ", InstanceValue=" +
                        instanceValue
        );
    }

    private int integerValue(
            JsonNode event,
            String field,
            int minimum,
            int maximum
    ) {

        String raw = requiredText(event, field);
        int value;

        try {
            value = Integer.parseInt(raw);
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException(
                    field + " debe ser un entero",
                    exception
            );
        }

        if (value < minimum || value > maximum) {
            throw new IllegalArgumentException(
                    field + " debe encontrarse entre " +
                            minimum + " y " + maximum
            );
        }

        return value;
    }

    private String requiredText(
            JsonNode event,
            String field
    ) {

        String value =
                requiredTextAllowEmpty(event, field);

        if (value.isBlank()) {
            throw new IllegalArgumentException(
                    "Campo Zabbix obligatorio vacío: " +
                            field
            );
        }

        return value;
    }

    private String requiredTextAllowEmpty(
            JsonNode event,
            String field
    ) {

        JsonNode value = event.get(field);

        if (value == null || value.isNull()) {
            throw new IllegalArgumentException(
                    "Campo Zabbix obligatorio ausente: " +
                            field
            );
        }

        return value.asText().trim();
    }
}
