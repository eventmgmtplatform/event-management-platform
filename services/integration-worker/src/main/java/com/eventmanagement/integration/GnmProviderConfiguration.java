package com.eventmanagement.integration;

import java.util.List;
import java.util.Map;

public record GnmProviderConfiguration(
        String provider,
        Map<String, Tenant> tenants
) {

    public record Tenant(
            String customerCode,
            String organizationId,
            Template incidentTemplate,
            int cycleInterval,
            Map<String, RecipientGroup> recipientGroups
    ) {
    }

    public record Template(
            String templateId,
            String templateName
    ) {
    }

    public record RecipientGroup(
            String logicalName,
            List<String> groupIds
    ) {
    }
}
