package com.eventmanagement.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class GnmEverbridgeCloseMapperTest {

    private final ObjectMapper objectMapper =
            new ObjectMapper();

    private final GnmEverbridgeMapper mapper =
            new GnmEverbridgeMapper(objectMapper);

    @Test
    void shouldMapCertifiedCloseWithNotificationContract()
            throws Exception {

        JsonNode event =
                canonicalEvent();

        EverbridgeProviderContext context =
                providerContext();

        ObjectNode result =
                mapper.mapClose(
                        event,
                        context
                );

        assertEquals(
                "CloseWithNotification",
                result.path("incidentAction").asText()
        );

        assertEquals(
                "***Clear*** Alert for vit on utlcorpdp01",
                result.path("name").asText()
        );

        JsonNode phaseTemplate =
                result.path("incidentPhases")
                        .get(0)
                        .path("phaseTemplate");

        assertEquals(
                "8101205968426652",
                phaseTemplate.path("templateId").asText()
        );

        assertEquals(
                "GSMA_C00_HV",
                phaseTemplate.path("templateName").asText()
        );

        JsonNode broadcast =
                phaseTemplate.path(
                        "broadcastTemplate"
                );

        JsonNode settings =
                broadcast.path(
                        "broadcastSettings"
                );

        assertEquals(
                "false",
                settings.path("confirm").asText()
        );

        assertFalse(
                settings.has("cycleInterval"),
                "CloseWithNotification must omit cycleInterval"
        );

        assertEquals(
                "Contact",
                settings.path(
                        "deliveryPathOrder"
                ).asText()
        );

        JsonNode groupIds =
                broadcast.path(
                        "broadcastContacts"
                ).path("groupIds");

        assertTrue(groupIds.isArray());

        assertEquals(
                "3760896702677017",
                groupIds.get(0).asText()
        );

        assertEquals(
                "367116624592928",
                groupIds.get(1).asText()
        );
    }

    @Test
    void shouldNotPutProviderUriIdentityIntoCloseBody()
            throws Exception {

        ObjectNode result =
                mapper.mapClose(
                        canonicalEvent(),
                        providerContext()
                );

        assertFalse(
                result.has("organizationId")
        );

        assertFalse(
                result.has("incidentId")
        );
    }

    @Test
    void shouldPreserveCertifiedTemplateHierarchy()
            throws Exception {

        ObjectNode result =
                mapper.mapClose(
                        canonicalEvent(),
                        providerContext()
                );

        JsonNode phaseTemplate =
                result.path("incidentPhases")
                        .get(0)
                        .path("phaseTemplate");

        assertTrue(
                phaseTemplate.has("templateId")
        );

        assertTrue(
                phaseTemplate.has("templateName")
        );

        assertFalse(
                phaseTemplate.path("formTemplate")
                        .has("templateId")
        );

        assertFalse(
                phaseTemplate.path("formTemplate")
                        .has("templateName")
        );
    }

    private JsonNode canonicalEvent()
            throws Exception {

        return objectMapper.readTree(
                """
                {
                  "customerCode":"vit",
                  "customer":"VITRO",
                  "severity":3,
                  "severityName":"Minor",
                  "node":"utlcorpdp01",
                  "nodeAlias":"172.16.3.7",
                  "resourceId":"utlcorpdp01",
                  "component":"WIN",
                  "subcomponent":"VIT Windows",
                  "componentType":"",
                  "instanceId":"D:",
                  "instanceValue":"90.21 %",
                  "alertKey":"vit_dsp_gntm_win",
                  "identifier":"vit_dsp_gntm_win:utlcorpdp01:D::Zabbix",
                  "summary":"High Space Used on disk D: 90% (MINOR)",
                  "ticketNumber":"INC0576787",
                  "ticketGroup":"K-MX-VIT-DYNAMIC-AUTOMATION",
                  "legacyCorrelation":{
                    "serverSerial":"1266890674",
                    "serverName":"MX330P0SDCP"
                  },
                  "notification":{
                    "type":"HUNT",
                    "group":"VIT - VITRO - K - VITRO-MX-WINTEL",
                    "incidentId":"2713046552385248"
                  }
                }
                """
        );
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
