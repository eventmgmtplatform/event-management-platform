package com.eventmanagement.processor.adapters.http;
import com.eventmanagement.processor.application.AiopsEngine;
import com.eventmanagement.processor.domain.aiops.*;
import com.eventmanagement.processor.ports.out.AiopsConfigurations;
import com.fasterxml.jackson.core.*;
import com.fasterxml.jackson.databind.*;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.*;
import io.smallrye.common.annotation.Blocking;
import java.util.*;

@Path("/api/v1/aiops") @Blocking
@Produces(MediaType.APPLICATION_JSON) @Consumes(MediaType.APPLICATION_JSON)
public class AiopsResource {
    @Inject AdminRequestContext identity;
    @Inject AiopsConfigurations configurations;
    @Inject AiopsEngine engine;
    private final ObjectMapper mapper=new ObjectMapper(JsonFactory.builder().enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION).build())
            .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS);
    @GET public Object list(@QueryParam("limit") @DefaultValue("50") int limit,@QueryParam("after") @DefaultValue("") String after) {
        return configurations.list(identity.actor().tenant(),limit,after);
    }
    @GET @Path("/{id}") public Response get(@PathParam("id")String id) {
        return response(200,configurations.get(identity.actor().tenant(),id));
    }
    @POST public Response create(@HeaderParam("If-Match")String match,String raw) {
        var actor=identity.actor();var body=parse(raw,Set.of("id","name","enabled"));
        return response(201,configurations.save(actor,configuration(text(body,"id"),body),revision(match),true));
    }
    @PUT @Path("/{id}") public Response update(@PathParam("id")String id,@HeaderParam("If-Match")String match,String raw) {
        var actor=identity.actor();var body=parse(raw,Set.of("name","enabled"));
        return response(200,configurations.save(actor,configuration(id,body),revision(match),false));
    }
    @DELETE @Path("/{id}") public Response delete(@PathParam("id")String id,@HeaderParam("If-Match")String match) {
        configurations.delete(identity.actor(),id,revision(match));return Response.noContent().build();
    }
    @POST @Path("/{id}/assessments") public Object assess(@PathParam("id")String id,String raw) {
        var actor=identity.actor();var body=parse(raw,Set.of("resource","summary","severity"));
        if(!body.path("severity").isIntegralNumber() || !body.path("severity").canConvertToInt())throw new ApiFailure(422,"INVALID_SEVERITY");
        return engine.assess(actor.tenant(),id,new AiopsSignal(text(body,"resource"),text(body,"summary"),body.path("severity").intValue()));
    }
    private static AiopsConfiguration configuration(String id,JsonNode body) {
        if(!body.path("enabled").isBoolean())throw new ApiFailure(422,"ENABLED_REQUIRED");
        return new AiopsConfiguration(id,text(body,"name"),body.path("enabled").booleanValue(),0);
    }
    private JsonNode parse(String raw,Set<String> fields) {
        if(raw==null || raw.length()>8192)throw new ApiFailure(413,"REQUEST_SIZE_LIMIT");
        try {
            var body=mapper.readTree(raw);
            if(body==null || !body.isObject())throw new ApiFailure(400,"OBJECT_REQUIRED");
            body.fieldNames().forEachRemaining(field->{if(!fields.contains(field))throw new ApiFailure(400,"UNKNOWN_REQUEST_FIELD");});return body;
        }catch(ApiFailure e){throw e;}catch(Exception e){throw new ApiFailure(400,"INVALID_JSON");}
    }
    private static String text(JsonNode body,String field){if(!body.path(field).isTextual())throw new ApiFailure(422,"TEXT_REQUIRED");return body.path(field).textValue();}
    private static long revision(String match) {
        if(match==null)throw new ApiFailure(428,"IF_MATCH_REQUIRED");
        if(!match.matches("\"[0-9]{1,18}\""))throw new ApiFailure(400,"INVALID_IF_MATCH");
        return Long.parseLong(match.substring(1,match.length()-1));
    }
    private static Response response(int status,AiopsConfiguration value){return Response.status(status).tag(Long.toString(value.revision())).entity(value).build();}
}
