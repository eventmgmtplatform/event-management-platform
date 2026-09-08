package com.eventmanagement.integration.cacf;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.NoSuchElementException;
import java.util.UUID;

@Path("/")
public class AutomationResource {
    private final AutomationRepository repository;private final CacfSettings settings;private final ObjectMapper mapper;
    @Inject public AutomationResource(AutomationRepository repository,CacfSettings settings,ObjectMapper mapper){this.repository=repository;this.settings=settings;this.mapper=mapper;}
    private void authorize(String token){
        if(!settings.enabled)throw new NotFoundException();
        if(token==null || !MessageDigest.isEqual(settings.token.getBytes(StandardCharsets.UTF_8),token.getBytes(StandardCharsets.UTF_8)))throw new NotAuthorizedException("CACF token required");
    }
    @POST @Path("api/v1/automations") @Consumes(MediaType.APPLICATION_JSON) @Produces(MediaType.APPLICATION_JSON)
    public Response submit(@HeaderParam("X-CACF-Token") String token,@HeaderParam("Idempotency-Key") String key,JsonNode request) throws Exception {
        authorize(token);
        try {
            var input=AutomationRequest.parse(request,null,null,settings.resultTimeout);
            if(key==null || !key.equals(input.executionId().toString()))throw new IllegalArgumentException("Idempotency-Key must equal executionId");
            var result=repository.accept(input);
            return Response.accepted(mapper.createObjectNode().put("executionId",input.executionId().toString()).put("state",result.path("state").asText()).put("accepted",true)).build();
        }catch(AutomationRepository.Conflict e){return error(409,e.getMessage());}catch(IllegalArgumentException e){return error(400,e.getMessage());}
    }
    @GET @Path("api/v1/automations/{id}") @Produces(MediaType.APPLICATION_JSON)
    public Response get(@HeaderParam("X-CACF-Token") String token,@PathParam("id") String id) throws Exception {
        authorize(token);
        try {
            var row=repository.get(UUID.fromString(id));
            if(row==null)return error(404,"Automation not found");
            var result=mapper.createObjectNode();
            String[][] fields={{"executionId","execution_id"},{"eventId","event_id"},{"eventKey","event_key"},{"providerExecutionId","provider_execution_id"},
                {"itsmTicketNumber","itsm_ticket_number"},{"state","state"},{"outcome","outcome"},{"requestedAt","requested_at"},{"acceptedAt","accepted_at"},{"deadlineAt","deadline_at"},{"completedAt","completed_at"}};
            for(String[] field:fields)result.set(field[0],row.path(field[1]));
            result.put("provider","NEXT");result.put("requiresReview","UNKNOWN".equals(row.path("outcome").asText()));
            return Response.ok(result).build();
        }
        catch(IllegalArgumentException e){return error(400,"Invalid executionId");}
    }
    @PUT @Path("api/v1/automations/{id}/ticket") @Consumes(MediaType.APPLICATION_JSON) @Produces(MediaType.APPLICATION_JSON)
    public Response ticket(@HeaderParam("X-CACF-Token") String token,@PathParam("id") String id,JsonNode request) throws Exception {
        authorize(token);
        try {repository.associateTicket(UUID.fromString(id),AutomationRequest.text(request,"number",100));return Response.noContent().build();}
        catch(AutomationRepository.Conflict e){return error(409,e.getMessage());}catch(NoSuchElementException e){return error(404,e.getMessage());}catch(IllegalArgumentException e){return error(400,e.getMessage());}
    }
    @POST @Path("data") @Consumes({MediaType.APPLICATION_XML,"text/xml"})
    public Response legacy(@HeaderParam("X-CACF-Token") String token,InputStream input) throws Exception {return callback(token,input);}
    @POST @Path("api/v1/providers/next/callback") @Consumes({MediaType.APPLICATION_XML,"text/xml"})
    public Response callback(@HeaderParam("X-CACF-Token") String token,InputStream input) throws Exception {
        authorize(token);
        byte[] xml=input.readNBytes(settings.maxXmlBytes+1);
        if(xml.length>settings.maxXmlBytes)return error(413,"XML payload exceeds size limit");
        try {repository.callback(NextAdapter.parse(xml,settings.maxXmlBytes));return Response.ok("request is applied",MediaType.TEXT_PLAIN).build();}
        catch(AutomationRepository.Conflict e){return error(409,e.getMessage());}catch(NoSuchElementException e){return error(404,e.getMessage());}catch(IllegalArgumentException e){return error(400,e.getMessage());}
    }
    private Response error(int code,String message){return Response.status(code).type(MediaType.APPLICATION_JSON).entity(mapper.createObjectNode().put("error",message)).build();}

    @GET @Path("metrics") @Produces(MediaType.TEXT_PLAIN)
    public String metrics(@HeaderParam("X-CACF-Token") String token) throws Exception {
        authorize(token);StringBuilder output=new StringBuilder();
        repository.metrics().forEach((name,value)->output.append("# TYPE cacf_").append(name).append(" gauge\n").append("cacf_").append(name).append(' ').append(value).append('\n'));
        return output.toString();
    }
}
