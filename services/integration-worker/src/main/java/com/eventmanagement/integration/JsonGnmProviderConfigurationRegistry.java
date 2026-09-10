package com.eventmanagement.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.io.InputStream;

@ApplicationScoped
public class JsonGnmProviderConfigurationRegistry
        implements GnmProviderConfigurationRegistry {

    private final GnmProviderConfiguration configuration;

    @Inject
    public JsonGnmProviderConfigurationRegistry(
            ObjectMapper objectMapper,
            @ConfigProperty(
                    name = "integration.gnm.registry.resource",
                    defaultValue = "gnm/provider-registry.json"
            ) String resource
    ) {
        if (resource == null || resource.isBlank()) {
            throw new IllegalArgumentException(
                    "integration.gnm.registry.resource cannot be blank"
            );
        }

        String normalized = resource.startsWith("/")
                ? resource.substring(1)
                : resource;

        ClassLoader classLoader =
                Thread.currentThread().getContextClassLoader();

        try (InputStream input =
                     resource.startsWith("file:") ? java.nio.file.Files.newInputStream(java.nio.file.Path.of(java.net.URI.create(resource))) : classLoader.getResourceAsStream(normalized)) {

            if (input == null) {
                throw new IllegalStateException(
                        "GNM provider registry not found: " + normalized
                );
            }

            this.configuration =
                    objectMapper.readValue(
                            input,
                            GnmProviderConfiguration.class
                    );

            validate(configuration);

        } catch (IllegalStateException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new IllegalStateException(
                    "Unable to load GNM provider registry: " + normalized,
                    exception
            );
        }
    }

    @Override
    public GnmProviderConfiguration configuration() {
        return configuration;
    }

    private void validate(
            GnmProviderConfiguration configuration
    ) {
        if (configuration == null) {
            throw new IllegalStateException(
                    "GNM provider registry is empty"
            );
        }

        if (configuration.provider() == null ||
                configuration.provider().isBlank()) {
            throw new IllegalStateException(
                    "GNM provider is required"
            );
        }

        if (!"EVERBRIDGE".equalsIgnoreCase(
                configuration.provider())) {
            throw new IllegalStateException(
                    "Unsupported GNM provider: " +
                            configuration.provider()
            );
        }

        if (configuration.tenants() == null ||
                configuration.tenants().isEmpty()) {
            throw new IllegalStateException(
                    "GNM tenant registry cannot be empty"
            );
        }
    }
}
