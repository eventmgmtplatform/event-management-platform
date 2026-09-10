package com.eventmanagement.processor.adapters.http;

import com.eventmanagement.processor.domain.admin.AdminFailure;
import jakarta.inject.Inject;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.*;
import jakarta.ws.rs.ext.*;
import java.util.Map;

@Provider
public class AdminExceptionMapper implements ExceptionMapper<Exception> {
    @Inject AdminRequestContext identity;
    @Inject com.eventmanagement.processor.ports.out.RuleAdministration audit;
    @Context Request request;
    @Override public Response toResponse(Exception failure) {
        int status=500;String code="ADMIN_REQUEST_FAILED";
        if(failure instanceof AdminFailure known) {
            status=switch(known.kind()){case INVALID->422;case FORBIDDEN->403;case NOT_FOUND->404;case CONFLICT->409;case UNAVAILABLE->503;};code=known.getMessage();
        }else if(failure instanceof ApiFailure known){status=known.status();code=known.getMessage();}
        else if(failure instanceof IllegalArgumentException){status=422;code="CONFIGURATION_INVALID";}
        else if(failure instanceof WebApplicationException web){status=web.getResponse().getStatus();code="HTTP_REQUEST_REJECTED";}
        if("POST".equals(request.getMethod()) && (status==400||status==413||status==422||status==428)) {
            try {audit.rejected(identity.actor(),identity.requestId(),"VALIDATION","admin");}
            catch(Exception ignored) { /* No tenant-scoped audit can be attributed to an invalid request context. */ }
        }
        var builder=Response.status(status).type(MediaType.APPLICATION_JSON_TYPE).entity(Map.of("code",code,"message",code,
                "resource","event-processor-admin","requestId",identity.requestId(),"retryable",status==503));
        return builder.build();
    }
}
