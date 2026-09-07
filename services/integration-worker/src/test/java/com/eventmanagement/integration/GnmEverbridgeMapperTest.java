package com.eventmanagement.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class GnmEverbridgeMapperTest {

    private final ObjectMapper objectMapper =
            new ObjectMapper();

    private final GnmEverbridgeMapper mapper =
            new GnmEverbridgeMapper(objectMapper);

    private EverbridgeProviderContext vitroContext() {

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

    @Test
    void shouldMatchCertifiedVitroGoldenContract()
            throws Exception {

        JsonNode canonical =
                read(
                        "/gnm/vitro-canonical-event.json"
                );

        JsonNode expected =
                read(
                        "/gnm/vitro-everbridge-launch-golden.json"
                );

        ObjectNode actual =
                mapper.mapLaunch(
                        canonical,
                        vitroContext()
                );

        assertEquals(
                expected,
                actual,
                () ->
                        "Everbridge provider JSON differs from certified VITRO golden contract.\nExpected:\n" +
                        pretty(expected) +
                        "\nActual:\n" +
                        pretty(actual)
        );
    }

    @Test
    void templateIdentityMustBeSiblingOfFormTemplate()
            throws Exception {

        ObjectNode actual =
                mapper.mapLaunch(
                        read(
                                "/gnm/vitro-canonical-event.json"
                        ),
                        vitroContext()
                );

        JsonNode phaseTemplate =
                actual.path("incidentPhases")
                        .path(0)
                        .path("phaseTemplate");

        assertEquals(
                "8101205968426652",
                phaseTemplate.path(
                        "templateId"
                ).asText()
        );

        assertEquals(
                "GSMA_C00_HV",
                phaseTemplate.path(
                        "templateName"
                ).asText()
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

    @Test
    void shouldPreserveProviderConfirmedVariableMappings()
            throws Exception {

        ObjectNode actual =
                mapper.mapLaunch(
                        read(
                                "/gnm/vitro-canonical-event.json"
                        ),
                        vitroContext()
                );

        JsonNode variables =
                actual.path("incidentPhases")
                        .path(0)
                        .path("phaseTemplate")
                        .path("formTemplate")
                        .path("formVariableItems");

        assertEquals(20, variables.size());

        assertVariable(
                variables,
                EverbridgeVariableCatalog.SERVER_SERIAL,
                "1266890674"
        );

        assertVariable(
                variables,
                EverbridgeVariableCatalog.SUMMARY,
                "High Space Used on disk D: 90% (MINOR)"
        );

        assertVariable(
                variables,
                EverbridgeVariableCatalog.COMPONENT_TYPE,
                ""
        );

        assertVariable(
                variables,
                EverbridgeVariableCatalog.ALERT_KEY,
                "vit_dsp_gntm_win"
        );

        assertVariable(
                variables,
                EverbridgeVariableCatalog.RESOURCE_ID,
                "utlcorpdp01"
        );
    }

    @Test
    void shouldNotContainTransportOrCredentialMaterial()
            throws Exception {

        ObjectNode actual =
                mapper.mapLaunch(
                        read(
                                "/gnm/vitro-canonical-event.json"
                        ),
                        vitroContext()
                );

        String serialized =
                objectMapper.writeValueAsString(actual)
                        .toLowerCase();

        assertFalse(serialized.contains("authorization"));
        assertFalse(serialized.contains("password"));
        assertFalse(serialized.contains("proxy"));
        assertFalse(serialized.contains("api.everbridge.net"));
    }

    private JsonNode read(String resource)
            throws Exception {

        try (InputStream input =
                     getClass().getResourceAsStream(
                             resource
                     )) {

            assertNotNull(
                    input,
                    "Missing test resource: " + resource
            );

            return objectMapper.readTree(input);
        }
    }

    private String pretty(JsonNode node) {

        try {
            return objectMapper
                    .writerWithDefaultPrettyPrinter()
                    .writeValueAsString(node);
        } catch (Exception exception) {
            return node.toString();
        }
    }

    private void assertVariable(
            JsonNode variables,
            String variableId,
            String expectedValue
    ) {

        for (JsonNode variable : variables) {

            if (variableId.equals(
                    variable.path(
                            "variableId"
                    ).asText()
            )) {

                assertEquals(
                        expectedValue,
                        variable.path("val")
                                .path(0)
                                .asText()
                );

                return;
            }
        }

        fail(
                "Missing Everbridge variableId: " +
                        variableId
        );
    }
}
