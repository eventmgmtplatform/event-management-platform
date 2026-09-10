package com.eventmanagement.processor.adapters.http;

import com.eventmanagement.processor.domain.admin.AdminActor;
import jakarta.enterprise.context.RequestScoped;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.HttpHeaders;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import java.util.UUID;

/** Unverified caller metadata. Authentication and role enforcement are explicitly deferred. */
@RequestScoped
public class AdminRequestContext {
    @Context HttpHeaders headers;
    @ConfigProperty(name="processor.admin.enabled") boolean enabled;
    private final String requestId=UUID.randomUUID().toString();
    public String requestId(){return requestId;}
    public AdminActor actor() {
        if(!enabled)throw new ApiFailure(503,"ADMIN_API_DISABLED");
        String tenant=headers.getHeaderString("X-Tenant-Id");
        String actor=headers.getHeaderString("X-Actor-Id");
        if(tenant==null)throw new ApiFailure(400,"TENANT_HEADER_REQUIRED");
        try{return new AdminActor(actor==null?"local-operator":actor,tenant);}
        catch(IllegalArgumentException e){throw new ApiFailure(400,"INVALID_REQUEST_CONTEXT");}
    }
}
