package com.eventmanagement.processor.adapters.kafka;

import com.eventmanagement.processor.domain.Event;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class StateRequestAdapterTest {
    final ObjectMapper mapper=new ObjectMapper();
    @Test void encodesStableIdentityDecisionsAndOriginalSeverity() throws Exception {
        var adapter=new StateRequestAdapter(mapper);
        var event=new Event("id","key","tenant",Event.Status.OK,0,Instant.parse("2026-09-10T00:00:00.123456789Z"),"{\"sourceSeverity\":3}");
        var evidence=mapper.createObjectNode().put("directive","CONTINUE");
        var result=adapter.encode(event,"processing",evidence);
        assertEquals("CLOSE",result.path("transition").asText());assertEquals(0,result.path("effectiveSeverity").asInt());
        assertEquals(3,result.path("sourceSeverity").asInt());assertEquals("2026-09-10T00:00:00.123456Z",result.path("occurredAt").asText());
        assertEquals(result,adapter.encode(event,"processing",evidence));
        assertEquals("tenant",result.path("tenantId").asText());assertEquals("CONTINUE",result.path("decisions").path("directive").asText());
    }
}
