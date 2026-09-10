package com.eventmanagement.gateway;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ZabbixMessageBusNormalizerTest {

    private static final String EVENT_ID =
            "11111111-2222-3333-4444-555555555555";

    private static final String RECEIVED_AT =
            "2026-08-13T04:30:00Z";

    private static final String EXPECTED_EVENT_KEY =
            "SDC:Zabbix:" +
            "sdc_portconn_rlzc_ncomni_itm:" +
            "sdcgdcimpro01:" +
            "net.tcp.port[158.98.59.35,8080]";

    private static final String EXPECTED_LEGACY_EVENT_KEY =
            "sdc_portconn_rlzc_ncomni_itm:" +
            "sdcgdcimpro01:" +
            "net.tcp.port[158.98.59.35,8080]:" +
            "Zabbix";

    private ObjectMapper objectMapper;
    private ZabbixMessageBusNormalizer normalizer;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        normalizer =
                new ZabbixMessageBusNormalizer(objectMapper);
    }

    @Test
    void normalizesProblemAsOpenEvent() throws Exception {

        JsonNode input = fixture(
                "zabbix-messagebus-problem.json"
        );

        ObjectNode normalized =
                normalizer.normalize(
                        input,
                        EVENT_ID,
                        RECEIVED_AT
                );

        assertEquals(
                "1.1",
                normalized.path("schemaVersion").asText()
        );

        assertEquals(
                EVENT_ID,
                normalized.path("eventId").asText()
        );

        assertEquals(
                EXPECTED_EVENT_KEY,
                normalized.path("eventKey").asText()
        );

        assertEquals(
                EXPECTED_LEGACY_EVENT_KEY,
                normalized.path("legacyEventKey").asText()
        );

        assertEquals(
                "SDC",
                normalized.path("tenant")
                        .path("code")
                        .asText()
        );

        assertEquals(
                "OPEN",
                normalized.path("lifecycleAction").asText()
        );

        assertEquals(
                "1",
                normalized.path("sourceType").asText()
        );

        assertEquals(
                "0",
                normalized.path("condition")
                        .path("instanceValue")
                        .asText()
        );

        assertEquals(
                5,
                normalized.path("sourceSeverity").asInt()
        );

        assertEquals(
                5,
                normalized.path("effectiveSeverity").asInt()
        );

        assertEquals(
                900,
                normalized.path("expirationSeconds").asInt()
        );
    }

    @Test
    void normalizesRecoveryAsCloseWithoutLosingSourceSeverity()
            throws Exception {

        JsonNode input = fixture(
                "zabbix-messagebus-recovery.json"
        );

        ObjectNode normalized =
                normalizer.normalize(
                        input,
                        EVENT_ID,
                        RECEIVED_AT
                );

        assertEquals(
                EXPECTED_EVENT_KEY,
                normalized.path("eventKey").asText()
        );

        assertEquals(
                EXPECTED_LEGACY_EVENT_KEY,
                normalized.path("legacyEventKey").asText()
        );

        assertEquals(
                "CLOSE",
                normalized.path("lifecycleAction").asText()
        );

        assertEquals(
                "0",
                normalized.path("sourceType").asText()
        );

        assertEquals(
                "1",
                normalized.path("condition")
                        .path("instanceValue")
                        .asText()
        );

        assertEquals(
                5,
                normalized.path("sourceSeverity").asInt()
        );

        assertEquals(
                0,
                normalized.path("effectiveSeverity").asInt()
        );
    }

    @Test
    void convertsSubcomponentsToTrimmedArray()
            throws Exception {

        ObjectNode normalized =
                normalizer.normalize(
                        fixture(
                                "zabbix-messagebus-problem.json"
                        ),
                        EVENT_ID,
                        RECEIVED_AT
                );

        JsonNode subcomponents =
                normalized.path("resource")
                        .path("subcomponents");

        assertTrue(subcomponents.isArray());
        assertEquals(2, subcomponents.size());

        assertEquals(
                "SDC Linux Servers",
                subcomponents.get(0).asText()
        );

        assertEquals(
                "SDC Socks Servers",
                subcomponents.get(1).asText()
        );
    }

    @Test
    void preservesSd2AsIndependentTenant()
            throws Exception {

        ObjectNode input =
                (ObjectNode) fixture(
                        "zabbix-messagebus-problem.json"
                );

        input.put("CustomerCode", "SD2");

        ObjectNode normalized =
                normalizer.normalize(
                        input,
                        EVENT_ID,
                        RECEIVED_AT
                );

        assertEquals(
                "SD2",
                normalized.path("tenant")
                        .path("code")
                        .asText()
        );

        assertTrue(
                normalized.path("eventKey")
                        .asText()
                        .startsWith("SD2:Zabbix:")
        );

        assertEquals(
                "SD2",
                normalized.path("originalEvent")
                        .path("CustomerCode")
                        .asText()
        );
    }

    @Test
    void rejectsContradictoryLifecycleCombination()
            throws Exception {

        ObjectNode input =
                (ObjectNode) fixture(
                        "zabbix-messagebus-problem.json"
                );

        input.put("Type", "0");
        input.put("InstanceValue", "0");

        IllegalArgumentException exception =
                assertThrows(
                        IllegalArgumentException.class,
                        () -> normalizer.normalize(
                                input,
                                EVENT_ID,
                                RECEIVED_AT
                        )
                );

        assertEquals(
                "Combinación Zabbix inválida: " +
                "Type=0, InstanceValue=0",
                exception.getMessage()
        );
    }

    @Test
    void rejectsMissingRequiredField()
            throws Exception {

        ObjectNode input =
                (ObjectNode) fixture(
                        "zabbix-messagebus-problem.json"
                );

        input.remove("AlertKey");

        IllegalArgumentException exception =
                assertThrows(
                        IllegalArgumentException.class,
                        () -> normalizer.normalize(
                                input,
                                EVENT_ID,
                                RECEIVED_AT
                        )
                );

        assertEquals(
                "Campo Zabbix obligatorio ausente: AlertKey",
                exception.getMessage()
        );
    }

    @Test
    void preservesOriginalEventAsIndependentCopy()
            throws Exception {

        ObjectNode input =
                (ObjectNode) fixture(
                        "zabbix-messagebus-problem.json"
                );

        ObjectNode normalized =
                normalizer.normalize(
                        input,
                        EVENT_ID,
                        RECEIVED_AT
                );

        JsonNode original =
                normalized.path("originalEvent");

        assertEquals(input, original);
        assertNotSame(input, original);

        input.put("CustomerCode", "MUTATED");

        assertEquals(
                "SDC",
                original.path("CustomerCode").asText()
        );
    }

    @Test
    void detectsNativeZabbixContract() throws Exception {

        JsonNode nativeEvent = fixture(
                "zabbix-messagebus-problem.json"
        );

        JsonNode legacyEvent =
                objectMapper.readTree(
                        Files.readString(
                                Path.of(
                                        "../../testing/fixtures/events/" +
                                        "zabbix-problem.json"
                                )
                        )
                );

        assertTrue(normalizer.supports(nativeEvent));
        assertTrue(!normalizer.supports(legacyEvent));
    }

    private JsonNode fixture(String filename)
            throws Exception {

        Path path = Path.of(
                "../../testing/fixtures/events/sdc",
                filename
        );

        return objectMapper.readTree(
                Files.readString(path)
        );
    }
}
