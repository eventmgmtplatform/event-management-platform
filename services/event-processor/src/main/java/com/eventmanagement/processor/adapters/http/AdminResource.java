package com.eventmanagement.processor.adapters.http;

import com.eventmanagement.processor.application.AdminService;
import com.eventmanagement.processor.adapters.kafka.GatewayEventAdapter;
import com.eventmanagement.processor.ports.out.RuleAdministration;
import com.fasterxml.jackson.core.*;
import com.fasterxml.jackson.databind.*;
import io.smallrye.common.annotation.Blocking;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.*;
import java.util.*;

@Path("/api/v1")  @Blocking
@Produces(MediaType.APPLICATION_JSON) @Consumes(MediaType.APPLICATION_JSON)
public class AdminResource {
    @Inject AdminService service;
    @Inject AdminRequestContext identity;
    @Inject GatewayEventAdapter gateway;
    private final ObjectMapper mapper=new ObjectMapper(JsonFactory.builder().enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
            .streamReadConstraints(StreamReadConstraints.builder().maxNestingDepth(40).maxStringLength(65536).build()).build())
            .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS);
    @GET @Path("/rules")
    public JsonNode list(@QueryParam("limit") @DefaultValue("50") int limit,@QueryParam("after") @DefaultValue("") String after) throws Exception {
        return mapper.readTree(service.list(identity.actor(),limit,after,identity.requestId()));
    }
    @GET @Path("/rules/{id}")
    public Response get(@PathParam("id")String id,@QueryParam("version")Integer version)throws Exception {
        JsonNode result=mapper.readTree(service.get(identity.actor(),id,version,identity.requestId()));
        return Response.ok(result).tag(Long.toString(result.path("revision").asLong())).build();
    }
    @GET @Path("/rules/{id}/history")
    public JsonNode history(@PathParam("id")String id,@QueryParam("limit")@DefaultValue("50")int limit,
                            @QueryParam("after")@DefaultValue("0")long after)throws Exception {
        return mapper.readTree(service.history(identity.actor(),id,limit,after,identity.requestId()));
    }
    @POST @Path("/rules/validate")
    public Object validate(String body) {
        var actor=identity.actor();var parsed=body(body,Set.of("rule"));required(parsed,"rule");
        var result=service.validate(actor,parsed.get("rule").toString(),identity.requestId());
        return Map.of("valid",true,"id",result.rule().id(),"version",result.rule().version(),"checksum",result.rule().checksum());
    }
    @POST @Path("/rules")
    public Response create(@HeaderParam("If-Match")String match,@HeaderParam("Idempotency-Key")String requestId,String body) {
        var actor=identity.actor();var parsed=body(body,Set.of("rule","reason"));required(parsed,"rule");
        var receipt=service.mutate(new RuleAdministration.Mutation(actor,requestId,revision(match),null,0,
                RuleAdministration.Change.CREATE,parsed.get("rule").toString(),text(parsed,"reason")));
        return Response.status(201).tag(Long.toString(receipt.revision())).entity(receipt).build();
    }
    @POST @Path("/rules/{id}/enable")
    public Response enable(@PathParam("id")String id,@HeaderParam("If-Match")String match,@HeaderParam("Idempotency-Key")String requestId,String body) {
        return transition(id,match,requestId,body,RuleAdministration.Change.ENABLE);
    }
    @POST @Path("/rules/{id}/disable")
    public Response disable(@PathParam("id")String id,@HeaderParam("If-Match")String match,@HeaderParam("Idempotency-Key")String requestId,String body) {
        return transition(id,match,requestId,body,RuleAdministration.Change.DISABLE);
    }
    @POST @Path("/rules/{id}/retire")
    public Response retire(@PathParam("id")String id,@HeaderParam("If-Match")String match,@HeaderParam("Idempotency-Key")String requestId,String body) {
        return transition(id,match,requestId,body,RuleAdministration.Change.RETIRE);
    }
    private Response transition(String id,String match,String requestId,String body,RuleAdministration.Change change) {
        var actor=identity.actor();var parsed=body(body,Set.of("version","reason"));
        if(!parsed.path("version").isIntegralNumber()||!parsed.path("version").canConvertToInt())throw new ApiFailure(400,"VERSION_REQUIRED");
        var receipt=service.mutate(new RuleAdministration.Mutation(actor,requestId,revision(match),id,parsed.path("version").intValue(),change,null,text(parsed,"reason")));
        return Response.ok(receipt).tag(Long.toString(receipt.revision())).build();
    }
    @POST @Path("/simulations")
    public Object simulate(String body) {
        var actor=identity.actor();var parsed=body(body,Set.of("event","candidateRule","candidateRules","evaluatedAt"));required(parsed,"event");
        var event=gateway.decode(parsed.get("event").toString());
        List<String> candidates=null;
        if(parsed.has("candidateRules")) {
            if(parsed.has("candidateRule") || !parsed.path("candidateRules").isArray() || parsed.path("candidateRules").isEmpty() || parsed.path("candidateRules").size()>256)
                throw new ApiFailure(400,"INVALID_CANDIDATE_SET");
            candidates=new ArrayList<>();for(var candidate:parsed.path("candidateRules"))candidates.add(candidate.toString());
        }else if(parsed.has("candidateRule"))candidates=List.of(parsed.get("candidateRule").toString());
        var result=service.simulateCandidates(actor,event,candidates,identity.requestId(),
                parsed.has("evaluatedAt")?java.time.Instant.parse(text(parsed,"evaluatedAt")):event.receivedAt());
        return Map.of("processingId",result.processingId(),"mode",result.mode(),"directive",result.directive(),
                "configurationSource",candidates!=null?"CANDIDATE":"ACTIVE",
                "snapshotChecksum",result.ruleSnapshot().checksum(),"stages",result.stages(),"candidates",result.candidates(),"enrichment",result.enrichment());
    }
    @GET @Path("/explain/{processingId}")
    public JsonNode explain(@PathParam("processingId")String id)throws Exception {
        return mapper.readTree(service.explain(identity.actor(),id,identity.requestId()));
    }
    private JsonNode body(String body,Set<String> allowed) {
        if(body==null||body.length()>262144)throw new ApiFailure(413,"REQUEST_SIZE_LIMIT");
        try {
            var parsed=mapper.readTree(body);
            if(parsed==null||!parsed.isObject())throw new ApiFailure(400,"OBJECT_REQUIRED");
            parsed.fieldNames().forEachRemaining(key->{if(!allowed.contains(key))throw new ApiFailure(400,"UNKNOWN_REQUEST_FIELD");});return parsed;
        }catch(ApiFailure e){throw e;}catch(Exception e){throw new ApiFailure(400,"INVALID_JSON");}
    }
    private static void required(JsonNode node,String field){if(!node.path(field).isObject())throw new ApiFailure(400,"OBJECT_REQUIRED");}
    private static String text(JsonNode node,String field){if(!node.path(field).isTextual())throw new ApiFailure(400,"TEXT_REQUIRED");return node.path(field).textValue();}
    private static long revision(String value) {
        if(value==null)throw new ApiFailure(428,"IF_MATCH_REQUIRED");
        if(!value.matches("\\\"[0-9]{1,18}\\\""))throw new ApiFailure(400,"INVALID_IF_MATCH");
        return Long.parseLong(value.substring(1,value.length()-1));
    }
}
