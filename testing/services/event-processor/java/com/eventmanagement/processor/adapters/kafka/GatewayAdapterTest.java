package com.eventmanagement.processor.adapters.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.eventmanagement.processor.domain.Event;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class GatewayAdapterTest {
    static final String OPEN="""
        {"schemaVersion":"1.1","eventId":"id","eventKey":"key","tenant":{"code":"SDC"},
         "lifecycleAction":"OPEN","sourceSeverity":5,"effectiveSeverity":5,
         "timestamps":{"receivedAt":"2026-09-09T00:00:00Z"}}
        """;
    private final GatewayEventAdapter adapter=new GatewayEventAdapter(new ObjectMapper());
    @Test void mapsOpenWithoutChangingOriginal() {
        var event=adapter.decode(OPEN);assertEquals(Event.Status.PROBLEM,event.status());
        assertEquals(5,event.severity());assertEquals(OPEN,event.originalJson());assertEquals("SDC",event.tenant());
    }
    @Test void mapsCloseToOkNotResolvedAndKeepsSourceSeverity() {
        var input=OPEN.replace("OPEN","CLOSE").replace("\"effectiveSeverity\":5","\"effectiveSeverity\":0");
        var event=adapter.decode(input);assertEquals(Event.Status.OK,event.status());assertEquals(0,event.severity());
        assertTrue(event.originalJson().contains("\"sourceSeverity\":5"));
    }
    @Test void legacyMissingTenantNeverFabricatesOne() {
        String input="""
          {"schemaVersion":"1.0","eventId":"id","alert":{"status":"RESOLVED","severity":0},
           "timestamps":{"receivedAt":"2026-09-09T00:00:00Z"},"originalEvent":{}}
          """;
        var event=adapter.decode(input);assertEquals("",event.tenant());
        assertEquals("id",event.eventKey());assertEquals(Event.Status.RESOLVED,event.status());
    }
    @Test void rejectsInvalidOrUnsupportedContractsWithoutLeakingPayload() {
        for(String body:new String[]{"not-json-secret", "[]", "{}", OPEN.replace("1.1","7.0"),
                OPEN.replace("\"effectiveSeverity\":5","\"effectiveSeverity\":8"),
                OPEN.replace("\"effectiveSeverity\":5","\"effectiveSeverity\":\"5\""),
                OPEN.replace("OPEN","UNKNOWN")}) {
            var error=assertThrows(IllegalArgumentException.class,()->adapter.decode(body));
            assertEquals("INVALID_GATEWAY_CONTRACT",error.getMessage());assertNull(error.getCause());
        }
    }
}
