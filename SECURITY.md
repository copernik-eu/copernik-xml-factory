<!--
  ~ SPDX-FileCopyrightText: 2026 Piotr P. Karwasz <piotr@github.copernik.eu>
  ~ SPDX-License-Identifier: Apache-2.0
  -->

# Security Policy

## Reporting a Vulnerability

Please report security vulnerabilities through GitHub Private Vulnerability Reporting:

https://github.com/copernik-eu/copernik-xml-factory/security

Do **not** report security issues through public GitHub issues, pull requests, or discussions.

## Threat Model

### Scope and intended use

This library is a helper for **safely creating JAXP factories**. Each `XmlFactories.newXxxFactory()` method returns a
fresh, hardened factory whose parsers reject the common XML attacks (external entity / DTD resolution, XXE, SSRF through
external references, and entity-expansion denial of service such as Billion Laughs). The exact guarantee each factory
makes is documented in the Javadoc:

https://javadoc.io/doc/eu.copernik/copernik-xml-factory/latest/eu/copernik/xml/factory/XmlFactories.html

The hardening applies to the factory and to the parsers, readers, transformers, validators and schemas it produces.

### What is in scope

- The hardening recipes applied by `XmlFactories` to the JAXP implementations it recognises (stock JDK, Apache Xerces,
  Xalan, Saxon, Woodstox, and Android's Expat/KXmlParser).
- A factory returned by `XmlFactories`, used as delivered, that fails to provide a guarantee the Javadoc states it
  provides.

### Assumptions about the environment

The library does not open network connections, spawn processes, install signal handlers, or read environment variables
of its own: each `XmlFactories` method only configures and returns a JAXP factory, and reads the JDK system properties
listed below. Which hardening recipe applies depends on the JAXP implementation present on the classpath.

**System properties that modify behaviour**

The processing limits are read from the following JDK system properties when a factory or parser is created, and the
same value is applied to the bundled parsers. If a property is unset, the JDK 25 secure value shown applies; a deployer
may set it to tighten (or loosen) a limit globally.

- `jdk.xml.elementAttributeLimit`: `200`
- `jdk.xml.entityExpansionLimit`: `2500`
- `jdk.xml.entityReplacementLimit`: `100000`
- `jdk.xml.maxElementDepth`: `100`
- `jdk.xml.maxGeneralEntitySizeLimit`: `100000`
- `jdk.xml.maxOccurLimit`: `5000`
- `jdk.xml.maxParameterEntitySizeLimit`: `15000`
- `jdk.xml.maxXMLNameLimit`: `1000`
- `jdk.xml.totalEntitySizeLimit`: `100000`

**Reserved settings (must not be loosened)**

The library MAY rely on the following features, attributes and properties staying as configured. They are reserved because
they govern external resource access, DTD, entity or schema handling, the installation of a resolver, or processing
limits; loosening any of them, on the returned factory or on a parser, reader, transformer, validator or schema it
produces, breaks the hardening for that instance.

- `com.ctc.wstx.dtdResolver`
- `com.ctc.wstx.entityResolver`
- `com.ctc.wstx.undeclaredEntityResolver`
- `http://apache.org/xml/features/disallow-doctype-decl`
- `http://apache.org/xml/features/nonvalidating/load-external-dtd`
- `http://apache.org/xml/properties/internal/entity-resolver`
- `http://javax.xml.XMLConstants/feature/secure-processing`
- `http://javax.xml.XMLConstants/property/accessExternalDTD`
- `http://javax.xml.XMLConstants/property/accessExternalSchema`
- `http://javax.xml.XMLConstants/property/accessExternalStylesheet`
- `http://saxon.sf.net/feature/allow-external-functions`
- `http://saxon.sf.net/feature/allowedProtocols`
- `http://xml.org/sax/features/external-general-entities`
- `http://xml.org/sax/features/external-parameter-entities`
- `javax.xml.stream.isSupportingExternalEntities`
- `javax.xml.stream.supportDTD`
- `jdk.xml.overrideDefaultParser`
- the JDK processing-limit properties listed above

This list is not exhaustive: any other feature, attribute, property or system property that grants access to an external
resource, relaxes DTD or entity processing, installs a resolver, or raises a processing limit is reserved on the same
terms. Installing a resolver through the typed `set*Resolver` methods, or through the `DefaultHandler` passed to
`SAXParser.parse`, has the same effect (see [What is out of scope](#what-is-out-of-scope)).

**Settings you may modify**

The following are security-relevant but safe to change on a returned factory: the protection they appear to govern is
enforced by the reserved settings above, which a caller cannot lift.

- **Validation.** You may turn on DTD or XSD validation, using these methods and features/properties:
  - `setSchema(Schema)`,
  - `setValidating(true)`,
  - `http://xml.org/sax/features/validation`,
  - `http://apache.org/xml/features/validation/schema`,
  - `http://java.sun.com/xml/jaxp/properties/schemaLanguage`,
  - `http://java.sun.com/xml/jaxp/properties/schemaSource`,
  - `http://apache.org/xml/properties/schema/external-schemaLocation`,
  - `http://apache.org/xml/properties/schema/external-noNamespaceSchemaLocation`.

  An external DTD or schema named through any of these is still refused, so supply the schema yourself (in memory through
  `setSchema` / `schemaSource`, or by installing a resolver that resolves the resource and does not return `null`).

- **XInclude.** You may turn on XInclude support, using these methods and features/properties:
  - `setXIncludeAware(true)`,
  - `http://apache.org/xml/features/xinclude`.

  As in the previous case, you need to provide a secure resolver.

### What is out of scope

A returned factory is hardened as delivered; reconfiguring it is a decision to take over hardening for that instance,
and reports against a factory reconfigured in any of the ways below are out of scope.

- **Modifying a reserved setting.** Loosening any feature, attribute or property reserved under
  [Assumptions about the environment](#assumptions-about-the-environment).
- **Installing your own resolver.** Setting an entity, resource or URI resolver, whether it returns `null` or returns
  content, replaces the resolution policy the hardening relies on. This includes the `DefaultHandler` passed to
  `SAXParser.parse(..., DefaultHandler)`, which the parser installs as its entity resolver.
- **Caller-supplied top-level URIs.** A URI passed directly to a parse call (`DocumentBuilder.parse(String)`,
  `StreamSource(systemId)`, a `SAXSource` built from a system id) is fetched as-is by the JAXP implementation without
  consulting the hardening layer. Restrict it yourself if the URI is untrusted.
- The behaviour of a JAXP implementation that `XmlFactories` does not recognise (it throws rather than returning an
  unhardened factory), and any defect in the underlying JAXP implementation itself.

### Downstream responsibility

Use the factory as returned. If you reconfigure it, you take over hardening for that instance and are responsible for
re-establishing any protection you remove.
