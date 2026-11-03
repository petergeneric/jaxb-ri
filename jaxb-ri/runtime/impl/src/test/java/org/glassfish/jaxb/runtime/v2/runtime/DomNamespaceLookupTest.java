/*
 * Copyright (c) 2026 Contributors to the Eclipse Foundation. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0, which is available at
 * http://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */

package org.glassfish.jaxb.runtime.v2.runtime;

import java.io.ByteArrayOutputStream;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;

import javax.xml.parsers.DocumentBuilderFactory;

import jakarta.xml.bind.JAXBContext;
import jakarta.xml.bind.JAXBException;
import jakarta.xml.bind.Marshaller;
import jakarta.xml.bind.annotation.XmlAccessType;
import jakarta.xml.bind.annotation.XmlAccessorType;
import jakarta.xml.bind.annotation.XmlAnyElement;
import jakarta.xml.bind.annotation.XmlRootElement;
import jakarta.xml.bind.annotation.XmlType;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.xml.sax.InputSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

public class DomNamespaceLookupTest {

    private static final String NAMESPACE = "urn:foo";

    // Every DOM element is qualified, so this also exercises the original
    // adaptor without relying on the change to default namespace clearing.
    private static final String REBOUND_PREFIX_ELEMENT_SAMPLE = "<q:x xmlns:q=\"urn:bar\" xmlns:p=\"urn:foo\">"
            + "<r:y xmlns:r=\"urn:foo\"><r:z xmlns:r=\"urn:bar\" p:key=\"value\"/>"
            + "</r:y></q:x>";

    private static JAXBContext context;

    @BeforeAll
    public static void setUpBeforeClass() throws Exception {
        context = JAXBContext.newInstance(Root.class);
    }

    @Test
    public void shouldPreserveDomAttributeNamespaceWhenAncestorPrefixIsRebound() throws Exception {
        Root root = createRoot(REBOUND_PREFIX_ELEMENT_SAMPLE);

        String actual = serializeObject(root);
        Element child = (Element) parse(actual).getElementsByTagNameNS("urn:bar", "z").item(0);

        assertEquals("value", child.getAttributeNS(NAMESPACE, "key"));
        assertNull(child.getAttributeNodeNS("urn:bar", "key"));
    }

    private String serializeObject(Root root) throws JAXBException {
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        Marshaller marshaller = context.createMarshaller();
        marshaller.setProperty(Marshaller.JAXB_FRAGMENT, Boolean.TRUE);
        marshaller.marshal(root, outputStream);
        return outputStream.toString(StandardCharsets.UTF_8);
    }

    private Root createRoot(String xml) throws Exception {
        Root root = new Root();
        root.xml = parse(xml).getDocumentElement();
        return root;
    }

    private Document parse(String xml) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        return factory.newDocumentBuilder().parse(new InputSource(new StringReader(xml)));
    }

    @XmlAccessorType(XmlAccessType.FIELD)
    @XmlRootElement(name = "root", namespace = NAMESPACE)
    @XmlType(namespace = NAMESPACE)
    private static class Root {

        @XmlAnyElement
        private Element xml;
    }
}
