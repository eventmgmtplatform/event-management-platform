package com.eventmanagement.integration;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class GnmProviderContextResolverTest {

    private GnmProviderContextResolver resolver() {

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

        return new GnmProviderContextResolver(
                () -> configuration
        );
    }

    @Test
    void resolvesCertifiedVitroProviderContext() {

        EverbridgeProviderContext context =
                resolver().resolve(
                        "vit",
                        "VIT - VITRO - K - VITRO-MX-WINTEL"
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
    }

    @Test
    void resolvesCustomerCodeCaseInsensitively() {

        EverbridgeProviderContext context =
                resolver().resolve(
                        "VIT",
                        "VIT - VITRO - K - VITRO-MX-WINTEL"
                );

        assertEquals(
                "453003085618991",
                context.organizationId()
        );
    }

    @Test
    void rejectsUnknownTenant() {

        IllegalArgumentException exception =
                assertThrows(
                        IllegalArgumentException.class,
                        () -> resolver().resolve(
                                "unknown",
                                "VIT - VITRO - K - VITRO-MX-WINTEL"
                        )
                );

        assertEquals(
                "No GNM tenant configuration for customerCode: unknown",
                exception.getMessage()
        );
    }

    @Test
    void rejectsUnknownLogicalGroup() {

        IllegalArgumentException exception =
                assertThrows(
                        IllegalArgumentException.class,
                        () -> resolver().resolve(
                                "vit",
                                "UNKNOWN-GROUP"
                        )
                );

        assertEquals(
                "No GNM recipient group mapping for customerCode=vit, logicalGroup=UNKNOWN-GROUP",
                exception.getMessage()
        );
    }
}
