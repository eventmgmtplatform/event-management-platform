package com.eventmanagement.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.camel.Exchange;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.support.DefaultExchange;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class GnmCommandProcessorTest {

    private final ObjectMapper mapper =
            new ObjectMapper();

    private GnmCommandProcessor processor() {

        GnmProviderConfiguration configuration =
                new GnmProviderConfiguration(
                        "EVERBRIDGE",
                        Map.of(
                                "vit",
                                new GnmProviderConfiguration.Tenant(
                                        "vit",
                                        "453003085618991",
                                        new GnmProviderConfiguration.Template(
                                                "8101205968426652",
                                                "GSMA_C00_HV"
                                        ),
                                        15,
                                        Map.of(
                                                "VIT - VITRO - K - VITRO-MX-WINTEL",
                                                new GnmProviderConfiguration.RecipientGroup(
                                                        "VIT - VITRO - K - VITRO-MX-WINTEL",
                                                        List.of(
                                                                "3760896702677017",
                                                                "367116624592928"
                                                        )
                                                )
                                        )
                                )
                        )
                );

        GnmProviderContextResolver resolver =
                new GnmProviderContextResolver(
                        () -> configuration
                );

        GnmEverbridgeMapper everbridgeMapper =
                new GnmEverbridgeMapper(
                        mapper
                );

        return new GnmCommandProcessor(
                resolver,
                everbridgeMapper
        );
    }

    @Test
    void shouldPrepareCertifiedVitroEverbridgeLaunch()
            throws Exception {

        Exchange exchange = validExchange();

        processor().process(exchange);

        assertEquals(
                "HUNT",
                exchange.getProperty(
                        "gnmNotificationType"
                )
        );

        JsonNode canonical =
                exchange.getProperty(
                        "gnmCanonicalEvent",
                        JsonNode.class
                );

        assertNotNull(canonical);

        assertEquals(
                "1266890674",
                canonical.path("legacyCorrelation")
                        .path("serverSerial")
                        .asText()
        );

        EverbridgeProviderContext context =
                exchange.getProperty(
                        "gnmProviderContext",
                        EverbridgeProviderContext.class
                );

        assertNotNull(context);

        assertEquals(
                "453003085618991",
                context.organizationId()
        );

        assertEquals(
                "8101205968426652",
                context.templateId()
        );

        assertEquals(
                "GSMA_C00_HV",
                context.templateName()
        );

        assertEquals(
                List.of(
                        "3760896702677017",
                        "367116624592928"
                ),
                context.groupIds()
        );

        assertEquals(
                15,
                context.cycleInterval()
        );

        JsonNode providerRequest =
                exchange.getProperty(
                        "gnmProviderRequest",
                        JsonNode.class
                );

        assertNotNull(providerRequest);

        JsonNode golden =
                readResource(
                        "/gnm/vitro-everbridge-launch-golden.json"
                );

        assertEquals(
                golden,
                providerRequest,
                """
                GNM command preparation differs from the
                certified VITRO Everbridge Launch contract
                """
        );
    }

    @Test
    void shouldKeepCanonicalCommandFreeOfProviderConfiguration()
            throws Exception {

        Exchange exchange = validExchange();

        processor().process(exchange);

        JsonNode canonical =
                exchange.getProperty(
                        "gnmCanonicalEvent",
                        JsonNode.class
                );

        assertNotNull(canonical);

        assertFalse(
                canonical.has("organizationId")
        );

        assertFalse(
                canonical.has("templateId")
        );

        assertFalse(
                canonical.has("templateName")
        );

        assertFalse(
                canonical.has("groupIds")
        );

        assertFalse(
                canonical.has("cycleInterval")
        );
    }

    @Test
    void shouldRejectUnsupportedOperation()
            throws Exception {

        Exchange exchange = validExchange();

        exchange.setProperty(
                "operation",
                "CREATE_TICKET"
        );

        assertThrows(
                IllegalArgumentException.class,
                () -> processor().process(exchange)
        );
    }

    @Test
    void shouldRejectMissingServerSerial()
            throws Exception {

        Exchange exchange = validExchange();

        exchange.setProperty(
                "integrationPayload",
                mapper.readTree("""
                        {
                          "customerCode": "vit",
                          "customer": "VITRO",
                          "severity": 3,
                          "severityName": "Minor",
                          "node": "utlcorpdp01",
                          "resourceId": "utlcorpdp01",
                          "summary": "High Space Used",
                          "legacyCorrelation": {},
                          "notification": {
                            "type": "HUNT",
                            "group": "VIT - VITRO - K - VITRO-MX-WINTEL"
                          }
                        }
                        """)
        );

        assertThrows(
                IllegalArgumentException.class,
                () -> processor().process(exchange)
        );
    }

    @Test
    void shouldRejectUnsupportedNotificationType()
            throws Exception {

        Exchange exchange = validExchange();

        exchange.setProperty(
                "integrationPayload",
                mapper.readTree("""
                        {
                          "customerCode": "vit",
                          "customer": "VITRO",
                          "severity": 3,
                          "severityName": "Minor",
                          "node": "utlcorpdp01",
                          "resourceId": "utlcorpdp01",
                          "summary": "High Space Used",
                          "legacyCorrelation": {
                            "serverSerial": "1266890674"
                          },
                          "notification": {
                            "type": "UNKNOWN",
                            "group": "VIT - VITRO - K - VITRO-MX-WINTEL"
                          }
                        }
                        """)
        );

        assertThrows(
                IllegalArgumentException.class,
                () -> processor().process(exchange)
        );
    }

    @Test
    void shouldRejectUnknownProviderGroup()
            throws Exception {

        Exchange exchange = validExchange();

        JsonNode payload =
                exchange.getProperty(
                        "integrationPayload",
                        JsonNode.class
                );

        ((com.fasterxml.jackson.databind.node.ObjectNode)
                payload.path("notification"))
                .put(
                        "group",
                        "UNKNOWN-GROUP"
                );

        IllegalArgumentException exception =
                assertThrows(
                        IllegalArgumentException.class,
                        () -> processor().process(exchange)
                );

        assertTrue(
                exception.getMessage()
                        .contains(
                                "No GNM recipient group mapping"
                        )
        );

        assertNull(
                exchange.getProperty(
                        "gnmProviderRequest"
                )
        );
    }

    @Test
    void shouldNotProduceTransportOrCredentialMaterial()
            throws Exception {

        Exchange exchange = validExchange();

        processor().process(exchange);

        JsonNode providerRequest =
                exchange.getProperty(
                        "gnmProviderRequest",
                        JsonNode.class
                );

        String serialized =
                mapper.writeValueAsString(
                        providerRequest
                ).toLowerCase();

        assertFalse(
                serialized.contains(
                        "authorization"
                )
        );

        assertFalse(
                serialized.contains(
                        "password"
                )
        );

        assertFalse(
                serialized.contains(
                        "proxy"
                )
        );

        assertFalse(
                serialized.contains(
                        "api.everbridge.net"
                )
        );
    }

    private Exchange validExchange()
            throws Exception {

        Exchange exchange =
                new DefaultExchange(
                        new DefaultCamelContext()
                );

        exchange.setProperty(
                "commandId",
                "cmd-gnm-001"
        );

        exchange.setProperty(
                "eventId",
                "evt-gnm-001"
        );

        exchange.setProperty(
                "operation",
                "SEND_NOTIFICATION"
        );

        exchange.setProperty(
                "integrationPayload",
                readResource(
                        "/gnm/vitro-canonical-event.json"
                )
        );

        return exchange;
    }

    private JsonNode readResource(
            String resource
    ) throws Exception {

        try (InputStream input =
                     getClass()
                             .getResourceAsStream(
                                     resource
                             )) {

            assertNotNull(
                    input,
                    "Missing test resource: " +
                            resource
            );

            return mapper.readTree(
                    input
            );
        }
    }
}
