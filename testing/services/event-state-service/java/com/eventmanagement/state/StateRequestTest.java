package com.eventmanagement.state;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class StateRequestTest {
    static ObjectNode fixture() throws Exception {
        return (ObjectNode)new ObjectMapper().readTree(StateRequestTest.class.getResourceAsStream("/health/state-request.json"));
    }
    @Test void acceptsExplicitContractAndNormalizesTimestampPrecision() throws Exception {
        var request=fixture().put("occurredAt","2026-09-10T00:00:00.123456789Z");
        assertEquals(123456000,StateRequest.parse(request).occurredAt().getNano());
        assertEquals("OPEN",StateRequest.parse(request).transition());
    }
    @Test void rejectsMissingIdentityUnknownVersionAndUnsupportedTransitions() throws Exception {
        for (String field:new String[]{"messageId","eventId","eventKey","tenantId","correlationId","causationId"}) {
            var request=fixture();request.remove(field);
            assertThrows(RejectedIntegrationResult.class,()->StateRequest.parse(request));
        }
        assertThrows(RejectedIntegrationResult.class,()->StateRequest.parse(fixture().put("schemaVersion","2.0")));
        assertThrows(RejectedIntegrationResult.class,()->StateRequest.parse(fixture().put("transition","EXPIRE")));
        assertThrows(RejectedIntegrationResult.class,()->StateRequest.parse(fixture().put("eventKey","UNKNOWN")));
    }
    @Test void closeRequiresZeroEffectiveSeverityAndValidTimestamp() throws Exception {
        assertThrows(RejectedIntegrationResult.class,()->StateRequest.parse(fixture().put("transition","CLOSE")));
        assertEquals(0,StateRequest.parse(fixture().put("transition","CLOSE").put("effectiveSeverity",0)).effectiveSeverity());
        assertThrows(RejectedIntegrationResult.class,()->StateRequest.parse(fixture().put("sourceSeverity",6)));
        assertThrows(RejectedIntegrationResult.class,()->StateRequest.parse(fixture().put("occurredAt","yesterday")));
    }
}
