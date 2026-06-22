/*
 * SPDX-FileCopyrightText: 2026 Piotr P. Karwasz <piotr@github.copernik.eu>
 * SPDX-License-Identifier: Apache-2.0
 */

package eu.copernik.xml.factory;

import org.xml.sax.EntityResolver;
import org.xml.sax.XMLReader;

/**
 * {@link XMLReader} wrapper that keeps a {@link Resolvers.FallbackDenyResolver} floor as the reader's entity resolver, non-overridable by the caller.
 *
 * <p>The floor is installed once and stays the reader's entity resolver for the wrapper's lifetime; {@link #setEntityResolver(EntityResolver)} routes the
 * caller's resolver through {@link Resolvers.FallbackDenyResolver#setDelegate} instead of replacing it. This includes the {@code DefaultHandler} that
 * {@link javax.xml.parsers.SAXParser#parse(org.xml.sax.InputSource, org.xml.sax.helpers.DefaultHandler) SAXParser.parse(source, handler)} installs as the
 * reader's entity resolver, which would otherwise silently replace the floor. {@link #getEntityResolver()} reports the caller's resolver unwrapped.</p>
 *
 * <p>A provider that needs a non-deny floor (e.g. one that also permits the external DTD subset) passes a {@link Resolvers.FallbackDenyResolver} subclass to the
 * two-argument constructor; a single stable floor instance also lets that subclass double as a {@link org.xml.sax.ext.LexicalHandler}.</p>
 */
final class HardeningXMLReader extends DelegatingXMLReader {

    private final Resolvers.FallbackDenyResolver floor;

    HardeningXMLReader(final XMLReader delegate) {
        this(delegate, new Resolvers.FallbackDenyResolver(null));
    }

    HardeningXMLReader(final XMLReader delegate, final Resolvers.FallbackDenyResolver floor) {
        super(delegate);
        this.floor = floor;
        super.setEntityResolver(floor);
    }

    @Override
    public void setEntityResolver(final EntityResolver resolver) {
        floor.setDelegate(resolver);
    }

    @Override
    public EntityResolver getEntityResolver() {
        return floor.getDelegate();
    }
}
