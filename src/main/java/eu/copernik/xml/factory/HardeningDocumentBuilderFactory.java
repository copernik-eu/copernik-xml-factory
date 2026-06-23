/*
 * SPDX-FileCopyrightText: 2026 Piotr P. Karwasz <piotr@github.copernik.eu>
 * SPDX-License-Identifier: Apache-2.0
 */

package eu.copernik.xml.factory;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import org.xml.sax.EntityResolver;

/**
 * Hardened {@link DocumentBuilderFactory} wrapper.
 *
 * <p>Wraps every {@link DocumentBuilder} produced in a {@link HardeningDocumentBuilder}, which keeps a deny-all {@link EntityResolver} floor; required when the
 * underlying factory carries no resolver of its own and does not honour JAXP 1.5 {@code ACCESS_EXTERNAL_*} (e.g. Xerces).</p>
 */
final class HardeningDocumentBuilderFactory extends DelegatingDocumentBuilderFactory {

    HardeningDocumentBuilderFactory(final DocumentBuilderFactory delegate) {
        super(delegate);
    }

    @Override
    public DocumentBuilder newDocumentBuilder() throws ParserConfigurationException {
        return new HardeningDocumentBuilder(super.newDocumentBuilder());
    }
}
