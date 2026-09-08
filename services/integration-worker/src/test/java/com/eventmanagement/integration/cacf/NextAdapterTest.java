package com.eventmanagement.integration.cacf;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class NextAdapterTest {
    @Test void createsNamespacedContractWithStandardEscapingAndSeparateTicketMapping() throws Exception {
        var request=new ObjectMapper().readTree(getClass().getResourceAsStream("/cacf/request.json"));
        var canonical=AutomationRequest.parse(request,null,null,600);
        String xml=NextAdapter.create(canonical.payload(),"transaction-1",Instant.EPOCH);
        var d=SecureXml.parse(xml.getBytes(StandardCharsets.UTF_8),100000);
        assertEquals("LOCAL:1:local",d.getElementsByTagNameNS(NextAdapter.NS,"RequesterID").item(0).getTextContent());
        assertEquals(request.path("event").path("summary").asText(),d.getElementsByTagNameNS(NextAdapter.NS,"Abstract").item(0).getTextContent());
        assertEquals("LOCAL-AUTOMATION",d.getElementsByTagNameNS(NextAdapter.NS,"AssignedGroup").item(0).getTextContent());
        assertFalse(xml.contains("mappedTo=\"ipc\""));
        String update=NextAdapter.ticketUpdate(request,"provider-1","INCLOCAL001","transaction-2",Instant.EPOCH);
        assertTrue(update.contains("mappedTo=\"ipc\""));assertTrue(update.contains("INCLOCAL001"));
    }

    @Test void normalizesKnownOutcomesAndPreservesUnknownEvidence() {
        String[][] values={{"RESOLVE","REMEDIATED"},{"REMEDIATION","REMEDIATED"},{"ESCALATE","ESCALATED"},{"TOWER TRANSFER","TRANSFER"},{"NO ACTION","NO_ACTION"},{"NO MATCHING HOST","NO_MATCHING_HOST"},{"new-provider-value","UNKNOWN"}};
        for(String[] pair:values) {
            byte[] xml=callback("result",pair[0]);var message=NextAdapter.parse(xml,10000);
            assertEquals(pair[1],message.outcome());assertArrayEquals(xml,message.raw());
        }
    }
    @Test void rejectsForeignNamespaceAndAmbiguousCorrelationFields() {
        assertThrows(IllegalArgumentException.class,()->NextAdapter.parse("<ServiceIncident><TransactionName>X</TransactionName></ServiceIncident>".getBytes(StandardCharsets.UTF_8),10000));
        String xml=new String(callback("result","RESOLVE"),StandardCharsets.UTF_8).replace("</RequesterID>","</RequesterID><RequesterID>other</RequesterID>");
        assertThrows(IllegalArgumentException.class,()->NextAdapter.parse(xml.getBytes(StandardCharsets.UTF_8),10000));
    }
    static byte[] callback(String transaction,String status) {
        return ("<ServiceIncident xmlns='"+NextAdapter.NS+"'><RequesterID>LOCAL:1:local</RequesterID><ProviderID>provider-1</ProviderID><Transaction><TransactionName>"+transaction+"</TransactionName></Transaction><WorkflowStatus>"+status+"</WorkflowStatus></ServiceIncident>").getBytes(StandardCharsets.UTF_8);
    }
}
