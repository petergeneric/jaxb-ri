/*
 * Copyright (c) 2026 Contributors to the Eclipse Foundation. All rights reserved.
 * Copyright (c) 1997, 2023 Oracle and/or its affiliates. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0, which is available at
 * http://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */

package org.glassfish.jaxb.runtime.v2.runtime;

import java.io.StringReader;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.List;

import javax.xml.XMLConstants;
import javax.xml.namespace.QName;
import javax.xml.parsers.DocumentBuilderFactory;

import jakarta.xml.bind.JAXBContext;
import jakarta.xml.bind.Marshaller;
import jakarta.xml.bind.annotation.XmlAccessType;
import jakarta.xml.bind.annotation.XmlAccessorType;
import jakarta.xml.bind.annotation.XmlAnyElement;
import jakarta.xml.bind.annotation.XmlElement;
import jakarta.xml.bind.annotation.XmlRootElement;
import jakarta.xml.bind.annotation.XmlType;
import org.glassfish.jaxb.runtime.marshaller.NamespacePrefixMapper;
import org.junit.jupiter.api.Test;
import org.w3c.dom.Element;
import org.xml.sax.InputSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * JAXB-only variant of Metro's fromjava.nosei_apt.client.EchoClient.testExceptions().
 * Regression test for the DOM prefix collision fix from <a href="https://github.com/javaee/jaxb-v2/commit/52203942940186afc49177a82912e4ade54e9b88">jaxb-v2 52203942940186afc49177a82912e4ade54e9b88</a>.
 * Test types adapted from <a href="https://github.com/eclipse-ee4j/metro-jax-ws/tree/e111a264eae5da1eebdf21c5ec386a936e1e2bcb/jaxws-ri/runtime/rt/src/main/java/com/sun/xml/ws/fault">Metro's SOAP11Fault and DetailType</a>.
 */
public class SoapFaultDomNamespaceTest {

    private static final String SOAP_NAMESPACE = "http://schemas.xmlsoap.org/soap/envelope/";
    private static final String FAULT_NAMESPACE = "urn:test:fault";
    private static final String TYPE_NAMESPACE = "urn:test:types";

    @Test
    public void shouldRoundTripSoapFaultWithConflictingDomPrefix() throws Exception {
        // The fault and its DOM detail deliberately bind ns2 to different URIs.
        // ns3 is also declared locally: reallocating the detail's prefix to ns3
        // before forcing its declarations can overwrite that generated binding.
        Element exception = parse("<ns2:Exception1 xmlns:ns2=\"" + FAULT_NAMESPACE
                + "\" xmlns:ns3=\"" + TYPE_NAMESPACE
                + "\" xmlns:xsi=\"" + XMLConstants.W3C_XML_SCHEMA_INSTANCE_NS_URI
                + "\" xsi:type=\"ns3:ExceptionType\">"
                + "<message>my exception1 Message</message><valid>true</valid>"
                + "</ns2:Exception1>");
        SoapFault fault = new SoapFault();
        fault.faultcode = new QName(SOAP_NAMESPACE, "Server", "ns2");
        fault.faultstring = "my exception1";
        fault.detail = new Detail();
        fault.detail.entries.add(exception);

        JAXBContext context = JAXBContext.newInstance(SoapFault.class);
        Marshaller marshaller = context.createMarshaller();
        marshaller.setProperty("org.glassfish.jaxb.namespacePrefixMapper", new NamespacePrefixMapper() {
            @Override
            public String getPreferredPrefix(String namespaceUri, String suggestion, boolean requirePrefix) {
                return SOAP_NAMESPACE.equals(namespaceUri) ? "ns2" : suggestion;
            }
        });
        StringWriter output = new StringWriter();
        marshaller.marshal(fault, output);

        // Parsing also rejects duplicate namespace declarations / malformed XML.
        Element faultXml = parse(output.toString());
        assertEquals("ns2:Fault", faultXml.getNodeName());
        assertEquals(SOAP_NAMESPACE, faultXml.lookupNamespaceURI("ns2"));
        Element exceptionXml = (Element) faultXml
                .getElementsByTagNameNS("*", "Exception1").item(0);
        assertNotNull(exceptionXml);
        assertEquals(FAULT_NAMESPACE, exceptionXml.getNamespaceURI());
        assertEquals("ns2:Exception1", exceptionXml.getNodeName());
        assertEquals(FAULT_NAMESPACE, exceptionXml.lookupNamespaceURI("ns2"));
        assertEquals("ns3:ExceptionType", exceptionXml
                .getAttributeNS(XMLConstants.W3C_XML_SCHEMA_INSTANCE_NS_URI, "type"));
        assertEquals(TYPE_NAMESPACE, exceptionXml.lookupNamespaceURI("ns3"));

        SoapFault restored = (SoapFault) context.createUnmarshaller()
                .unmarshal(new StringReader(output.toString()));
        assertEquals(fault.faultcode, restored.faultcode);
        assertEquals("my exception1", restored.faultstring);
        assertEquals(1, restored.detail.entries.size());
        Element restoredException = restored.detail.entries.get(0);
        assertEquals(FAULT_NAMESPACE, restoredException.getNamespaceURI());
        assertEquals("my exception1 Message", restoredException
                .getElementsByTagNameNS("", "message").item(0).getTextContent());
        assertEquals("true", restoredException
                .getElementsByTagNameNS("", "valid").item(0).getTextContent());
    }

    private Element parse(String xml) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        return factory.newDocumentBuilder().parse(new InputSource(new StringReader(xml))).getDocumentElement();
    }


    @XmlAccessorType(XmlAccessType.FIELD)
    @XmlType(name = "", propOrder = {"faultcode", "faultstring", "detail"})
    @XmlRootElement(name = "Fault", namespace = SOAP_NAMESPACE)
    private static class SoapFault {
        @XmlElement(namespace = "")
        private QName faultcode;

        @XmlElement(namespace = "")
        private String faultstring;

        @XmlElement(namespace = "")
        private Detail detail;
    }

    private static class Detail {
        @XmlAnyElement
        private List<Element> entries = new ArrayList<>();
    }
}
