package com.eventmanagement.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.jboss.logging.Logger;

@Named("gnmCommandProcessor")
@ApplicationScoped
public class GnmCommandProcessor implements Processor {

    private static final Logger LOG =
            Logger.getLogger(GnmCommandProcessor.class);

    private final GnmProviderContextResolver providerContextResolver;
    private final GnmEverbridgeMapper everbridgeMapper;

    @Inject
    public GnmCommandProcessor(
            GnmProviderContextResolver providerContextResolver,
            GnmEverbridgeMapper everbridgeMapper
    ) {
        this.providerContextResolver = providerContextResolver;
        this.everbridgeMapper = everbridgeMapper;
    }

    @Override
    public void process(Exchange exchange) {

        String operation =
                exchange.getProperty(
                        "operation",
                        String.class
                );

        if (!"SEND_NOTIFICATION".equals(operation) &&
                !"CLOSE_NOTIFICATION".equals(operation)) {

            throw new IllegalArgumentException(
                    "Operación GNM no soportada: " + operation
            );
        }

        JsonNode payload =
                exchange.getProperty(
                        "integrationPayload",
                        JsonNode.class
                );

        if (payload == null || !payload.isObject()) {
            throw new IllegalArgumentException(
                    "Payload GNM ausente o inválido"
            );
        }

        /*
         * Validate the provider-neutral GNM contract first.
         *
         * Provider identifiers remain outside integration.commands.
         * They are resolved only after the canonical command has passed
         * validation.
         */
        String customerCode =
                requiredText(payload, "customerCode");

        requiredText(payload, "customer");
        requiredInteger(payload, "severity");
        requiredText(payload, "severityName");
        requiredText(payload, "node");
        requiredText(payload, "resourceId");
        requiredText(payload, "summary");

        JsonNode legacyCorrelation =
                requiredObject(
                        payload,
                        "legacyCorrelation"
                );

        requiredText(
                legacyCorrelation,
                "serverSerial"
        );

        JsonNode notification =
                requiredObject(
                        payload,
                        "notification"
                );

        String notificationType =
                requiredText(
                        notification,
                        "type"
                ).toUpperCase();

        if (!"HUNT".equals(notificationType) &&
                !"BROADCAST".equals(notificationType)) {

            throw new IllegalArgumentException(
                    "Tipo de notificación GNM no soportado: " +
                            notificationType
            );
        }

        String logicalGroup =
                requiredText(
                        notification,
                        "group"
                );

        /*
         * Preserve the complete canonical payload independently from
         * provider-specific translation.
         */
        JsonNode canonicalEvent =
                payload.deepCopy();

        exchange.setProperty(
                "gnmCanonicalEvent",
                canonicalEvent
        );

        exchange.setProperty(
                "gnmNotificationType",
                notificationType
        );

        /*
         * Resolve provider configuration from the internal registry.
         *
         * No provider identity is expected in integration.commands.
         */
        EverbridgeProviderContext providerContext =
                providerContextResolver.resolve(
                        customerCode,
                        logicalGroup
                );

        /*
         * Translate the canonical event into the already certified
         * Everbridge Launch contract.
         *
         * This is preparation only:
         * - no HTTP
         * - no credentials
         * - no proxy
         * - no external side effect
         */
        String incidentId = null;

        ObjectNode providerRequest;

        if ("CLOSE_NOTIFICATION".equals(operation)) {

            /*
             * incidentId is the variable provider incident identity.
             *
             * Unlike organizationId, it is not static provider
             * configuration. CLOSE therefore receives the incident
             * identity from the provider-neutral Notification domain.
             *
             * It remains URI identity and is not embedded in the
             * Everbridge request body.
             */
            incidentId =
                    requiredText(
                            notification,
                            "incidentId"
                    );

            providerRequest =
                    everbridgeMapper.mapClose(
                            canonicalEvent,
                            providerContext
                    );

        } else {

            providerRequest =
                    everbridgeMapper.mapLaunch(
                            canonicalEvent,
                            providerContext
                    );
        }

        exchange.setProperty(
                "gnmProviderContext",
                providerContext
        );

        /*
         * Materialize the provider organization identity for the
         * authoritative post-Launch GET confirmation.
         *
         * The value originates exclusively from the internal provider
         * registry and is never accepted from integration.commands.
         */
        exchange.setProperty(
                "gnmOrganizationId",
                providerContext.organizationId()
        );

        if (incidentId != null) {
            exchange.setProperty(
                    "gnmIncidentId",
                    incidentId
            );
        }

        exchange.setProperty(
                "gnmProviderRequest",
                providerRequest
        );

        LOG.infov(
                "GNM provider request prepared: commandId={0}, eventId={1}, type={2}, provider=EVERBRIDGE",
                exchange.getProperty("commandId"),
                exchange.getProperty("eventId"),
                notificationType
        );
    }

    private JsonNode requiredObject(
            JsonNode node,
            String fieldName
    ) {

        JsonNode value = node.get(fieldName);

        if (value == null ||
                value.isNull() ||
                !value.isObject()) {

            throw new IllegalArgumentException(
                    "Campo obligatorio ausente o inválido: " +
                            fieldName
            );
        }

        return value;
    }

    private String requiredText(
            JsonNode node,
            String fieldName
    ) {

        JsonNode value = node.get(fieldName);

        if (value == null ||
                value.isNull() ||
                value.asText().isBlank()) {

            throw new IllegalArgumentException(
                    "Campo obligatorio ausente: " +
                            fieldName
            );
        }

        return value.asText().trim();
    }

    private int requiredInteger(
            JsonNode node,
            String fieldName
    ) {

        JsonNode value = node.get(fieldName);

        if (value == null ||
                value.isNull() ||
                !value.isIntegralNumber()) {

            throw new IllegalArgumentException(
                    "Campo obligatorio ausente o inválido: " +
                            fieldName
            );
        }

        return value.asInt();
    }
}
