package com.eventmanagement.integration.cacf;

import jakarta.inject.Singleton;
import jakarta.inject.Inject;
import java.util.Optional;
import org.eclipse.microprofile.config.inject.ConfigProperty;

@Singleton
public class CacfSettings {
    public final boolean enabled;
    public final String baseUrl, username, password, token;
    public final int httpTimeout, resultTimeout, acknowledgementTimeout, maxXmlBytes;
    @Inject
    public CacfSettings(
            @ConfigProperty(name="cacf.enabled",defaultValue="false") boolean enabled,
            @ConfigProperty(name="cacf.next.base-url",defaultValue="http://next-mock:8080") String baseUrl,
            @ConfigProperty(name="cacf.next.username") Optional<String> username,
            @ConfigProperty(name="cacf.next.password") Optional<String> password,
            @ConfigProperty(name="cacf.api-token") Optional<String> token,
            @ConfigProperty(name="cacf.next.http-timeout-seconds",defaultValue="180") int httpTimeout,
            @ConfigProperty(name="cacf.result-timeout-seconds",defaultValue="600") int resultTimeout,
            @ConfigProperty(name="cacf.acknowledgement-timeout-seconds",defaultValue="600") int acknowledgementTimeout,
            @ConfigProperty(name="cacf.xml.max-bytes",defaultValue="1048576") int maxXmlBytes) {
        this.enabled=enabled;this.baseUrl=baseUrl;this.username=username.orElse("");this.password=password.orElse("");this.token=token.orElse("");
        this.httpTimeout=httpTimeout;this.resultTimeout=resultTimeout;this.acknowledgementTimeout=acknowledgementTimeout;this.maxXmlBytes=maxXmlBytes;
        if(httpTimeout<1 || httpTimeout>3600 || resultTimeout<1 || resultTimeout>604800 || acknowledgementTimeout<1 || acknowledgementTimeout>604800 || maxXmlBytes<1 || maxXmlBytes>16777216)throw new IllegalArgumentException("CACF limits are outside the supported range");
        if(enabled && (this.username.isBlank() || this.password.isBlank() || this.token.isBlank()))throw new IllegalArgumentException("CACF credentials are required when enabled");
    }
}
