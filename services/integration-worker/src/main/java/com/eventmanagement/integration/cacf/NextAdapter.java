package com.eventmanagement.integration.cacf;

import com.fasterxml.jackson.databind.JsonNode;
import java.io.StringWriter;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

/** NEXT's legacy ServiceIncident vocabulary is confined to this adapter. */
public final class NextAdapter {
    public static final String NS = "http://b2b.ibm.com/schema/IS_B2B_CDM/R2_2";
    private NextAdapter() { }

    public static String requesterId(JsonNode request) {
        return request.path("event").path("sourceSystem").asText().trim() + ":"
                + request.path("event").path("sourceSerial").asText().trim() + ":"
                + request.path("customer").path("code").asText().trim();
    }

    public static String create(JsonNode request, String transaction, Instant now) throws Exception {
        Document d = document();
        Element root = d.getDocumentElement();
        JsonNode event = request.path("event");
        add(root, "RequesterID", requesterId(request));
        add(root, "RequesterSeverity", event.path("severity").asText());
        add(root, "TradingPartnerID", request.path("customer").path("code").asText());
        transaction(root, "CREATE", transaction, now);
        add(root.getElementsByTagNameNS(NS, "Transaction").item(0) instanceof Element t ? t : root,
                "TransactionRouting", "ASYNC::" + request.path("customer").path("code").asText() + "INC");
        Element incident = add(root, "Incident", null);
        add(incident, "Abstract", event.path("summary").asText());
        String[] labels = {"Summary","Date","Severity","ResourceId","CustomerCode","InstanceId","InstanceSituation","AlertKey","TicketGroup","InstanceValue","ComponentType","Component","SubComponent","ApplId","Node","NodeAlias","AlertGroup","EventType","MonitoringSolution","EventKey"};
        String[] fields = {"summary","occurredAt","severity","resourceId","","instanceId","instanceSituation","alertKey","","instanceValue","componentType","component","subComponent","applicationId","node","nodeAlias","alertGroup","eventType","monitoringSolution","eventKey"};
        StringBuilder description = new StringBuilder();
        for (int i = 0; i < labels.length; i++) {
            String value = i == 4 ? request.path("customer").path("code").asText()
                    : i == 8 ? request.path("ticket").path("holdingAssignmentGroup").asText()
                    : event.path(fields[i]).asText("");
            description.append(labels[i]).append(": ").append(value).append('\n');
        }
        add(incident, "Description", description.toString());
        Element flex = add(incident, "FlexFields", null);
        Map<String, String> mappings = new LinkedHashMap<>();
        mappings.put("originating_event_id", "sourceSerial"); mappings.put("event_class", "eventClass");
        mappings.put("service_state", "serviceState"); mappings.put("host_state", "hostState");
        mappings.put("ITEM", "item"); mappings.put("tec_id", "sourceSystem"); mappings.put("severity", "severity");
        mappings.put("ipcenabled", "automationEnabled"); mappings.put("fqdn", "node"); mappings.put("alertkey", "alertKey");
        mappings.put("alertgroup", "alertGroup"); mappings.put("ipaddress", "nodeAlias"); mappings.put("component", "component");
        mappings.put("componenttype", "componentType"); mappings.put("subcomponent", "subComponent");
        mappings.put("instanceid", "instanceId"); mappings.put("instancevalue", "instanceValue");
        mappings.put("instancesituation", "instanceSituation"); mappings.put("monitoring", "monitoringSolution");
        mappings.put("location", "location"); mappings.put("lastoccurrence", "occurredAt");
        mappings.forEach((wire, field) -> {
            Element f = add(flex, "FlexField", event.path(field).asText(""));
            f.setAttribute("mappedTo", wire); f.setAttribute("id", "0");
        });
        add(add(root, "AssignedTo", null), "AssignedGroup", request.path("ticket").path("holdingAssignmentGroup").asText());
        Element asset = add(root, "Asset", null);
        add(asset, "AssetID", event.path("resourceId").asText()); add(asset, "Asset_Tag", event.path("node").asText());
        return serialize(d);
    }

    public static String ticketUpdate(JsonNode request, String providerId, String ticket, String transaction, Instant now) throws Exception {
        Document d = document(); Element root = d.getDocumentElement();
        add(root, "RequesterID", requesterId(request)); add(root, "ProviderID", providerId);
        add(root, "TradingPartnerID", request.path("customer").path("code").asText());
        transaction(root, "TKTUPDATE", transaction, now);
        Element f = add(add(add(root, "Incident", null), "FlexFields", null), "FlexField", ticket);
        f.setAttribute("mappedTo", "ipc");
        return serialize(d);
    }

    public record Message(String requesterId, String providerId, String transactionNumber,
            String transactionName, String status, String substatus, String estimatedWait, byte[] raw) {
        public boolean acknowledgement() { return "Acknowledge_Create".equalsIgnoreCase(transactionName); }
        public String outcome() {
            String value = status.isBlank() ? transactionName : status;
            if ("NO MATCHING HOST".equalsIgnoreCase(substatus)) return "NO_MATCHING_HOST";
            return switch (value.trim().toUpperCase(java.util.Locale.ROOT)) {
                case "RESOLVE", "REMEDIATION" -> "REMEDIATED";
                case "ESCALATE" -> "ESCALATED";
                case "TRANSFER", "TOWER TRANSFER" -> "TRANSFER";
                case "NO ACTION" -> "NO_ACTION";
                case "NO MATCHING HOST" -> "NO_MATCHING_HOST";
                case "REPEATED INCIDENT", "AUTO ESCALATION" -> "REPEATED_INCIDENT";
                default -> "UNKNOWN";
            };
        }
    }

    public static Message parse(byte[] xml, int maxBytes) {
        Document d = SecureXml.parse(xml, maxBytes);
        if (!"ServiceIncident".equals(d.getDocumentElement().getLocalName()) || !NS.equals(d.getDocumentElement().getNamespaceURI()))
            throw new IllegalArgumentException("Invalid ServiceIncident root or namespace");
        String name = field(d, "TransactionName");
        if (name.isBlank()) throw new IllegalArgumentException("Missing TransactionName");
        return new Message(field(d,"RequesterID"), field(d,"ProviderID"), field(d,"TransactionNumber"), name,
                field(d,"WorkflowStatus"), field(d,"WorkflowSubStatus"), flex(d,"estimated_wait_time"), xml.clone());
    }

    private static String field(Document d, String name) {
        var nodes = d.getElementsByTagNameNS(NS, name);
        if (nodes.getLength() > 1) throw new IllegalArgumentException("Ambiguous XML field: " + name);
        return nodes.getLength() == 0 ? "" : nodes.item(0).getTextContent().trim();
    }
    private static String flex(Document d, String name) {
        var nodes = d.getElementsByTagNameNS(NS, "FlexField");
        String value = "";
        for (int i=0;i<nodes.getLength();i++) if (name.equals(((Element)nodes.item(i)).getAttribute("mappedTo"))) value=nodes.item(i).getTextContent().trim();
        return value;
    }
    private static Document document() throws Exception {
        var factory=DocumentBuilderFactory.newInstance(); factory.setNamespaceAware(true);
        Document d=factory.newDocumentBuilder().newDocument(); Element root=d.createElementNS(NS,"ServiceIncident"); d.appendChild(root);
        root.setAttributeNS(XMLConstants.XMLNS_ATTRIBUTE_NS_URI,"xmlns",NS);
        root.setAttributeNS(XMLConstants.XMLNS_ATTRIBUTE_NS_URI,"xmlns:xsi",XMLConstants.W3C_XML_SCHEMA_INSTANCE_NS_URI);
        root.setAttributeNS(XMLConstants.W3C_XML_SCHEMA_INSTANCE_NS_URI,"xsi:schemaLocation",NS+" IS_B2B_CDM_R2_2.xsd");
        return d;
    }
    private static void transaction(Element root, String operation, String id, Instant now) {
        Element t=add(root,"Transaction",null); add(t,"TransactionName",operation); add(t,"TransactionType","2");
        add(t,"TransactionDateTime",now.toString()); add(t,"TransactionNumber",id);
    }
    private static Element add(Element parent, String name, String value) {
        Element child=parent.getOwnerDocument().createElementNS(NS,name); if(value!=null)child.setTextContent(value);parent.appendChild(child);return child;
    }
    private static String serializeSafe(Document d) {
        try {return serialize(d);} catch(Exception e){throw new IllegalArgumentException("Invalid XML document");}
    }
    private static String serialize(Document d) throws Exception {
        var factory=TransformerFactory.newInstance();factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING,true);
        factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD,"");factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_STYLESHEET,"");
        var out=new StringWriter();factory.newTransformer().transform(new DOMSource(d),new StreamResult(out));return out.toString();
    }
}
