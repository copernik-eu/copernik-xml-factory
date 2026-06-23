/*
 * SPDX-FileCopyrightText: 2026 Piotr P. Karwasz <piotr@github.copernik.eu>
 * SPDX-License-Identifier: Apache-2.0
 */

package eu.copernik.xml.factory;

import static eu.copernik.xml.factory.AttackTestSupport.assertParseFails;
import static eu.copernik.xml.factory.AttackTestSupport.assertParseSucceeds;
import static eu.copernik.xml.factory.AttackTestSupport.inputSource;
import static eu.copernik.xml.factory.AttackTestSupport.resourceUrl;
import static eu.copernik.xml.factory.AttackTestSupport.strictDocumentBuilder;
import static eu.copernik.xml.factory.AttackTestSupport.strictXMLReader;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.ThrowingSupplier;
import org.xml.sax.SAXException;
import org.xml.sax.XMLReader;

/**
 * Checks that a hardened, schema-validating parser does not fetch a schema named through an embedded
 * {@code xsi:schemaLocation} / {@code xsi:noNamespaceSchemaLocation} hint in the instance document (as opposed to the
 * parser-level schema-location properties exercised by {@link SchemaLocationPropertyTest}).
 *
 * <p>The fixtures declare the instance's root element, so a parser that resolves the hint validates the instance cleanly
 * and one that does not cannot. The permissive controls prove the external schema is reachable in principle, so the
 * hardened side throwing means the hint fetch was refused. The stock JDK refuses it through {@code accessExternalSchema=""};
 * external Apache Xerces, which ignores that property, refuses it through the deny-all entity-resolver floor.</p>
 *
 * <p>Not every parser supports these schema-validation knobs (Android's KXmlParser and Expat do not), so the whole
 * configuration runs through {@link #configureOrSkip}: a parser that rejects schema validation skips rather than fails.</p>
 */
@Tag("schema")
class SchemaLocationHintTest {

    private static final String SCHEMA_LANGUAGE = "http://java.sun.com/xml/jaxp/properties/schemaLanguage";
    private static final String SCHEMA_FEATURE = "http://apache.org/xml/features/validation/schema";
    private static final String XSI_NS = "http://www.w3.org/2001/XMLSchema-instance";
    private static final String LEAKED_NS = "http://example.org/leaked";

    /** Instance whose root, {@code <root>}, hints at the no-namespace fixture through {@code xsi:noNamespaceSchemaLocation}. */
    private static String noNamespaceHintInstance() {
        return "<root xmlns:xsi=\"" + XSI_NS + "\" xsi:noNamespaceSchemaLocation=\"" + resourceUrl("no-namespace.xsd") + "\">x</root>";
    }

    /** Instance whose root, {@code l:leaked}, hints at the namespaced fixture through {@code xsi:schemaLocation}. */
    private static String namespacedHintInstance() {
        return "<l:leaked xmlns:l=\"" + LEAKED_NS + "\" xmlns:xsi=\"" + XSI_NS + "\" xsi:schemaLocation=\"" + LEAKED_NS + " "
                + resourceUrl("included.xsd") + "\">x</l:leaked>";
    }

    /**
     * Runs the parser setup, skipping the test (rather than failing it) on parsers that do not accept these
     * schema-validation features/properties, such as Android's KXmlParser and Expat.
     */
    private static <T> T configureOrSkip(final ThrowingSupplier<T> setup) {
        try {
            return setup.get();
        } catch (final Throwable t) {
            return Assumptions.abort("Parser does not support schema validation through these features/properties: " + t);
        }
    }

    private static DocumentBuilder hardenedValidatingDom() {
        return configureOrSkip(() -> {
            final DocumentBuilderFactory factory = XmlFactories.newDocumentBuilderFactory();
            factory.setNamespaceAware(true);
            factory.setValidating(true);
            factory.setAttribute(SCHEMA_LANGUAGE, XMLConstants.W3C_XML_SCHEMA_NS_URI);
            return strictDocumentBuilder(factory);
        });
    }

    private static DocumentBuilder permissiveValidatingDom() {
        return configureOrSkip(() -> {
            final DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(true);
            factory.setValidating(true);
            factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, false);
            factory.setAttribute(SCHEMA_LANGUAGE, XMLConstants.W3C_XML_SCHEMA_NS_URI);
            return strictDocumentBuilder(factory);
        });
    }

    private static XMLReader hardenedValidatingSax() {
        return configureOrSkip(() -> {
            final SAXParserFactory factory = XmlFactories.newSAXParserFactory();
            factory.setNamespaceAware(true);
            factory.setValidating(true);
            factory.setFeature(SCHEMA_FEATURE, true);
            final SAXParser parser = factory.newSAXParser();
            parser.setProperty(SCHEMA_LANGUAGE, XMLConstants.W3C_XML_SCHEMA_NS_URI);
            return strictXMLReader(parser.getXMLReader());
        });
    }

    private static XMLReader permissiveValidatingSax() {
        return configureOrSkip(() -> {
            final SAXParserFactory factory = SAXParserFactory.newInstance();
            factory.setNamespaceAware(true);
            factory.setValidating(true);
            factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, false);
            factory.setFeature(SCHEMA_FEATURE, true);
            final SAXParser parser = factory.newSAXParser();
            parser.setProperty(SCHEMA_LANGUAGE, XMLConstants.W3C_XML_SCHEMA_NS_URI);
            return strictXMLReader(parser.getXMLReader());
        });
    }

    @Test
    void hardenedDomRefusesNoNamespaceHint() {
        final DocumentBuilder builder = hardenedValidatingDom();
        assertParseFails(() -> builder.parse(inputSource(noNamespaceHintInstance())), "DOM xsi:noNamespaceSchemaLocation", SAXException.class);
    }

    @Test
    void permissiveDomFetchesNoNamespaceHint() {
        final DocumentBuilder builder = permissiveValidatingDom();
        assertParseSucceeds(() -> builder.parse(inputSource(noNamespaceHintInstance())), "DOM xsi:noNamespaceSchemaLocation (permissive)");
    }

    @Test
    void hardenedDomRefusesSchemaLocationHint() {
        final DocumentBuilder builder = hardenedValidatingDom();
        assertParseFails(() -> builder.parse(inputSource(namespacedHintInstance())), "DOM xsi:schemaLocation", SAXException.class);
    }

    @Test
    void permissiveDomFetchesSchemaLocationHint() {
        final DocumentBuilder builder = permissiveValidatingDom();
        assertParseSucceeds(() -> builder.parse(inputSource(namespacedHintInstance())), "DOM xsi:schemaLocation (permissive)");
    }

    @Test
    void hardenedSaxRefusesNoNamespaceHint() {
        final XMLReader reader = hardenedValidatingSax();
        assertParseFails(() -> reader.parse(inputSource(noNamespaceHintInstance())), "SAX xsi:noNamespaceSchemaLocation", SAXException.class);
    }

    @Test
    void permissiveSaxFetchesNoNamespaceHint() {
        final XMLReader reader = permissiveValidatingSax();
        assertParseSucceeds(() -> reader.parse(inputSource(noNamespaceHintInstance())), "SAX xsi:noNamespaceSchemaLocation (permissive)");
    }

    @Test
    void hardenedSaxRefusesSchemaLocationHint() {
        final XMLReader reader = hardenedValidatingSax();
        assertParseFails(() -> reader.parse(inputSource(namespacedHintInstance())), "SAX xsi:schemaLocation", SAXException.class);
    }

    @Test
    void permissiveSaxFetchesSchemaLocationHint() {
        final XMLReader reader = permissiveValidatingSax();
        assertParseSucceeds(() -> reader.parse(inputSource(namespacedHintInstance())), "SAX xsi:schemaLocation (permissive)");
    }
}
