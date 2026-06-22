/*
 * SPDX-FileCopyrightText: 2026 Piotr P. Karwasz <piotr@github.copernik.eu>
 * SPDX-License-Identifier: Apache-2.0
 */

package eu.copernik.xml.factory;

import org.xml.sax.EntityResolver;
import org.xml.sax.XMLReader;

/**
 * {@link XMLReader} wrapper that keeps a deny-all {@link EntityResolver} as a non-overridable floor (see {@link Resolvers.FallbackDenyResolver}).
 *
 * <p>A caller-set resolver is wrapped rather than replacing the floor. This includes the {@code DefaultHandler} that
 * {@link javax.xml.parsers.SAXParser#parse(org.xml.sax.InputSource, org.xml.sax.helpers.DefaultHandler) SAXParser.parse(source, handler)} installs as the
 * reader's entity resolver, which would otherwise silently replace the deny-all one. {@link #getEntityResolver()} reports the caller's resolver unwrapped, so
 * the wrapping stays transparent.</p>
 */
final class HardeningXMLReader extends DelegatingXMLReader {

    private EntityResolver userResolver;

    HardeningXMLReader(final XMLReader delegate) {
        super(delegate);
        super.setEntityResolver(new Resolvers.FallbackDenyResolver(null));
    }

    @Override
    public void setEntityResolver(final EntityResolver resolver) {
        userResolver = resolver;
        super.setEntityResolver(new Resolvers.FallbackDenyResolver(resolver));
    }

    @Override
    public EntityResolver getEntityResolver() {
        return userResolver;
    }
}
