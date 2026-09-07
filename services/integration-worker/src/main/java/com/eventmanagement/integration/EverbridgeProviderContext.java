package com.eventmanagement.integration;

import java.util.List;

public record EverbridgeProviderContext(
        String organizationId,
        String templateId,
        String templateName,
        List<String> groupIds,
        int cycleInterval
) {

    public EverbridgeProviderContext {

        if (organizationId == null || organizationId.isBlank()) {
            throw new IllegalArgumentException(
                    "Everbridge organizationId is required"
            );
        }

        if (templateId == null || templateId.isBlank()) {
            throw new IllegalArgumentException(
                    "Everbridge templateId is required"
            );
        }

        if (templateName == null || templateName.isBlank()) {
            throw new IllegalArgumentException(
                    "Everbridge templateName is required"
            );
        }

        if (groupIds == null || groupIds.isEmpty()) {
            throw new IllegalArgumentException(
                    "Everbridge groupIds are required"
            );
        }

        groupIds = List.copyOf(groupIds);

        if (groupIds.stream().anyMatch(
                value -> value == null || value.isBlank()
        )) {
            throw new IllegalArgumentException(
                    "Everbridge groupIds cannot contain blank values"
            );
        }

        if (cycleInterval <= 0) {
            throw new IllegalArgumentException(
                    "Everbridge cycleInterval must be positive"
            );
        }
    }
}
