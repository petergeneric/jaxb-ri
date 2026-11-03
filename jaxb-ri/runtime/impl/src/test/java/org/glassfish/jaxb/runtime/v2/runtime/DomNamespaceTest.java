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
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.stream.XMLOutputFactory;
import javax.xml.stream.XMLStreamWriter;
import javax.xml.transform.dom.DOMResult;

import jakarta.xml.bind.JAXBContext;
import jakarta.xml.bind.JAXBException;
import jakarta.xml.bind.Marshaller;
import jakarta.xml.bind.Unmarshaller;
import jakarta.xml.bind.annotation.XmlAccessType;
import jakarta.xml.bind.annotation.XmlAccessorType;
import jakarta.xml.bind.annotation.XmlAnyElement;
import jakarta.xml.bind.annotation.XmlElement;
import jakarta.xml.bind.annotation.XmlRootElement;
import jakarta.xml.bind.annotation.XmlType;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.xml.sax.InputSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

public class DomNamespaceTest {

    private static final String NAMESPACE = "urn:foo";

    private static final String BASIC_ELEMENT_SAMPLE = "<x/>";

    private static final String EMPTY_NAMESPACE_SAMPLE = "<x xmlns=\"\"/>";

    private static final String BASIC_ELEMENT_EXPECTED =
            "<root xmlns=\"urn:foo\"><x xmlns=\"\"/></root>";

    private static final String NESTED_ELEMENT_SAMPLE = "<x><y/></x>";

    private static final String NESTED_ELEMENT_EXPECTED =
            "<root xmlns=\"urn:foo\"><x xmlns=\"\"><y/></x></root>";

    private static final String SIBLING_ELEMENT_EXPECTED =
            "<root xmlns=\"urn:foo\"><x xmlns=\"\"><y/></x><after>value</after></root>";

    private static final String QUALIFIED_ELEMENT_SAMPLE = "<x xmlns:p=\"urn:foo\" p:key=\"value\">"
            + "<p:y/><z xmlns=\"urn:foo\"/></x>";

    private static JAXBContext context;

    @BeforeAll
    public static void setUpBeforeClass() throws Exception {
        context = JAXBContext.newInstance(Root.class);
    }

    @Test
    public void shouldSerializeUnqualifiedElementWithoutRedundantNamespaces() throws Exception {
        Root root = createRoot(BASIC_ELEMENT_SAMPLE);
        String actual = serializeObject(root);
        assertEquals(BASIC_ELEMENT_EXPECTED, actual);
    }

    @Test
    public void shouldSerializeExplicitEmptyNamespaceWithoutRedundantNamespaces() throws Exception {
        Root root = createRoot(EMPTY_NAMESPACE_SAMPLE);
        String actual = serializeObject(root);
        assertEquals(BASIC_ELEMENT_EXPECTED, actual);
    }

    @Test
    public void shouldSerializeNestedElementsWithoutRedundantNamespaces() throws Exception {
        Root root = createRoot(NESTED_ELEMENT_SAMPLE);
        String actual = serializeObject(root);
        assertEquals(NESTED_ELEMENT_EXPECTED, actual);
    }

    @Test
    public void shouldRestoreParentNamespaceForFollowingJaxbElement() throws Exception {
        Root root = createRoot(NESTED_ELEMENT_SAMPLE);
        root.after = "value";

        String actual = serializeObject(root);

        assertEquals(SIBLING_ELEMENT_EXPECTED, actual);
    }

    @Test
    public void shouldPreserveDomNamespacesAndQualifiedAttributes() throws Exception {
        Root root = createRoot(QUALIFIED_ELEMENT_SAMPLE);

        String actual = serializeObject(root);
        Element child = (Element) parse(actual).getDocumentElement().getFirstChild();

        assertNull(child.getNamespaceURI());
        assertEquals(3, child.getAttributes().getLength());
        assertEquals("value", child.getAttributeNS(NAMESPACE, "key"));
        assertEquals(NAMESPACE, child.getFirstChild().getNamespaceURI());
        assertEquals("p:y", child.getFirstChild().getNodeName());
        assertEquals(NAMESPACE, child.getLastChild().getNamespaceURI());
    }

    @Test
    public void shouldPreserveDomDescendantsWithoutExplicitNamespaceDeclarations() throws Exception {
        Root root = createRoot(BASIC_ELEMENT_SAMPLE);
        Document document = root.xml.getOwnerDocument();
        root.xml.appendChild(document.createElementNS(NAMESPACE, "y"));
        root.xml.appendChild(document.createElementNS(NAMESPACE, "p:z"));

        String actual = serializeObject(root);
        Element child = (Element) parse(actual).getDocumentElement().getFirstChild();

        assertNull(child.getNamespaceURI());
        assertEquals(1, child.getAttributes().getLength());
        assertEquals(NAMESPACE, child.getFirstChild().getNamespaceURI());
        assertEquals(NAMESPACE, child.getLastChild().getNamespaceURI());
        assertEquals("p:z", child.getLastChild().getNodeName());
    }

    @Test
    public void shouldParseAndSerializeWithoutAddingNamespaceDeclarations() throws Exception {
        Unmarshaller unmarshaller = context.createUnmarshaller();
        String serialised = serializeObject(createRoot(BASIC_ELEMENT_SAMPLE));

        Root root = (Root) unmarshaller.unmarshal(new StringReader(serialised));
        String actual = serializeObject(root);

        assertEquals(BASIC_ELEMENT_EXPECTED, serialised);
        assertEquals(serialised, actual);
    }

    @Test
    public void shouldSerializeToDifferentOutputsWithOnlyTheRequiredNamespaceDeclaration() throws Exception {
        Root root = createRoot(BASIC_ELEMENT_SAMPLE);
        Marshaller marshaller = context.createMarshaller();
        marshaller.setProperty(Marshaller.JAXB_FRAGMENT, Boolean.TRUE);
        StringWriter writerOutput = new StringWriter();
        DOMResult domOutput = new DOMResult();
        StringWriter staxOutput = new StringWriter();
        XMLStreamWriter writer = XMLOutputFactory.newFactory().createXMLStreamWriter(staxOutput);

        marshaller.marshal(root, writerOutput);
        marshaller.marshal(root, domOutput);
        marshaller.marshal(root, writer);
        writer.close();

        assertUnqualifiedChild(parse(writerOutput.toString()));
        assertUnqualifiedChild((Document) domOutput.getNode());
        assertUnqualifiedChild(parse(staxOutput.toString()));
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

    private void assertUnqualifiedChild(Document document) {
        Element parent = document.getDocumentElement();
        assertEquals(NAMESPACE, parent.getNamespaceURI());
        Element child = (Element) parent.getFirstChild();
        assertNull(child.getNamespaceURI());
        assertEquals(1, child.getAttributes().getLength());
        assertEquals("", child.getAttributeNS(XMLConstants.XMLNS_ATTRIBUTE_NS_URI, "xmlns"));
    }

    @XmlAccessorType(XmlAccessType.FIELD)
    @XmlRootElement(name = "root", namespace = NAMESPACE)
    @XmlType(namespace = NAMESPACE, propOrder = {"xml", "after"})
    private static class Root {

        @XmlAnyElement
        private Element xml;

        @XmlElement(namespace = NAMESPACE)
        private String after;
    }
}
