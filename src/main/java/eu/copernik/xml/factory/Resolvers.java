/*
 * SPDX-FileCopyrightText: 2026 Piotr P. Karwasz <piotr@github.copernik.eu>
 * SPDX-License-Identifier: Apache-2.0
 */

package eu.copernik.xml.factory;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;

import javax.xml.stream.XMLResolver;
import javax.xml.stream.XMLStreamException;
import javax.xml.transform.TransformerException;
import javax.xml.transform.URIResolver;

import org.w3c.dom.ls.LSResourceResolver;
import org.xml.sax.EntityResolver;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.ext.EntityResolver2;

/**
 * Policy resolvers that fix the outcome of every external lookup.
 *
 * <p>Three members are exposed:</p>
 * <ul>
 *     <li>{@link DenyAll} refuses every lookup with an exception. Stateless singletons; use them on schema/XSLT compile paths and on StAX entity hooks where
 *         any external fetch is a hardening violation.</li>
 *     <li>{@link IgnoreAll} returns an empty input. Stateless singleton; use it on Woodstox's DTD-subset and undeclared-entity hooks where the parse must
 *         continue with no replacement content.</li>
 *     <li>{@link FallbackDenyResolver} denies the SAX/DOM entity channel but, unlike the others, is instantiated to wrap an optional caller-supplied resolver so
 *         a caller can opt specific resources in without removing the deny-all floor.</li>
 * </ul>
 *
 * <p>The {@link DenyAll} and {@link IgnoreAll} singletons cover the {@link LSResourceResolver}, {@link URIResolver} and {@link XMLResolver} channels, which have
 * no caller-override concern.</p>
 */
final class Resolvers {

    /**
     * Refuses every external resource lookup with an exception. All members are single-method resolvers exposed as lambdas.
     */
    static final class DenyAll {

        /**
         * Refuses every {@code xs:import}/{@code xs:include}/{@code xs:redefine} lookup at schema-compile time.
         */
        static final LSResourceResolver LS_RESOURCE = (type, namespaceURI, publicId, systemId, baseURI) -> {
            throw new SecurityException(forbiddenMessage(type, namespaceURI, publicId, systemId, baseURI));
        };

        /**
         * Refuses every {@code xsl:import}/{@code xsl:include}/{@code document()} lookup during XSLT compile and transform.
         */
        static final URIResolver URI = (href, base) -> {
            throw new TransformerException(forbiddenMessage("uri", null, null, href, base));
        };

        /**
         * Refuses every external entity lookup performed by a StAX parser.
         */
        static final XMLResolver XML = (publicID, systemID, baseURI, namespace) -> {
            throw new XMLStreamException(forbiddenMessage(null, namespace, publicID, systemID, baseURI));
        };

        private DenyAll() {
        }
    }

    /**
     * Returns an empty input for every external resource lookup so the parse can continue without replacement content.
     *
     * <p>Only an {@link XMLResolver} flavour is exposed: schema and XSLT compile paths must always deny imports, and SAX/DOM use {@link FallbackDenyResolver}
     * plus {@link AndroidProvider}'s subset-aware resolver where needed.</p>
     */
    static final class IgnoreAll {

        /**
         * Empty {@link ByteArrayInputStream} shared across every call. {@code read()} on a zero-length array always returns {@code -1}, so reusing the
         * instance is safe even though the type is technically stateful.
         */
        private static final InputStream EMPTY = new ByteArrayInputStream(new byte[0]);

        /**
         * Returns an empty input for every external entity lookup performed by a StAX parser.
         */
        static final XMLResolver XML = (publicID, systemID, baseURI, namespace) -> EMPTY;

        private IgnoreAll() {
        }
    }

    /**
     * {@link EntityResolver2} that consults an optional caller-supplied resolver and denies (throws) whatever the caller does not resolve.
     *
     * <p>This is the entity-resolution counterpart of the JAXP 1.5 {@code ACCESS_EXTERNAL_*} properties: a non-overridable floor. The hardened DOM and SAX
     * wrappers install one of these and, when the caller sets their own {@link EntityResolver}, re-wrap it here rather than letting it replace the floor. A
     * caller therefore opts a specific resource in by returning a non-{@code null} {@link InputSource} from their resolver; anything they leave unresolved (a
     * {@code null} return, or no caller resolver at all) is refused instead of fetched.</p>
     *
     * <p>Only {@link #resolveEntity(String, String, String, String) resolveEntity} (the actual external fetch) falls back to denying. {@link #getExternalSubset}
     * delegates when possible and otherwise returns {@code null} (the canonical "no synthetic subset" signal); denying there would break ordinary parsing.</p>
     */
    static final class FallbackDenyResolver implements EntityResolver2 {

        /**
         * Caller-supplied resolver consulted first, or {@code null} for a pure deny-all floor.
         */
        private final EntityResolver delegate;

        FallbackDenyResolver(final EntityResolver delegate) {
            this.delegate = delegate;
        }

        @Override
        public InputSource getExternalSubset(final String name, final String baseURI) throws SAXException, IOException {
            return delegate instanceof EntityResolver2 ? ((EntityResolver2) delegate).getExternalSubset(name, baseURI) : null;
        }

        @Override
        public InputSource resolveEntity(final String publicId, final String systemId) throws SAXException, IOException {
            return resolveEntity(null, publicId, null, systemId);
        }

        @Override
        public InputSource resolveEntity(final String name, final String publicId, final String baseURI, final String systemId)
                throws SAXException, IOException {
            final InputSource resolved = resolveWithDelegate(name, publicId, baseURI, systemId);
            if (resolved != null) {
                return resolved;
            }
            throw new SAXException(forbiddenMessage(name, null, publicId, systemId, baseURI));
        }

        private InputSource resolveWithDelegate(final String name, final String publicId, final String baseURI, final String systemId)
                throws SAXException, IOException {
            if (delegate instanceof EntityResolver2) {
                return ((EntityResolver2) delegate).resolveEntity(name, publicId, baseURI, systemId);
            }
            return delegate != null ? delegate.resolveEntity(publicId, systemId) : null;
        }
    }

    private static String forbiddenMessage(final String type, final String namespace, final String publicId, final String systemId, final String baseURI) {
        return String.format("External resource fetch forbidden by hardening: type=%s, namespace=%s, publicId=%s, systemId=%s, baseURI=%s", type, namespace,
                publicId, systemId, baseURI);
    }

    private Resolvers() {
    }
}
