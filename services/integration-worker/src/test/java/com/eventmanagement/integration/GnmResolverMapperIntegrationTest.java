package com.eventmanagement.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.io.InputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class GnmResolverMapperIntegrationTest {

    private final ObjectMapper objectMapper =
            new ObjectMapper();

    @Test
    void resolvesVitroConfigurationAndProducesCertifiedGoldenLaunch()
            throws Exception {

        JsonNode canonical =
                readJson(
                        "gnm/vitro-canonical-event.json"
                );

        JsonNode golden =
                readJson(
                        "gnm/vitro-everbridge-launch-golden.json"
                );

        GnmProviderConfiguration configuration =
                readConfiguration(
                        "gnm/provider-registry.json"
                );

        GnmProviderConfigurationRegistry registry =
                () -> configuration;

        GnmProviderContextResolver resolver =
                new GnmProviderContextResolver(
                        registry
                );

        String customerCode =
                canonical.path("customerCode").asText();

        String logicalGroup =
                canonical.path("notification")
                        .path("group")
                        .asText();

        EverbridgeProviderContext context =
                resolver.resolve(
                        customerCode,
                        logicalGroup
                );

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
                2,
                context.groupIds().size()
        );

        assertEquals(
                15,
                context.cycleInterval()
        );

        GnmEverbridgeMapper mapper =
                new GnmEverbridgeMapper(
                        objectMapper
                );

        JsonNode actual =
                mapper.mapLaunch(
                        canonical,
                        context
                );

        assertNotNull(actual);

        assertEquals(
                golden,
                actual,
                """
                Resolver → Mapper output differs from the
                certified VITRO Everbridge Launch contract
                """
        );
    }

    private JsonNode readJson(
            String resource
    ) throws Exception {

        try (InputStream input =
                     getClass()
                             .getClassLoader()
                             .getResourceAsStream(resource)) {

            assertNotNull(
                    input,
                    "Missing test resource: " + resource
            );

            return objectMapper.readTree(input);
        }
    }

    private GnmProviderConfiguration readConfiguration(
            String resource
    ) throws Exception {

        try (InputStream input =
                     getClass()
                             .getClassLoader()
                             .getResourceAsStream(resource)) {

            assertNotNull(
                    input,
                    "Missing configuration resource: " + resource
            );

            return objectMapper.readValue(
                    input,
                    GnmProviderConfiguration.class
            );
        }
    }
}
