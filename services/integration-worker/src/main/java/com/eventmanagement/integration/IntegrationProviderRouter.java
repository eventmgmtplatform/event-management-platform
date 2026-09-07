package com.eventmanagement.integration;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Named;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.jboss.logging.Logger;

@Named("integrationProviderRouter")
@ApplicationScoped
public class IntegrationProviderRouter implements Processor {

    private static final Logger LOG =
            Logger.getLogger(IntegrationProviderRouter.class);

    public static final String PROVIDER_PROPERTY =
            "integrationProvider";

    @Override
    public void process(Exchange exchange) {

        String integrationType =
                exchange.getProperty(
                        "integrationType",
                        String.class
                );

        IntegrationProvider provider =
                IntegrationProvider.from(integrationType);

        exchange.setProperty(
                PROVIDER_PROPERTY,
                provider.name()
        );

        LOG.infov(
                "Integration provider resolved: integrationType={0}, provider={1}",
                integrationType,
                provider
        );
    }
}
