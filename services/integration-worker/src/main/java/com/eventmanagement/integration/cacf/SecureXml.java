package com.eventmanagement.integration.cacf;

import java.io.ByteArrayInputStream;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import org.w3c.dom.Document;
import org.xml.sax.SAXException;
import org.xml.sax.SAXParseException;
import org.xml.sax.helpers.DefaultHandler;

/** Parses provider evidence without permitting external resources or XML inclusion. */
public final class SecureXml {
    private SecureXml() {
    }

    public static Document parse(byte[] xml, int maxBytes) {
        if (maxBytes <= 0) {
            throw new IllegalArgumentException("XML size limit must be positive");
        }
        if (xml == null || xml.length == 0 || xml.length > maxBytes) {
            throw new IllegalArgumentException("XML payload is empty or exceeds the size limit");
        }
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(true);
            factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
            factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
            factory.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
            factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
            factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
            factory.setAttribute("http://www.oracle.com/xml/jaxp/properties/maxElementDepth", "64");
            factory.setXIncludeAware(false);
            factory.setExpandEntityReferences(false);
            var builder = factory.newDocumentBuilder();
            builder.setEntityResolver((publicId, systemId) -> {
                throw new SAXException("External XML resources are forbidden");
            });
            builder.setErrorHandler(new DefaultHandler() {
                @Override
                public void error(SAXParseException exception) throws SAXException {
                    throw exception;
                }

                @Override
                public void fatalError(SAXParseException exception) throws SAXException {
                    throw exception;
                }
            });
            Document document = builder.parse(new ByteArrayInputStream(xml));
            if (document.getElementsByTagNameNS("http://www.w3.org/2001/XInclude", "*").getLength() > 0) {
                throw new SAXException("XML inclusion is forbidden");
            }
            return document;
        } catch (Exception exception) {
            // Parser messages may contain provider content or external resource names.
            // Do not attach the cause to an exception that could reach logs or HTTP responses.
            throw new IllegalArgumentException("Invalid or unsafe XML payload");
        }
    }
}
