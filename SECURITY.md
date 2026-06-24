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

This model is versioned with the library: a report against a released version is triaged against the model as
it stood at that version, not at `HEAD`. A finding that breaks something listed under [What is in
scope](#what-is-in-scope) should be reported through the channel above; a finding that falls under [What is
out of scope](#what-is-out-of-scope) will be closed citing this section.

### Scope and intended use

This library is a helper for **safely creating JAXP factories**. Each `XmlFactories.newXxxFactory()` method returns a
fresh, hardened factory whose parsers reject the common XML attacks (external entity / DTD resolution, XXE, SSRF through
external references, and entity-expansion denial of service such as Billion Laughs). The exact guarantee each factory
makes is documented in the Javadoc:

https://javadoc.io/doc/eu.copernik/copernik-xml-factory/latest/eu/copernik/xml/factory/XmlFactories.html

The hardening applies to the factory and to the parsers, readers, transformers, validators and schemas it produces.

### Adversary model and trust boundary

The adversary is whoever controls the XML an application parses, together with any external system an XML
document tries to reach through an entity, DTD, schema, stylesheet, or XInclude reference. The hardening
exists to stop that untrusted document from reading local resources, reaching the network, or exhausting
memory or CPU.

The trust boundary is the factory as returned by `XmlFactories`. The XML handed to a parser, reader,
transformer, validator or schema produced by that factory is **untrusted**; the configuration of the factory
is **trusted**, and keeping it as delivered is the caller's responsibility. A caller running in the same
process can always reconfigure or replace the factory, so such a caller is not an adversary this model
defends against: that is the reason reconfiguration moves a report
[out of scope](#what-is-out-of-scope).

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

### Known non-findings

XML-security scanners and static analysers routinely flag the parsers this library produces. The following
are **not** vulnerabilities under this model:

- A claim that a factory or instance produced by `XmlFactories` is unsafe, without showing that a reserved
  setting was loosened, a resolver was installed, or an untrusted top-level URI was passed (see
  [Assumptions about the environment](#assumptions-about-the-environment) and
  [What is out of scope](#what-is-out-of-scope)). As delivered, the instance is hardened; the bare presence
  of a `SAXParser`, `DocumentBuilder`, `XMLReader`, `Transformer`, `Validator` or `Schema` is not a finding.
- XXE, external-entity, SSRF-through-external-reference, or entity-expansion (Billion Laughs) reports against
  a factory used as delivered. Blocking these is exactly what the hardening does. A working proof against an
  unmodified instance is a `VALID` finding (see below); a scanner that pattern-matches on parser type is not.
- Reports against an instance after the caller installed a resolver (including the `DefaultHandler` passed to
  `SAXParser.parse(..., DefaultHandler)`) or loosened a reserved setting.
- Reports about a top-level URI the caller passed directly to a parse call. That URI is fetched as-is and is
  the caller's to validate.
- Reports in a JAXP implementation this library does not recognise: `XmlFactories` throws rather than
  returning an unhardened factory, so there is no instance to attack.

### Triage dispositions

A report judged against this model receives exactly one of:

| Disposition | Meaning |
| --- | --- |
| `VALID` | A factory or instance used as delivered fails to provide a guarantee its Javadoc states (for example, a hardened parser still resolves an external entity, or a documented processing limit is not applied). |
| `OUT-OF-SCOPE: reconfigured` | A reserved setting was loosened, or a resolver was installed, on the factory or a produced instance before the reported behaviour (see [What is out of scope](#what-is-out-of-scope)). |
| `OUT-OF-SCOPE: caller input` | The behaviour follows from a top-level URI, or other input, the caller passed directly to a parse call. |
| `OUT-OF-SCOPE: foreign implementation` | The behaviour is in a JAXP implementation `XmlFactories` does not recognise, or in the underlying JAXP implementation itself. |
| `MODEL-GAP` | The report fits none of the above. The model is then incomplete: revise it rather than making an ad-hoc call. |

### Conditions that would change this model

Revise this model when any of the following change: a new `XmlFactories` factory method or other public
surface; support for a JAXP implementation beyond those listed under [What is in scope](#what-is-in-scope);
a change in the JDK secure defaults that the processing-limit list records; a new reserved setting; or a
report that cannot be routed to one of the dispositions above.
