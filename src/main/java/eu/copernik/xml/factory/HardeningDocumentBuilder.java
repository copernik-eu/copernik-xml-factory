/*
 * SPDX-FileCopyrightText: 2026 Piotr P. Karwasz <piotr@github.copernik.eu>
 * SPDX-License-Identifier: Apache-2.0
 */

package eu.copernik.xml.factory;

import javax.xml.parsers.DocumentBuilder;

import org.xml.sax.EntityResolver;

/**
 * {@link DocumentBuilder} wrapper that keeps a deny-all {@link EntityResolver} as a non-overridable floor.
 *
 * <p>A caller-set resolver is sandwiched inside a {@link Resolvers.FallbackDenyResolver} instead of replacing the deny-all one, so an external lookup the caller's
 * resolver does not satisfy is denied rather than fetched. {@link #reset()} re-establishes the bare deny-all floor, matching the just-constructed state.</p>
 */
final class HardeningDocumentBuilder extends DelegatingDocumentBuilder {

    HardeningDocumentBuilder(final DocumentBuilder delegate) {
        super(delegate);
        installFloor();
    }

    @Override
    public void setEntityResolver(final EntityResolver resolver) {
        super.setEntityResolver(new Resolvers.FallbackDenyResolver(resolver));
    }

    @Override
    public void reset() {
        super.reset();
        installFloor();
    }

    private void installFloor() {
        super.setEntityResolver(new Resolvers.FallbackDenyResolver(null));
    }
}
