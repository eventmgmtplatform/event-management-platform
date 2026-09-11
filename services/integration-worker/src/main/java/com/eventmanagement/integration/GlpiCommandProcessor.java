package com.eventmanagement.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.*;
import org.apache.camel.*;

@Named("glpiCommandProcessor")
@ApplicationScoped
public class GlpiCommandProcessor implements Processor {
    @Inject ObjectMapper mapper;
    @Override public void process(Exchange exchange) {
        JsonNode payload=exchange.getProperty("integrationPayload",JsonNode.class);
        String operation=exchange.getProperty("operation",String.class);
        if(!java.util.Set.of("CREATE_TICKET","RESOLVE_TICKET","CLOSE_TICKET","APPLY_AUTOMATION_RESULT").contains(operation))
            throw new IllegalArgumentException("Unsupported GLPI operation");
        ObjectNode input=mapper.createObjectNode();
        if("CREATE_TICKET".equals(operation)) {
            String name=payload.path("name").asText(payload.path("summary").asText());
            if(name.isBlank()) throw new IllegalArgumentException("GLPI name or summary required");
            // Native name marker supports read-only recovery after an uncertain POST.
            String commandId=exchange.getProperty("commandId",String.class);
            if(commandId==null || !commandId.matches("[A-Za-z0-9._:-]{1,128}"))throw new IllegalArgumentException("Invalid GLPI command identity");
            String marker="[OEM:"+commandId+"]";
            input.put("name",name.substring(0,Math.min(name.length(),Math.max(0,250-marker.length())))+" "+marker);
            input.put("content",payload.path("content").asText(payload.path("description").asText(name)));
            input.put("type",1); input.put("status",1);
            int severity=payload.path("severity").asInt(3);
            if(payload.has("severity") && (!payload.path("severity").isIntegralNumber() || severity<0 || severity>5))
                throw new IllegalArgumentException("Invalid GLPI source severity");
            input.put("urgency",Math.max(1,severity)); input.put("impact",Math.max(1,severity));
            for(String field:java.util.List.of("urgency","impact","priority","type","entities_id","itilcategories_id","_users_id_requester","_users_id_assign","_groups_id_assign")) {
                if(payload.has(field)) {
                    JsonNode value=payload.get(field);
                    int max=field.equals("type")?2:java.util.Set.of("urgency","impact").contains(field)?5:field.equals("priority")?6:Integer.MAX_VALUE;
                    int min=field.endsWith("_id")?0:1;
                    if(!value.isIntegralNumber() || !value.canConvertToInt() || value.asInt()<min || value.asInt()>max)
                        throw new IllegalArgumentException("Invalid GLPI "+field);
                    input.set(field,value);
                }
            }
        } else {
            long id=payload.path("ticketId").asLong(0);
            if(id<1) throw new IllegalArgumentException("GLPI ticketId required");
            exchange.setProperty("glpiTicketId",id);
            if("CLOSE_TICKET".equals(operation)) {
                input.put("id",id); input.put("status",6);
            } else {
                String content=payload.path("content").asText(payload.path("closeNotes").asText());
                if(content.isBlank()) throw new IllegalArgumentException("GLPI content required");
                input.put("itemtype","Ticket");input.put("items_id",id);input.put("content",content);
                if("APPLY_AUTOMATION_RESULT".equals(operation))input.put("is_private",1);
            }
        }
        exchange.setProperty("glpiRequest",mapper.createObjectNode().set("input",input));
    }
}
