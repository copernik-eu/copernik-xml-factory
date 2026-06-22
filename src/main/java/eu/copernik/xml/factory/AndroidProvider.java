/*
 * SPDX-FileCopyrightText: 2026 Piotr P. Karwasz <piotr@github.copernik.eu>
 * SPDX-License-Identifier: Apache-2.0
 */

package eu.copernik.xml.factory;

import java.io.IOException;
import java.util.Objects;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.SAXParserFactory;

import org.xml.sax.EntityResolver;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.SAXNotRecognizedException;
import org.xml.sax.SAXNotSupportedException;
import org.xml.sax.XMLReader;
import org.xml.sax.ext.LexicalHandler;

/**
 * Hardening recipes for Android's Apache Harmony based DOM and SAX implementation.
 *
 * <p>Factory classes live in the {@code org.apache.harmony.xml.parsers.*} package, but DOM and SAX are backed by two different engines:</p>
 * <ul>
 *     <li>{@link SAXParserFactory} produces {@code org.apache.harmony.xml.ExpatReader}, a SAX wrapper around the platform's native Expat parser.</li>
 *     <li>{@link DocumentBuilderFactory} produces {@code DocumentBuilderImpl}, which builds the DOM tree on top of {@code com.android.org.kxml2.io.KXmlParser}
 *         (a kxml2 pull parser).</li>
 * </ul>
 *
 * <p>What the SAX/Expat surface exposes:</p>
 * <ul>
 *     <li>SAX features: only {@code namespaces}, {@code namespace-prefixes}, {@code string-interning}, {@code validation},
 *         {@code external-general-entities} and {@code external-parameter-entities}. The last three are read-only and cannot be enabled. {@code namespace-prefixes}
 *         is recognised but enabling it is a no-op, and enabling it together with {@code namespaces} triggers an exception at parse time.</li>
 *     <li>SAX properties: only {@code lexical-handler}.</li>
 *     <li>{@link XMLConstants#FEATURE_SECURE_PROCESSING} and JAXP 1.5 {@code ACCESS_EXTERNAL_*} are not recognised.</li>
 *     <li>Entity expansion: native libexpat enforces a built-in Billion Laughs check (compiled-in activation threshold and amplification factor), so internal
 *         entity expansion is already bounded below us.</li>
 *     <li>Every external fetch (DTD subset, DOCTYPE {@code SYSTEM}, general/parameter entity) flows through the 2-arg {@link EntityResolver#resolveEntity}.
 *     Without a resolver external fetches are ignored.</li>
 * </ul>
 *
 * <p>What the DOM/KXmlParser surface exposes:</p>
 * <ul>
 *     <li>{@link DocumentBuilderFactory#setFeature} only recognises {@code namespaces} and {@code validation}.</li>
 *     <li>{@link DocumentBuilderFactory#setAttribute} always throws {@code IllegalArgumentException}.</li>
 *     <li>{@link XMLConstants#FEATURE_SECURE_PROCESSING} and JAXP 1.5 {@code ACCESS_EXTERNAL_*} are not recognised.</li>
 *     <li>Entity expansion: KXmlParser does not support user-defined entities and they are silently dropped.</li>
 * </ul>
 *
 * <p>The SAX path reuses {@link HardeningXMLReader} and {@link HardeningSAXParserFactory}, with a {@link DtdAwareDenyResolver} (a
 * {@link Resolvers.FallbackDenyResolver} subclass) as the floor: it is installed as both the {@link LexicalHandler} and the entity-resolver floor, allows the
 * external subset to load silently (so a DOCTYPE that names an external DTD but does not use it parses) and denies every external general or parameter entity
 * reference. A caller-set {@link EntityResolver} (including the handler that {@code SAXParser.parse(source, handler)} installs) is consulted first, but anything
 * it does not resolve falls through to the floor. {@link ExpatFeatureGuard} adds only the {@code setFeature} guard for ExpatReader's {@code namespace-prefixes}
 * quirk.</p>
 */
final class AndroidProvider {

    /**
     * Deny floor that additionally lets the external DTD subset declared by the DOCTYPE be skipped silently; merely <em>declaring</em> an external subset does
     * not throw.
     *
     * <p>As a {@link Resolvers.FallbackDenyResolver} it consults the caller's resolver first; as a {@link LexicalHandler} (via {@link org.xml.sax.ext.DefaultHandler2})
     * it tracks the declared subset's identifiers so {@link #onUnresolved} can tell the subset apart from a forbidden external general or parameter entity.</p>
     */
    private static final class DtdAwareDenyResolver extends Resolvers.FallbackDenyResolver {

        private String dtdPublicId;
        private String dtdSystemId;
        private boolean inDtd;

        DtdAwareDenyResolver() {
            super(null);
        }

        @Override
        public void startDTD(final String name, final String publicId, final String systemId) {
            inDtd = true;
            dtdPublicId = publicId;
            dtdSystemId = systemId;
        }

        @Override
        public void endDTD() {
            inDtd = false;
        }

        @Override
        protected InputSource onUnresolved(final String name, final String publicId, final String baseURI, final String systemId)
                throws SAXException, IOException {
            // Declaring (but not using) an external subset must not throw: let the parser skip it silently. Everything else is denied by the floor.
            if (inDtd && Objects.equals(publicId, dtdPublicId) && Objects.equals(systemId, dtdSystemId)) {
                return null;
            }
            return super.onUnresolved(name, publicId, baseURI, systemId);
        }
    }

    /**
     * {@link XMLReader} wrapper that surfaces ExpatReader's unsupported-feature behaviour at {@code setFeature} time.
     *
     * <p>ExpatReader supports only the {@code namespaces} feature; it recognises {@code namespace-prefixes} but enabling it is a no-op, and enabling both at once
     * makes {@code ExpatReader.parse()} throw {@link SAXNotSupportedException}. Rejecting {@code namespace-prefixes=true} up front prevents that conflict and lets
     * consumers such as Apache Xalan's identity transformer catch the exception at configuration time and still parse. This guard carries no entity-resolution
     * hardening, so it also serves as the permissive (unhardened) positive control.</p>
     */
    static final class ExpatFeatureGuard extends DelegatingXMLReader {

        ExpatFeatureGuard(final XMLReader delegate) {
            super(delegate);
        }

        @Override
        public void setFeature(final String name, final boolean value) throws SAXNotRecognizedException, SAXNotSupportedException {
            if (value && NAMESPACE_PREFIXES_FEATURE.equals(name)) {
                throw new SAXNotSupportedException(
                        "ExpatReader does not support enabling '" + NAMESPACE_PREFIXES_FEATURE + "'; only '" + NAMESPACES_FEATURE + "' is supported");
            }
            super.setFeature(name, value);
        }
    }

    private static final String LEXICAL_HANDLER_PROPERTY = "http://xml.org/sax/properties/lexical-handler";

    private static final String NAMESPACES_FEATURE = "http://xml.org/sax/features/namespaces";

    private static final String NAMESPACE_PREFIXES_FEATURE = "http://xml.org/sax/features/namespace-prefixes";

    static DocumentBuilderFactory configure(final DocumentBuilderFactory factory) {
        return factory;
    }

    static SAXParserFactory configure(final SAXParserFactory factory) {
        return new HardeningSAXParserFactory(factory, AndroidProvider::configure);
    }

    static XMLReader configure(final XMLReader reader) {
        // The SAXParserFactory hardener passes a raw ExpatReader.
        // Idempotency for an already-hardened HardeningXMLReader lives in XmlFactories.harden.
        final XMLReader guarded = reader instanceof ExpatFeatureGuard ? reader : new ExpatFeatureGuard(reader);
        final DtdAwareDenyResolver floor = new DtdAwareDenyResolver();
        try {
            guarded.setProperty(LEXICAL_HANDLER_PROPERTY, floor);
        } catch (final SAXException e) {
            // ExpatReader recognises the lexical-handler property; if a future replacement does not, fall through and lose subset-vs-entity discrimination.
        }
        // The floor doubles as the lexical handler (it tracks DTD state) and the entity-resolver floor.
        // HardeningXMLReader keeps it non-bypassable and routes a caller-set resolver through it.
        // ExpatFeatureGuard adds the namespace-prefixes guard underneath.
        return new HardeningXMLReader(guarded, floor);
    }

    private AndroidProvider() {
    }
}
