/*
 * SPDX-FileCopyrightText: 2026 Piotr P. Karwasz <piotr@github.copernik.eu>
 * SPDX-License-Identifier: Apache-2.0
 */

package eu.copernik.xml.factory;

import javax.xml.parsers.SAXParser;

import org.xml.sax.XMLReader;

/**
 * {@link SAXParser} wrapper whose {@link #getXMLReader()} returns the hardened reader.
 *
 * <p>{@link DelegatingSAXParser}'s inherited {@code parse(...)} overloads call {@code this.getXMLReader()} virtually, so routing them through the hardened
 * reader covers even {@code parse(source, handler)}, which installs the handler as the reader's entity resolver. With a plain {@link SAXParser} that call
 * would replace any pre-set resolver on the raw reader; here it goes through the hardened reader's deny-all floor instead.</p>
 */
final class HardeningSAXParser extends DelegatingSAXParser {

    private final XMLReader reader;

    HardeningSAXParser(final SAXParser delegate, final XMLReader reader) {
        super(delegate);
        this.reader = reader;
    }

    @Override
    public XMLReader getXMLReader() {
        return reader;
    }
}
