<h1>
OpenUniverse
</h1>

<a title="License MIT" href="https://github.com/ou-org/OpenUniverse/blob/main/LICENSE"><img src="https://img.shields.io/badge/license-MIT-blue?style=flat-square"></a>
<a title="Latest release" href="https://github.com/ou-org/OpenUniverse/releases"><img src="https://img.shields.io/github/v/release/ou-org/OpenUniverse?style=flat-square&color=28A745"></a>
<a title="Powered by OpenUniverse" href="https://github.com/ou-org/OpenUniverse"><img src="https://img.shields.io/badge/powered_by-OpenUniverse-blueviolet?style=flat-square"></a>

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
  OpenUniverse operates in several stages to transform static documents definitions into a dynamic,
  event-driven
  infrastructure.
  <br><br>
  First, the repository working directory is scanned for documents. Each document is represented as a JSON
  object. A single JSON file may contain a single document or an array of documents. During this scan,
  OpenUniverse automatically skips any documents with unsupported specification versions or those explicitly
  marked as disabled.
  <br><br>
  Next, each discovered document is passed through an optional chain of pre-run constraints, if declared.
  Constraints ensure that documents meet structural, logical, and environmental requirements before execution.
  Processing does not starts if any constraint reports invalid requirements.
  <br><br>
  Once validation is complete, OpenUniverse enters the discovery phase. Here, the system analyzes declared
  search queries, resolves cross-references between documents, and maps their relationships.
  <br><br>
  After discovery, OpenUniverse loads the resolved document instances and begins execution. At this point, the
  runtime becomes active:
<ul>
  <li><b>Triggers</b> fire events based on conditions, calendars, or schedules</li>
  <li><b>Processors</b> consume event messages and execute the appropriate activities across <b>systems</b> defined
    in
    <b>jobs</b>
  </li>
  <li><b>Export targets</b> deliver data to external backends for storage or further processing</li>
  <li><b>DMQ</b> intercepts undeliverable or failed messages, routing them into a dedicated dead-message queue
    for later inspection, retries, or manual handling</li>
</ul>
</p>

## Getting Started

### 1. Download OpenUniverse binary

```sh
wget -P ~/Downloads https://example.com/ou
```

### 2. Download "Hello, Universe!" example

Download archive with ready to use sample repository:

```sh
mkdir -p /path/to/your/folder
curl -L "https://example.com/HelloUniverse.tar.gz" | tar -xzf - -C /path/to/your/folder
```

### 3. Strart OpenUniverse

```sh
ou /path/to/your/folder start --stdout --assume-yes
```

### 4. Listen Your Universe!

Just point your browser to
[http://localhost:8080/events](http://localhost:8080/events)

> [!NOTE]
> Read more about HelloUniverse example
> [here](https://example.com/HelloUniverse.md).

## Documentation

- [Core components](doc/core-components.md)
- [Ontology Overview](doc/ontology.md)
- [Query Syntax](doc/query-syntax.md)
- [Abstract Document Structure](doc/abstract-documents-structure.md)
- [Root Document Structure (work in progress...)](doc/root-documents-structure.md)
- [Syntax for Placeholder Injection](doc/placeholder-injection.md)
- [Java Requirement](doc/java-requirement.md)
- [Java Search Order](doc/java-search-order.md)
- [Build Guide](doc/build-guide.md)
- [Command-line Interface](doc/command-line-interface.md)
- [Running Guide](doc/running-guide.md)

## Tutorials

- [Hello Universe: First Steps with OpenUniverse](doc/examples/hello-universe.md)
- [Signing Output Records](doc/signing-guide.md)
- [Check File Size: Triggered Validation Example](doc/triggered-validation.md)
- [Send data to Apache Solr](doc/solr-exporter.md)

## Document Examples

- [CheckFile](doc/examples/CheckFile.json)
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

## Useful Guides

- [Minimal Git Repository Guide](doc/git-repo.md)
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
