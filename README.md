<h1>
OpenUniverse
</h1>

<a title="Category UaC (Universe as Code)" href="https://github.com/ou-org/OpenUniverse"><img src="https://img.shields.io/badge/category-UaC_(Universe_as_Code)-blueviolet?style=flat-square"></a>
<a title="License MIT" href="https://github.com/ou-org/OpenUniverse/blob/main/LICENSE"><img src="https://img.shields.io/badge/license-MIT-blue?style=flat-square"></a>
<a title="Latest release" href="https://github.com/ou-org/OpenUniverse/releases"><img src="https://img.shields.io/github/v/release/ou-org/OpenUniverse?style=flat-square&color=28A745"></a>

<h2>What is Universe?</h2>
<p>
  A <b>Universe</b> is a complete digital environment that connects data, information, and knowledge across
  diverse domains. It extends beyond IT infrastructure to include ALL data sources like physical assets,
  business processes, regulatory constraints, data lifecycles, and knowledge flows. A Universe can be
  understood through different perspectives:
<ul>
  <li>Structural (composition, ownership, hierarchy).</li>
  <li>Functional (usage, control, data flow).</li>
  <li>Historical (lineage, evolution, journals and immutable traces of changes).</li>
  <li>Regulatory (compliance obligations, contractual bindings, access constraints).</li>
  <li>Protocols (standards, interoperability, communication rules).</li>
</ul>
It also encompasses events, transactions, state changes, and lifecycle transitions, thereby enabling reasoning
about system evolution. In this sense, a Universe is a living ecosystem where raw data flows into information,
and information evolves into knowledge, shaping how people, machines, and structures interact securely and
intelligently.
</p>
<h2>Universe as Code</h2>
<p>
  <b>UaC (Universe as Code)</b> is the forward-thinking extension of the well-known IaC (Infrastructure as
  Code)
  paradigm. While IaC focuses on describing and automating IT infrastructure such as servers, networks, and
  software stacks, UaC embraces a much broader and more complex scope.
  In UaC, the principle of declarative,
  code-driven definition extends not only to infrastructure but also to physical assets, business processes,
  regulatory constraints, data lifecycles, and knowledge flows.<br><br>
  Just as IaC transformed system
  administration into programmable workflows, UaC envisions an environment where entire digital-physical
  ecosystems can be described, evolved, and reasoned about through code. This approach anticipates future
  needs
  where interoperability, compliance, intelligence, and evolution of systems are orchestrated holistically,
  making UaC a natural evolution beyond IaC.
</p>
<h2>Why <span class="product-name">Open<wbr>Universe</span>?</h2>
<p>
  <b>Open<wbr>Universe</b> is an ultralight, security-first open source
  UaC (Universe as Code) platform that unifies your environment.
  <br><br>
  Empower your infrastructure with a code-first approach to event-driven cross-system workflows.
  Define triggers and responses as code, connect with diverse technologies, and support language-agnostic
  event sources and actions. Built for real-time coordination across modern distributed architecture.
  <br><br>
  Define triggers, workflows, and conditions declaratively. Write, version, and audit your entire
  orchestration
  logic using standard development tools. From simple alerts to complex multi-step responses, everything is
  stored and deployed as code — enabling consistent, automated, and reviewable operations.
</p>
<h2>How it works</h2>
<p>
  OpenUniverse operates in several stages to transform static document definitions into a dynamic,
  event-driven infrastructure.
  <br><br>
  First, the repository working directory is scanned for documents. Each document is represented as a JSON
  object. A single JSON file may contain a single document or an array of documents. During this scan,
  OpenUniverse automatically skips any documents with unsupported specification versions or those explicitly
  marked as disabled.
  <br><br>
  If the repository is marked to enforce signed commits (via repository configuration), OpenUniverse performs
  commit signature verification before any constraint checks:
  <ul>
    <li>The system extracts the HEAD commit signature and verifies its cryptographic validity.</li>
    <li>It evaluates the signing key:
      <ul>
        <li>Only keys marked as ultimately trusted (u) or fully trusted (f) in the GnuPG keyring are accepted.</li>
        <li>Keys that are revoked, expired, disabled, marginal, or unknown are rejected.</li>
      </ul>
    </li>
    <li>If the signature verification fails or the key does not meet the strict trust criteria, processing halts to prevent execution of untrusted or tampered documents.</li>
  </ul>
  If the repository is not marked for signed commits, this step is skipped and documents are processed normally.
  <br><br>
  Next, each discovered document is passed through an optional chain of pre-run constraints, if declared.
  Constraints ensure that documents meet structural, logical, and environmental requirements before execution.
  Processing does not start if any constraint reports invalid requirements.
  <br><br>
  Once validation is complete, OpenUniverse enters the discovery phase. Here, the system analyzes declared
  search queries, resolves cross-references between documents, and maps their relationships.
  <br><br>
  After discovery, OpenUniverse loads the resolved document instances and begins execution. At this point, the
  runtime becomes active:
<ul>
  <li><b>Triggers</b> fire events based on conditions, calendars, or schedules</li>
  <li><b>Processors</b> consume event messages and execute the appropriate activities across <b>systems</b> defined
      in <b>jobs</b></li>
  <li><b>Export targets</b> deliver data to external backends for storage or further processing</li>
  <li><b>DMQ</b> intercepts undeliverable or failed messages, routing them into a dedicated dead-message queue
      for later inspection, retries, or manual handling</li>
</ul>
</p>

## Why OpenUniverse Releases Are Source-Only

OpenUniverse is distributed as **source code + build tooling**, not as
precompiled binaries.\
This is an intentional design decision, and here’s why:

### 1. Security & Trust

Releasing only source means that you, the user, are in full control of the build
process.

- You can inspect the code you’re running.
- You compile it yourself, so you don’t need to trust an opaque binary artifact.
- Sensitive build parameters (e.g. your **keystore for JAR signing**) stay on
  your machine — they are never bundled or exposed in a public binary.

### 2. Customization

OpenUniverse is designed to be an **Infrastructure-as-Code platform**, which
often needs to be adapted to different environments:

- Different Linux distributions
- Custom runtime integrations
- Organization-specific signing keys, certificates, or packaging rules

A single prebuilt binary would either be too rigid or require shipping dozens of
variants.\
Instead, our **build script (`build.sh`) + properties file
(`build.properties`)** gives you a reproducible, customizable build pipeline.

### 3. Reproducibility

By shipping only the source:

- You always know _how_ the software was built.
- Builds are **deterministic**: two people running the same script get the same
  output (aside from user-specific signing).
- This aligns with modern reproducible-builds practices in open source.

### 4. Lightweight Releases

Instead of hosting large binary artifacts, releases are minimal:

- The `build.sh` script orchestrates everything.
- Dependencies (like JDK, Maven) are standard and easy to install.
- You only download what you need.

### 5. Developer Empowerment

OpenUniverse is meant for developers, operators, and infrastructure teams.
By keeping the release **source-first**, we encourage exploration, contribution,
and understanding of the platform rather than black-box usage.

---

`build.properties`

```properties
########################################################
# OpenUnvierse configuration properties file (Example) #
########################################################

# -----------------------------
# JAR SIGNING CONFIG
# -----------------------------

SIGN_JAR_KEYSTORE="$HOME/keystore.p12"
SIGN_JAR_STORETYPE="PKCS12"
SIGN_JAR_ALIAS="signing_alias"
SIGN_JAR_KEYPASS="your_password"
SIGN_JAR_STOREPASS="your_password"
SIGN_JAR_TSA="http://timestamp.digicert.com"

# -----------------------------
# APP IMAGE SIGNING CONFIG
# -----------------------------

# If unset or empty, generate unsigned AppImage (⚠️ WARNING! NOT RECOMMENDED IN PRODUCTION!)
YOUR_40_CHARACTER_HEX_FINGERPRINT="86A32BE7AB448F546095841B16F66731F8F57B73"
```

## Getting Started

#### 1. Download OpenUniverse binary:

[https://github.com/ou-org/OpenUniverse/releases/download/v1.0.21/ou-linux-x86_64](https://github.com/ou-org/OpenUniverse/releases/download/v1.0.21/ou-linux-x86_64)

#### 2. Download "Hello, Universe!" example:

Download and extract archive with ready to use sample repository:

[https://github.com/ou-org/OpenUniverse/releases/download/v1.0.21/HelloUniverse.zip](https://github.com/ou-org/OpenUniverse/releases/download/v1.0.21/HelloUniverse.zip)

#### 3. Strart Your Universe:

```sh
ou /path/to/your/folder/HelloUniverseRepo start --stdout --assume-yes
```

#### 4. Hear the Universe!

Open your browser and go to:

[http://localhost:8080/events](http://localhost:8080/events)

> [!NOTE]
> Read more about HelloUniverse example
> [here](https://github.com/ou-org/OpenUniverse/blob/master/doc/examples/HelloUniverse.md).

## Documentation

- [Core components](doc/core-components.md)
- [Ontology Overview](doc/ontology.md)
- [Query Syntax](doc/query-syntax.md)
- [Abstract Document Structure](doc/abstract-documents-structure.md)
- [Syntax for Placeholder Injection](doc/placeholder-injection.md)
- [Java Requirement](doc/java-requirement.md)
- [Java Search Order](doc/java-search-order.md)
- [Build Guide](doc/build-guide.md)
- [Command-line Interface](doc/command-line-interface.md)
- [Running Guide](doc/running-guide.md)

## Tutorials

- [Signing Output Records](doc/signing-guide.md)
- [Send data to Apache Solr](doc/solr-exporter.md)

## Examples

- [Hello, Universe!](doc/examples/HelloUniverse.md)
- [Check File Size](doc/examples/CheckFileSize.md)

<!--
- [Document Validator](doc/examples/document-validator.json)
- [Hello Solr](doc/examples/hello-solr.json)
- [Hello SSE (JSON)](doc/examples/hello-sse.json)
- [Hello SSE (YAML)](doc/examples/hello-sse.yaml)
- [Hello Universe](doc/examples/HelloUniverse.json)
- [Inject Properties](doc/examples/inject-properties.json)
- [Signing](doc/examples/Signing.json)
- [Trigger](doc/examples/Trigger.json)

## Plugin Examples

- [Decrypt](scripts/decrypt.py)
- [DMQ](scripts/dmq.py)
- [File Size Monitor](scripts/file_size_monitor.py)
- [File Validator (Python)](scripts/fileValidator.py)
- [File Validator (Shell)](scripts/fileValidator.sh)
- [OS](scripts/os.py)
- [Send to Solr](scripts/send-to-solr.py)
- [SSE](scripts/sse.py)
- [Validator](scripts/validator.py)
-->

## Useful Guides

- [Minimal Git Repository Guide](doc/git-repo.md)
- [Apache Solr Guide](doc/solr-guide.md)
- [Working with Encrypted Values](doc/work-with-encrypted-values.md)

## Related Works

1. [A Scalable, event-driven architecture for designing interactions across heterogeneous devices in smart environments](https://doi.org/10.1016/j.infsof.2019.01.006)<br>
   Ovidiu-Andrei Schipor, Radu-Daniel Vatavu, Jean Vanderdonckt. _Information
   and Software Technology_, Vol. 109 (2019), pp. 43–59 ISSN: 0950-5849<br>

2. [The Power of Event-Driven Architecture: Enabling RealTime Systems and Scalable Solutions](https://doi.org/10.61841/turcomat.v11i1.14928)<br>
   Adisheshu Reddy Kommera. _Turkish Journal of Computer and Mathematics
   Education_, Vol. 11 (2020), pp. 1740–1751 ISSN: 3048-4855<br>

3. [Exploring event-driven architecture in microservices — patterns, pitfalls and best practices](https://doi.org/10.30574/ijsra.2021.4.1.0166)<br>
   Ashwin Chavan. _International Journal of Science and Research Archive_, Vol.
   4 (2021), pp. 229–249 ISSN: 2582-8185<br>

## LICENSE

The OpenUniverse is released under the
<a href="https://github.com/ou-org/OpenUniverse/blob/main/LICENSE">MIT</a>
license.
