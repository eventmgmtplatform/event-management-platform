package com.eventmanagement.integration;

public enum IntegrationProvider {

    SERVICENOW,
    GLPI,
    GNM,
    CACF;

    public static IntegrationProvider from(String value) {

        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(
                    "integrationType no puede estar vacío"
            );
        }

        try {
            return IntegrationProvider.valueOf(
                    value.trim().toUpperCase()
            );
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException(
                    "Integración no soportada: " + value.trim().toUpperCase(),
                    exception
            );
        }
    }
}
