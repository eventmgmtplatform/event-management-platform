package com.eventmanagement.integration;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.Locale;
import java.util.Map;

@ApplicationScoped
public class GnmProviderContextResolver {

    private final GnmProviderConfigurationRegistry registry;

    @Inject
    public GnmProviderContextResolver(
            GnmProviderConfigurationRegistry registry
    ) {
        this.registry = registry;
    }

    public EverbridgeProviderContext resolve(
            String customerCode,
            String logicalGroup
    ) {
        String normalizedCustomer =
                requireText(customerCode, "customerCode")
                        .toLowerCase(Locale.ROOT);

        String normalizedGroup =
                requireText(logicalGroup, "logicalGroup");

        GnmProviderConfiguration configuration =
                registry.configuration();

        Map<String, GnmProviderConfiguration.Tenant> tenants =
                configuration.tenants();

        GnmProviderConfiguration.Tenant tenant =
                tenants.get(normalizedCustomer);

        if (tenant == null) {
            tenant = tenants.entrySet()
                    .stream()
                    .filter(entry ->
                            entry.getKey()
                                    .equalsIgnoreCase(
                                            normalizedCustomer
                                    ))
                    .map(Map.Entry::getValue)
                    .findFirst()
                    .orElse(null);
        }

        if (tenant == null) {
            throw new IllegalArgumentException(
                    "No GNM tenant configuration for customerCode: " +
                            customerCode
            );
        }

        GnmProviderConfiguration.Template template =
                tenant.incidentTemplate();

        if (template == null) {
            throw new IllegalStateException(
                    "No GNM incident template for customerCode: " +
                            customerCode
            );
        }

        Map<String, GnmProviderConfiguration.RecipientGroup> groups =
                tenant.recipientGroups();

        if (groups == null || groups.isEmpty()) {
            throw new IllegalStateException(
                    "No GNM recipient groups for customerCode: " +
                            customerCode
            );
        }

        GnmProviderConfiguration.RecipientGroup group =
                groups.get(normalizedGroup);

        if (group == null) {
            group = groups.entrySet()
                    .stream()
                    .filter(entry ->
                            entry.getKey()
                                    .equalsIgnoreCase(
                                            normalizedGroup
                                    ))
                    .map(Map.Entry::getValue)
                    .findFirst()
                    .orElse(null);
        }

        if (group == null) {
            throw new IllegalArgumentException(
                    "No GNM recipient group mapping for customerCode=" +
                            customerCode +
                            ", logicalGroup=" +
                            logicalGroup
            );
        }

        if (group.groupIds() == null ||
                group.groupIds().isEmpty()) {
            throw new IllegalStateException(
                    "GNM recipient group has no provider groupIds: " +
                            logicalGroup
            );
        }

        return new EverbridgeProviderContext(
                requireText(
                        tenant.organizationId(),
                        "organizationId"
                ),
                requireText(
                        template.templateId(),
                        "templateId"
                ),
                requireText(
                        template.templateName(),
                        "templateName"
                ),
                group.groupIds(),
                requirePositiveCycleInterval(
                        tenant.cycleInterval()
                )
        );
    }

    private int requirePositiveCycleInterval(
            int cycleInterval
    ) {
        if (cycleInterval <= 0) {
            throw new IllegalArgumentException(
                    "cycleInterval must be greater than zero"
            );
        }

        return cycleInterval;
    }

    private String requireText(
            String value,
            String field
    ) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(
                    field + " cannot be blank"
            );
        }

        return value.trim();
    }
}
