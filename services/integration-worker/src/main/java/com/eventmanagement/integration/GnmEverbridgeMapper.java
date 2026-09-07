package com.eventmanagement.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.inject.Named;

@Named("gnmEverbridgeMapper")
@ApplicationScoped
public class GnmEverbridgeMapper {


    /**
     * Maps the provider-neutral GNM event into the certified
     * Everbridge CloseWithNotification contract.
     *
     * CLOSE preserves the same provider registry/template/group
     * resolution as Launch, but changes the provider lifecycle
     * mutation semantics:
     *
     * - incidentAction = CloseWithNotification
     * - name = ***Clear*** Alert for <customerCode> on <node>
     * - Hunt confirmation = false
     * - cycleInterval is omitted
     *
     * organizationId and incidentId remain URI identities and are
     * therefore not embedded into the provider request body.
     */
    public ObjectNode mapClose(
            JsonNode canonicalEvent,
            EverbridgeProviderContext context
    ) {

        ObjectNode close =
                mapLaunch(
                        canonicalEvent,
                        context
                ).deepCopy();

        close.put(
                "incidentAction",
                "CloseWithNotification"
        );

        String customerCode =
                requiredText(
                        canonicalEvent,
                        "customerCode"
                );

        String node =
                requiredText(
                        canonicalEvent,
                        "node"
                );

        close.put(
                "name",
                "***Clear*** Alert for "
                        + customerCode
                        + " on "
                        + node
        );

        JsonNode phases =
                close.path(
                        "incidentPhases"
                );

        if (!phases.isArray()
                || phases.isEmpty()) {

            throw new IllegalStateException(
                    "GNM Close mapping requires incident phase"
            );
        }

        JsonNode settingsNode =
                phases.get(0)
                        .path("phaseTemplate")
                        .path("broadcastTemplate")
                        .path("broadcastSettings");

        if (!(settingsNode instanceof ObjectNode settings)) {
            throw new IllegalStateException(
                    "GNM Close mapping requires broadcastSettings"
            );
        }

        /*
         * Legacy Everbridge CloseWithNotification contract:
         * Hunt clear notifications do not request confirmation.
         */
        settings.put(
                "confirm",
                "false"
        );

        /*
         * The externally certified PUT contract does not send
         * cycleInterval.
         */
        settings.remove(
                "cycleInterval"
        );

        return close;
    }


    private final ObjectMapper objectMapper;

    @Inject
    public GnmEverbridgeMapper(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public ObjectNode mapLaunch(
            JsonNode canonicalEvent,
            EverbridgeProviderContext context
    ) {

        if (canonicalEvent == null || !canonicalEvent.isObject()) {
            throw new IllegalArgumentException(
                    "GNM canonical event is required"
            );
        }

        JsonNode notification =
                requiredObject(canonicalEvent, "notification");

        JsonNode legacyCorrelation =
                requiredObject(canonicalEvent, "legacyCorrelation");

        String customerCode =
                requiredText(canonicalEvent, "customerCode");

        String node =
                requiredText(canonicalEvent, "node");

        String notificationType =
                requiredText(notification, "type")
                        .toUpperCase();

        if (!"HUNT".equals(notificationType) &&
                !"BROADCAST".equals(notificationType)) {
            throw new IllegalArgumentException(
                    "Unsupported GNM notification type: " +
                            notificationType
            );
        }

        ObjectNode root =
                objectMapper.createObjectNode();

        root.put("incidentAction", "Launch");

        root.put(
                "name",
                buildLaunchName(
                        customerCode,
                        node,
                        requiredText(canonicalEvent, "summary")
                )
        );

        ArrayNode incidentPhases =
                root.putArray("incidentPhases");

        ObjectNode phase =
                incidentPhases.addObject();

        ObjectNode phaseTemplate =
                phase.putObject("phaseTemplate");

        ObjectNode broadcastTemplate =
                phaseTemplate.putObject("broadcastTemplate");

        broadcastTemplate.put(
                "priority",
                optionalText(
                        canonicalEvent,
                        "priority",
                        "NonPriority"
                )
        );

        broadcastTemplate.put(
                "type",
                "Standard"
        );

        ObjectNode broadcastContacts =
                broadcastTemplate.putObject(
                        "broadcastContacts"
                );

        broadcastContacts.putArray("externalIds");

        ArrayNode groupIds =
                broadcastContacts.putArray("groupIds");

        context.groupIds().forEach(groupIds::add);

        ObjectNode broadcastSettings =
                broadcastTemplate.putObject(
                        "broadcastSettings"
                );

        broadcastSettings.put(
                "confirm",
                "true"
        );

        broadcastSettings.put(
                "cycleInterval",
                context.cycleInterval()
        );

        broadcastSettings.put(
                "deliveryPathOrder",
                "Contact"
        );

        ObjectNode formTemplate =
                phaseTemplate.putObject("formTemplate");

        ArrayNode variables =
                formTemplate.putArray(
                        "formVariableItems"
                );

        int seq = 0;

        seq = add(
                variables,
                EverbridgeVariableCatalog.CUSTOMER_CODE,
                seq,
                customerCode
        );

        seq = addOptional(
                variables,
                EverbridgeVariableCatalog.IDENTIFIER,
                seq,
                canonicalEvent,
                "identifier"
        );

        seq = add(
                variables,
                EverbridgeVariableCatalog.SEVERITY,
                seq,
                String.valueOf(
                        requiredInteger(
                                canonicalEvent,
                                "severity"
                        )
                )
        );

        seq = add(
                variables,
                EverbridgeVariableCatalog.NODE,
                seq,
                node
        );

        seq = addOptional(
                variables,
                EverbridgeVariableCatalog.NODE_ALIAS,
                seq,
                canonicalEvent,
                "nodeAlias"
        );

        seq = addOptional(
                variables,
                EverbridgeVariableCatalog.SERVER_NAME,
                seq,
                legacyCorrelation,
                "serverName"
        );

        seq = add(
                variables,
                EverbridgeVariableCatalog.SERVER_SERIAL,
                seq,
                requiredText(
                        legacyCorrelation,
                        "serverSerial"
                )
        );

        seq = addOptional(
                variables,
                EverbridgeVariableCatalog.TICKET_NUMBER,
                seq,
                canonicalEvent,
                "ticketNumber"
        );

        seq = add(
                variables,
                EverbridgeVariableCatalog.SUMMARY,
                seq,
                requiredText(
                        canonicalEvent,
                        "summary"
                )
        );

        seq = add(
                variables,
                EverbridgeVariableCatalog.CUSTOMER,
                seq,
                requiredText(
                        canonicalEvent,
                        "customer"
                )
        );

        seq = addOptional(
                variables,
                EverbridgeVariableCatalog.TICKET_GROUP,
                seq,
                canonicalEvent,
                "ticketGroup"
        );

        seq = add(
                variables,
                EverbridgeVariableCatalog.NOTIFICATION_GROUP,
                seq,
                requiredText(
                        notification,
                        "group"
                )
        );

        seq = add(
                variables,
                EverbridgeVariableCatalog.SEVERITY_NAME,
                seq,
                requiredText(
                        canonicalEvent,
                        "severityName"
                )
        );

        seq = addOptional(
                variables,
                EverbridgeVariableCatalog.COMPONENT,
                seq,
                canonicalEvent,
                "component"
        );

        seq = addOptional(
                variables,
                EverbridgeVariableCatalog.COMPONENT_TYPE,
                seq,
                canonicalEvent,
                "componentType"
        );

        seq = addOptional(
                variables,
                EverbridgeVariableCatalog.INSTANCE_ID,
                seq,
                canonicalEvent,
                "instanceId"
        );

        seq = addOptional(
                variables,
                EverbridgeVariableCatalog.INSTANCE_VALUE,
                seq,
                canonicalEvent,
                "instanceValue"
        );

        seq = addOptional(
                variables,
                EverbridgeVariableCatalog.SUBCOMPONENT,
                seq,
                canonicalEvent,
                "subcomponent"
        );

        seq = addOptional(
                variables,
                EverbridgeVariableCatalog.ALERT_KEY,
                seq,
                canonicalEvent,
                "alertKey"
        );

        add(
                variables,
                EverbridgeVariableCatalog.RESOURCE_ID,
                seq,
                requiredText(
                        canonicalEvent,
                        "resourceId"
                )
        );

        /*
         * Critical provider hierarchy:
         *
         * templateId and templateName belong to phaseTemplate.
         * They are siblings of formTemplate.
         *
         * Moving them under formTemplate reproduces the previously
         * certified HTTP 400 provider parsing failure.
         */
        phaseTemplate.put(
                "templateId",
                context.templateId()
        );

        phaseTemplate.put(
                "templateName",
                context.templateName()
        );

        return root;
    }

    private int add(
            ArrayNode variables,
            String variableId,
            int seq,
            String value
    ) {

        ObjectNode item =
                variables.addObject();

        item.put(
                "variableId",
                variableId
        );

        item.put(
                "seq",
                seq
        );

        item.putArray("val")
                .add(value == null ? "" : value);

        return seq + 1;
    }

    private int addOptional(
            ArrayNode variables,
            String variableId,
            int seq,
            JsonNode source,
            String field
    ) {

        JsonNode value =
                source.get(field);

        return add(
                variables,
                variableId,
                seq,
                value == null || value.isNull()
                        ? ""
                        : value.asText()
        );
    }

    private JsonNode requiredObject(
            JsonNode node,
            String field
    ) {

        JsonNode value =
                node.get(field);

        if (value == null ||
                value.isNull() ||
                !value.isObject()) {

            throw new IllegalArgumentException(
                    "Required object missing: " + field
            );
        }

        return value;
    }

    private String requiredText(
            JsonNode node,
            String field
    ) {

        JsonNode value =
                node.get(field);

        if (value == null ||
                value.isNull() ||
                value.asText().isBlank()) {

            throw new IllegalArgumentException(
                    "Required field missing: " + field
            );
        }

        return value.asText().trim();
    }

    private int requiredInteger(
            JsonNode node,
            String field
    ) {

        JsonNode value =
                node.get(field);

        if (value == null ||
                value.isNull() ||
                !value.isIntegralNumber()) {

            throw new IllegalArgumentException(
                    "Required integer missing: " + field
            );
        }

        return value.asInt();
    }

    private String optionalText(
            JsonNode node,
            String field,
            String defaultValue
    ) {

        JsonNode value =
                node.get(field);

        if (value == null ||
                value.isNull() ||
                value.asText().isBlank()) {

            return defaultValue;
        }

        return value.asText().trim();
    }

    private String buildLaunchName(
            String customerCode,
            String node,
            String summary
    ) {

        return "Alert for " +
                customerCode +
                " on " +
                node +
                ": " +
                summary;
    }
}
