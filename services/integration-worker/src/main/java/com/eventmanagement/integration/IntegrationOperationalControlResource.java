package com.eventmanagement.integration;

import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.HeaderParam;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.MediaType;
import org.eclipse.microprofile.config.inject.ConfigProperty;

@Path("/integration/control")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class IntegrationOperationalControlResource {

    private final IntegrationOperationalControl control;
    private final String controlToken;

    @Inject
    public IntegrationOperationalControlResource(
            IntegrationOperationalControl control,
            @ConfigProperty(
                    name = "integration.control.token"
            ) String controlToken
    ) {
        this.control = control;
        this.controlToken = controlToken;
    }

    @GET
    public IntegrationOperationalControl.ControlSnapshot get(
            @HeaderParam("X-Integration-Control-Token") String token
    ) throws Exception {
        authorize(token);
        return control.snapshot();
    }

    @PUT
    public IntegrationOperationalControl.ControlSnapshot put(
            @HeaderParam("X-Integration-Control-Token") String token,
            ModeRequest request
    ) throws Exception {
        authorize(token);
        if (request == null) {
            throw new WebApplicationException("Request body is required", 400);
        }
        return control.changeMode(
                IntegrationOperatingMode.parse(request.operatingMode())
        );
    }

    private void authorize(String token) {
        if (controlToken == null || controlToken.isBlank()) {
            throw new WebApplicationException(
                    "Control token is not configured",
                    503
            );
        }
        if (!constantTimeEquals(controlToken, token)) {
            throw new WebApplicationException("Unauthorized", 401);
        }
    }

    static boolean constantTimeEquals(String expected, String actual) {
        if (expected == null || actual == null) {
            return false;
        }
        byte[] left = expected.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        byte[] right = actual.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        return java.security.MessageDigest.isEqual(left, right);
    }

    public record ModeRequest(String operatingMode) {
    }
}
