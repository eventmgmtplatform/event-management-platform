package com.eventmanagement.integration;

import java.util.Locale;

public enum IntegrationOperatingMode {
    STANDBY,
    ACTIVE,
    PULL_RESTART;

    public static IntegrationOperatingMode parse(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(
                    "operatingMode is required"
            );
        }
        try {
            return valueOf(
                    value.trim().toUpperCase(Locale.ROOT)
            );
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException(
                    "Unsupported operatingMode: " + value,
                    exception
            );
        }
    }
}
