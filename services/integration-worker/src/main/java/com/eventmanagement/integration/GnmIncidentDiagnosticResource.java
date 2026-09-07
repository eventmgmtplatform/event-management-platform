package com.eventmanagement.integration;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Local-only diagnostic seam used to certify the provider GET path.
 *
 * Disabled by default. It must never be considered a public product API.
 */
@Path("/internal/gnm/incidents")
@ApplicationScoped
@Produces(MediaType.APPLICATION_JSON)
public class GnmIncidentDiagnosticResource {

    private final GnmIncidentLookupClient lookupClient;
    private final boolean enabled;

    @Inject
    public GnmIncidentDiagnosticResource(
            GnmIncidentLookupClient lookupClient,
            @ConfigProperty(
                    name = "integration.gnm.diagnostic-get.enabled",
                    defaultValue = "false")
            boolean enabled) {

        this.lookupClient = lookupClient;
        this.enabled = enabled;
    }

    @GET
    @Path("/{organizationId}/{incidentId}")
    public Response getIncident(
            @PathParam("organizationId") String organizationId,
            @PathParam("incidentId") String incidentId
    ) throws Exception {

        if (!enabled) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }

        GnmIncidentSnapshot snapshot =
                lookupClient.getIncident(
                        organizationId,
                        incidentId
                );

        Map<String, Object> response =
                new LinkedHashMap<>();

        response.put(
                "incidentId",
                snapshot.incidentId()
        );

        response.put(
                "organizationId",
                snapshot.organizationId()
        );

        response.put(
                "incidentStatus",
                snapshot.incidentStatus()
        );

        response.put(
                "phaseId",
                snapshot.phaseId()
        );

        response.put(
                "phaseName",
                snapshot.phaseName()
        );

        response.put(
                "phaseNodeType",
                snapshot.phaseNodeType()
        );

        response.put(
                "notificationId",
                snapshot.notificationId()
        );

        response.put(
                "openConfirmed",
                snapshot.openConfirmed()
        );

        response.put(
                "closedConfirmed",
                snapshot.closedConfirmed()
        );

        return Response.ok(response).build();
    }
}
