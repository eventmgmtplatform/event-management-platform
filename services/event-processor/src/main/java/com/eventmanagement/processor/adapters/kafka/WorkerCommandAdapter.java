package com.eventmanagement.processor.adapters.kafka;

import com.eventmanagement.processor.domain.Event;
import com.eventmanagement.processor.domain.StableIdentity;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.time.Instant;
import java.util.*;

/** Output compatibility boundary. No provider execution. Used after typed routing and inside the durable command transaction.
 * Encode only the first intent; retries MUST reuse the persisted envelope, including timestamps
 * and processingId. The Worker rejects changed content with the same semantic command identity.
 */
public final class WorkerCommandAdapter {
    private static final Map<String,Set<String>> OPERATIONS=Map.of(
            "SERVICENOW",Set.of("CREATE_TICKET","RESOLVE_TICKET"),
            "GNM",Set.of("SEND_NOTIFICATION","CLOSE_NOTIFICATION"),
            "CACF",Set.of("AUTOMATION_REQUESTED"));
    private final ObjectMapper mapper;
    public WorkerCommandAdapter(ObjectMapper mapper) { this.mapper=mapper; }
    public ObjectNode encode(Event event,String processingId,String cycleId,String targetId,
                             String integration,String operation,JsonNode payload,Instant createdAt) {
        for(String value:Arrays.asList(event.tenant(),processingId,cycleId,targetId,integration,operation))
            if(value==null||value.isBlank())throw new IllegalArgumentException("COMMAND_IDENTITY_REQUIRED");
        if(!OPERATIONS.getOrDefault(integration,Set.of()).contains(operation))throw new IllegalArgumentException("UNSUPPORTED_WORKER_OPERATION");
        if(payload==null||!payload.isObject()||createdAt==null)throw new IllegalArgumentException("COMMAND_PAYLOAD_REQUIRED");
        // Worker ledger is keyed by commandId. Carry the semantic key there as well as in metadata.
        String key=StableIdentity.of("integration-intent-v1",event.tenant(),event.eventKey(),cycleId,targetId,integration,operation);
        ObjectNode command=mapper.createObjectNode();
        command.put("schemaVersion","1.0");command.put("commandId",key);
        command.put("eventId",event.eventId());command.put("eventKey",event.eventKey());command.put("tenant",event.tenant());
        command.put("processingId",processingId);command.put("createdAt",createdAt.toString());
        command.put("integrationType",integration);command.put("operation",operation);command.put("configuration",targetId);
        command.set("payload",payload.deepCopy());command.putObject("metadata").put("idempotencyKey",key);
        return command;
    }
}
