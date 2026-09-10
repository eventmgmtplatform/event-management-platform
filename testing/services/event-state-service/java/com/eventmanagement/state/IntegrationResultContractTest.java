package com.eventmanagement.state;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class IntegrationResultContractTest {
    static ObjectNode fixture() throws Exception {
        return (ObjectNode) new ObjectMapper().readTree(IntegrationResultContractTest.class
                .getResourceAsStream("/health/event-service.json")).path("result").deepCopy();
    }
    @Test void acceptsExistingWorkerContractAndOptionalExtensions() throws Exception {
        var result = fixture();
        result.putObject("metadata").put("future", true);
        IntegrationResultContract.validate(result);
        result.remove("externalId");
        IntegrationResultContract.validate(result);
    }
    @Test void rejectsMissingIdentityAndWrongJsonTypes() throws Exception {
        for (String field : new String[]{"resultId", "eventId", "eventKey", "tenant", "integrationType", "status"}) {
            var result = fixture(); result.remove(field);
            assertThrows(IllegalArgumentException.class, () -> IntegrationResultContract.validate(result), field);
            result.put(field, 12);
            assertThrows(IllegalArgumentException.class, () -> IntegrationResultContract.validate(result), field);
        }
    }
    @Test void rejectsPlaceholdersWhitespaceAndOversizedKeys() throws Exception {
        for (String key : new String[]{"UNKNOWN", "unknown", "N/A", "UNDEFINED", " ", " key", "k".repeat(129)}) {
            var result = fixture(); result.put("eventKey", key);
            assertThrows(IllegalArgumentException.class, () -> IntegrationResultContract.validate(result), key);
        }
    }
    @Test void rejectsUnsupportedProviderAndOversizedReference() throws Exception {
        var result = fixture(); result.put("integrationType", "OTHER");
        assertThrows(IllegalArgumentException.class, () -> IntegrationResultContract.validate(result));
        result.put("integrationType", "GNM"); result.put("externalId", "a".repeat(101));
        assertThrows(IllegalArgumentException.class, () -> IntegrationResultContract.validate(result));
    }
}
