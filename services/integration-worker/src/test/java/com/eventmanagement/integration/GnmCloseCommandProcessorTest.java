package com.eventmanagement.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.camel.Exchange;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.support.DefaultExchange;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class GnmCloseCommandProcessorTest {

    private final ObjectMapper objectMapper =
            new ObjectMapper();

    @Test
    void shouldPrepareCloseNotification()
            throws Exception {

        GnmCommandProcessor processor =
                processor();

        Exchange exchange =
                exchange(
                        "CLOSE_NOTIFICATION",
                        true
                );

        processor.process(exchange);

        assertEquals(
                "453003085618991",
                exchange.getProperty(
                        "gnmOrganizationId",
                        String.class
                )
        );

        assertEquals(
                "2713046552385248",
                exchange.getProperty(
                        "gnmIncidentId",
                        String.class
                )
        );

        JsonNode request =
                exchange.getProperty(
                        "gnmProviderRequest",
                        JsonNode.class
                );

        assertNotNull(request);

        assertEquals(
                "CloseWithNotification",
                request.path("incidentAction").asText()
        );

        assertEquals(
                "***Clear*** Alert for vit on utlcorpdp01",
                request.path("name").asText()
        );

        assertFalse(
                request.has("organizationId")
        );

        assertFalse(
                request.has("incidentId")
        );
    }

    @Test
    void shouldRequireIncidentIdForClose()
            throws Exception {

        GnmCommandProcessor processor =
                processor();

        Exchange exchange =
                exchange(
                        "CLOSE_NOTIFICATION",
                        false
                );

        IllegalArgumentException error =
                assertThrows(
                        IllegalArgumentException.class,
                        () -> processor.process(exchange)
                );

        assertTrue(
                error.getMessage().contains(
                        "incidentId"
                )
        );
    }

    @Test
    void shouldKeepSendNotificationBackwardCompatible()
            throws Exception {

        GnmCommandProcessor processor =
                processor();

        Exchange exchange =
                exchange(
                        "SEND_NOTIFICATION",
                        false
                );

        processor.process(exchange);

        assertNull(
                exchange.getProperty(
                        "gnmIncidentId"
                )
        );

        JsonNode request =
                exchange.getProperty(
                        "gnmProviderRequest",
                        JsonNode.class
                );

        assertNotNull(request);

        assertEquals(
                "Launch",
                request.path("incidentAction").asText()
        );
    }

    @Test
    void shouldRejectUnsupportedOperation()
            throws Exception {

        GnmCommandProcessor processor =
                processor();

        Exchange exchange =
                exchange(
                        "DELETE_NOTIFICATION",
                        true
                );

        IllegalArgumentException error =
                assertThrows(
                        IllegalArgumentException.class,
                        () -> processor.process(exchange)
                );

        assertTrue(
                error.getMessage().contains(
                        "Operación GNM no soportada"
                )
        );
    }

    private GnmCommandProcessor processor() {

        GnmProviderContextResolver resolver =
                new GnmProviderContextResolver(
                        null
                ) {
                    @Override
                    public EverbridgeProviderContext resolve(
                            String customerCode,
                            String logicalGroup
                    ) {
                        return providerContext();
                    }
                };

        return new GnmCommandProcessor(
                resolver,
                new GnmEverbridgeMapper(
                        objectMapper
                )
        );
    }

    private Exchange exchange(
            String operation,
            boolean includeIncidentId
    ) throws Exception {

        Exchange exchange =
                new DefaultExchange(
                        new DefaultCamelContext()
                );

        exchange.setProperty(
                "commandId",
                "cmd-gnm-close-001"
        );

        exchange.setProperty(
                "eventId",
                "evt-gnm-close-001"
        );

        exchange.setProperty(
                "operation",
                operation
        );

        exchange.setProperty(
                "integrationPayload",
                payload(
                        includeIncidentId
                )
        );

        return exchange;
    }

    private JsonNode payload(
            boolean includeIncidentId
    ) throws Exception {

        var payload =
                objectMapper.createObjectNode();

        payload.put("customerCode", "vit");
        payload.put("customer", "VITRO");
        payload.put("severity", 3);
        payload.put("severityName", "Minor");
        payload.put("node", "utlcorpdp01");
        payload.put("nodeAlias", "172.16.3.7");
        payload.put("resourceId", "utlcorpdp01");
        payload.put("component", "WIN");
        payload.put("subcomponent", "VIT Windows");
        payload.put("componentType", "");
        payload.put("instanceId", "D:");
        payload.put("instanceValue", "90.21 %");
        payload.put("alertKey", "vit_dsp_gntm_win");

        payload.put(
                "identifier",
                "vit_dsp_gntm_win:utlcorpdp01:D::Zabbix"
        );

        payload.put(
                "summary",
                "High Space Used on disk D: 90% (MINOR)"
        );

        payload.put(
                "ticketNumber",
                "INC0576787"
        );

        payload.put(
                "ticketGroup",
                "K-MX-VIT-DYNAMIC-AUTOMATION"
        );

        var correlation =
                payload.putObject(
                        "legacyCorrelation"
                );

        correlation.put(
                "serverSerial",
                "1266890674"
        );

        correlation.put(
                "serverName",
                "MX330P0SDCP"
        );

        var notification =
                payload.putObject(
                        "notification"
                );

        notification.put(
                "type",
                "HUNT"
        );

        notification.put(
                "group",
                "VIT - VITRO - K - VITRO-MX-WINTEL"
        );

        if (includeIncidentId) {
            notification.put(
                    "incidentId",
                    "2713046552385248"
            );
        }

        return payload;
    }

    private EverbridgeProviderContext providerContext() {

        return new EverbridgeProviderContext(
                "453003085618991",
                "8101205968426652",
                "GSMA_C00_HV",
                List.of(
                        "3760896702677017",
                        "367116624592928"
                ),
                15
        );
    }
}
