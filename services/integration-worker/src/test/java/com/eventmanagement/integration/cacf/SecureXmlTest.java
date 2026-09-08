package com.eventmanagement.integration.cacf;

import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class SecureXmlTest {
    private static byte[] bytes(String value) {
        return value.getBytes(StandardCharsets.UTF_8);
    }

    @Test
    void preservesNamespacesAndEscapedText() {
        var document = SecureXml.parse(bytes("<n:ServiceIncident xmlns:n='urn:test'><n:value>A &amp; B</n:value></n:ServiceIncident>"), 1024);
        assertEquals("urn:test", document.getDocumentElement().getNamespaceURI());
        assertEquals("A & B", document.getDocumentElement().getTextContent());
    }

    @Test
    void rejectsDoctypeAndExternalEntitiesWithoutExposingResourceNames() {
        for (String declaration : new String[] {
                "<!DOCTYPE root SYSTEM 'http://127.0.0.1:1/private'>",
                "<!DOCTYPE root [<!ENTITY secret SYSTEM 'file:///private/secret'>]>",
                "<!DOCTYPE root [<!ENTITY a 'expansion'>]>"
        }) {
            var error = assertThrows(IllegalArgumentException.class,
                    () -> SecureXml.parse(bytes(declaration + "<root/>"), 1024));
            assertEquals("Invalid or unsafe XML payload", error.getMessage());
            assertNull(error.getCause());
        }
    }

    @Test
    void rejectsXIncludeInsteadOfLeavingItForDownstreamProcessing() {
        assertThrows(IllegalArgumentException.class, () -> SecureXml.parse(bytes(
                "<root xmlns:xi='http://www.w3.org/2001/XInclude'><xi:include href='file:///private/secret'/></root>"), 1024));
    }

    @Test
    void enforcesByteLimitIncludingMultibyteCharacters() {
        byte[] xml = bytes("<r>á</r>");
        assertNotNull(SecureXml.parse(xml, xml.length));
        assertThrows(IllegalArgumentException.class, () -> SecureXml.parse(xml, xml.length - 1));
    }

    @Test
    void rejectsMalformedEmptyAndMissingPayloads() {
        assertThrows(IllegalArgumentException.class, () -> SecureXml.parse(bytes("<root>"), 1024));
        assertThrows(IllegalArgumentException.class, () -> SecureXml.parse(new byte[0], 1024));
        assertThrows(IllegalArgumentException.class, () -> SecureXml.parse(null, 1024));
        assertThrows(IllegalArgumentException.class, () -> SecureXml.parse(bytes("<r/>"), 0));
    }

    @Test void rejectsExcessiveNesting() {
        assertThrows(IllegalArgumentException.class, () -> SecureXml.parse(bytes("<r>".repeat(100) + "</r>".repeat(100)), 10000));
    }
}
