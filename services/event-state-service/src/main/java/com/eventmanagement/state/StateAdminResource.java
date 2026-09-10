package com.eventmanagement.state;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import io.smallrye.common.annotation.Blocking;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.*;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;
import javax.sql.DataSource;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;

@jakarta.ws.rs.Path("/api/v1/state")
@Produces(MediaType.APPLICATION_JSON)
@Blocking
@ApplicationScoped
public class StateAdminResource {
    private static final Logger LOG=Logger.getLogger(StateAdminResource.class);
    private final DataSource db;
    private final ObjectMapper json;
    private final JsonNode credentials;
    private static final String FIELDS="event_key,event_id,tenant,lifecycle_status,ticket_number,notification_id,automation_id,servicenow_status,gnm_status,cacf_status,source_severity,effective_severity,tally,version,first_seen_at,last_updated_at,last_state_at";
    @Inject public StateAdminResource(DataSource db,ObjectMapper json,
        @ConfigProperty(name="ess.admin.credentials-file") Optional<String> file) throws Exception {
        this.db=db;this.json=json;
        credentials=file.filter(s->!s.isBlank()).isPresent()?json.readTree(Files.readString(Path.of(file.get()))):json.createObjectNode();
    }
    private void authorize(String token,String tenant,boolean operator) {
        String expected=operator?credentials.path("operatorToken").asText():credentials.path("tenants").path(tenant==null?"":tenant).asText();
        if(credentials.isEmpty())throw error(503,"ADMIN_NOT_CONFIGURED");
        if(expected.isBlank() || token==null || !MessageDigest.isEqual(expected.getBytes(StandardCharsets.UTF_8),token.getBytes(StandardCharsets.UTF_8)))
            throw error(401,"UNAUTHORIZED");
    }
    private static WebApplicationException error(int status,String code) {
        return new WebApplicationException(Response.status(status).entity(Map.of("errorCode",code)).header("Cache-Control","no-store").build());
    }
    private static void limit(int value) {if(value<1||value>100)throw error(400,"INVALID_LIMIT");}
    private static void key(String key) {if(key==null||key.isBlank()||key.length()>128)throw error(400,"INVALID_EVENT_KEY");}
    private ArrayNode query(String sql,Object... values) {
        try(var c=db.getConnection();var s=c.prepareStatement(sql)) {
            s.setQueryTimeout(5);
            for(int i=0;i<values.length;i++)s.setObject(i+1,values[i]);
            var rows=json.createArrayNode();try(var r=s.executeQuery()){while(r.next())rows.add(json.readTree(r.getString(1)));}return rows;
        } catch(Exception e){throw error(503,"STATE_STORE_UNAVAILABLE");}
    }
    private Response result(Object value,String operation) {
        LOG.infof("ESS admin read operation=%s",operation);
        return Response.ok(value).header("Cache-Control","no-store").build();
    }
    @GET @jakarta.ws.rs.Path("/events")
    public Response events(@HeaderParam("X-ESS-Admin-Token")String token,@HeaderParam("X-Tenant-Id")String tenant,
        @QueryParam("limit")@DefaultValue("50")int count,@QueryParam("after")@DefaultValue("")String after) {
        authorize(token,tenant,false);limit(count);if(after.length()>128)throw error(400,"INVALID_CURSOR");
        var rows=query("SELECT row_to_json(s)::text FROM (SELECT "+FIELDS+" FROM event_management.event_state WHERE tenant=? AND event_key>? ORDER BY event_key LIMIT ?) s",tenant,after,count+1);
        boolean more=rows.size()>count;if(more)rows.remove(count);
        return result(Map.of("items",rows,"nextCursor",more?rows.get(rows.size()-1).path("event_key").asText():""),"events");
    }
    @GET @jakarta.ws.rs.Path("/event")
    public Response event(@HeaderParam("X-ESS-Admin-Token")String token,@HeaderParam("X-Tenant-Id")String tenant,@QueryParam("eventKey")String key) {
        authorize(token,tenant,false);key(key);
        var rows=query("SELECT row_to_json(s)::text FROM (SELECT "+FIELDS+" FROM event_management.event_state WHERE tenant=? AND event_key=?) s",tenant,key);
        if(rows.isEmpty())throw error(404,"EVENT_NOT_FOUND");return result(rows.get(0),"event");
    }
    @GET @jakarta.ws.rs.Path("/history")
    public Response history(@HeaderParam("X-ESS-Admin-Token")String token,@HeaderParam("X-Tenant-Id")String tenant,
        @QueryParam("eventKey")String key,@QueryParam("afterVersion")@DefaultValue("0")long version,@QueryParam("limit")@DefaultValue("50")int count) {
        authorize(token,tenant,false);key(key);limit(count);if(version<0)throw error(400,"INVALID_CURSOR");
        var rows=query("SELECT row_to_json(s)::text FROM (SELECT message_id,event_key,from_status,to_status,transition_type,aggregate_version,occurred_at,recorded_at FROM event_management.ess_event_transition WHERE tenant=? AND event_key=? AND aggregate_version>? ORDER BY aggregate_version LIMIT ?) s",tenant,key,version,count+1);
        boolean more=rows.size()>count;if(more)rows.remove(count);
        return result(Map.of("items",rows,"nextVersion",more?rows.get(rows.size()-1).path("aggregate_version").asLong():0),"history");
    }
    @GET @jakarta.ws.rs.Path("/quarantine")
    public Response quarantine(@HeaderParam("X-ESS-Admin-Token")String token) {
        authorize(token,null,true);
        return result(Map.of("scope","operator-global","items",query("SELECT row_to_json(s)::text FROM (SELECT reason,count(*) AS count,max(quarantined_at) AS last_seen_at FROM event_management.ess_quarantine GROUP BY reason ORDER BY reason LIMIT 100) s")),"quarantine-summary");
    }
}
