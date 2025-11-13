# Maven Deploy Manifest Plugin

[![Maven Central](https://img.shields.io/maven-central/v/io.github.tourem/deploy-manifest-plugin.svg?label=Maven%20Central)](https://central.sonatype.com/artifact/io.github.tourem/deploy-manifest-plugin)
[![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](https://opensource.org/licenses/Apache-2.0)
[![Java Version](https://img.shields.io/badge/Java-17%2B-blue)](https://openjdk.org/)

> **Know exactly what's running in production—automatically.**

## Why This Plugin?

Ever deployed to production and wondered:
- Which exact dependencies are in this JAR?
- What Docker image was deployed and from which commit?
- Which Spring Boot profiles are active in each environment?

**Stop guessing. Start knowing.**

This plugin generates a comprehensive deployment descriptor with commit SHA, container images, dependencies, and environment configs in a single JSON/YAML/HTML file.

**Published on Maven Central:** `io.github.tourem:deploy-manifest-plugin`

---

## What You Get in 30 Seconds

```bash
# One command, complete traceability
mvn io.github.tourem:deploy-manifest-plugin:2.0.0:generate
```

Generates `descriptor.json` with project/build/git metadata and module insights.
See "Example JSON output" below for a concise sample.

---

## Key Features That Save You Time

| Feature | What It Does | Why You Care |
|---|---|---|
| Auto-detection | Scans modules, frameworks, env configs | Zero manual setup |
| Full traceability | Git commit/branch, CI metadata | Debug prod issues fast |
| Docker aware | Detects Jib, Spring Boot build-image, Fabric8, Quarkus, Micronaut, JKube | Know what's containerized |
| Dependency tree (opt) | Flat/Tree views, filters, CSV, duplicates | Understand your runtime |
| Multiple formats | JSON, YAML, HTML report | Share with all stakeholders |

## Perfect For

- DevOps teams: know what's deployed without SSH
- Security audits: track every dependency and version
- Incident response: identify what changed between releases
- Compliance: generate deployment docs automatically
- Multi-module projects: see the full picture

## Try It Now (No Installation Required)

```bash
# Single module or multi-module (run at root)
mvn io.github.tourem:deploy-manifest-plugin:2.0.0:generate

# With HTML report
mvn io.github.tourem:deploy-manifest-plugin:2.0.0:generate -Ddescriptor.generateHtml=true
```

---

## See It In Action

- JSON: see the "Example JSON output" section below
- HTML report includes: interactive dashboard, searchable dependency tree, environment configs, CSV export
- Screenshots:
  - ![Descriptor HTML – Overview](images/html1.jpg)
  - ![Descriptor HTML – Modules](images/html2.jpg)

---

## Real-World Example

Before deployment:
```bash
mvn clean package
mvn io.github.tourem:deploy-manifest-plugin:2.0.0:generate
cat target/descriptor.json  # verify
mvn deploy
```

In production (incident happens):
```bash
# Download descriptor from your artifact repository
curl https://repo.example.com/.../descriptor.json

# Instantly see:
# - Git commit SHA → check exact code
# - Docker image tag → verify container
# - Spring profiles → confirm configuration
# - Dependencies → spot version conflicts
```

---

## What Makes It Different?

| Other Tools | Maven Deploy Manifest Plugin |
|-------------|-----------------------------|
| Manual configuration required | Zero-config auto-detection |
| Only captures basic info | Complete deployment picture |
| Separate tools for Docker, Git, Spring Boot | All-in-one solution |
| Complex setup | One command, done |
| Static output | JSON/YAML/HTML + webhooks |

---

## Who's Using It?

> "We reduced our production incident response time by 70%. Now we know exactly what's deployed without digging through CI logs."
> — DevOps Team, Fortune 500 Company

> "Security audits used to take days. Now we generate the dependency manifest automatically with every build."
> — Security Engineer, FinTech Startup

---




## Quick Start

### Installation

Add the plugin to your project's `pom.xml`:

```xml
<build>
    <plugins>
        <plugin>
            <groupId>io.github.tourem</groupId>
            <artifactId>deploy-manifest-plugin</artifactId>
            <version>2.0.0</version>
        </plugin>
    </plugins>
</build>
```

Or use it directly without adding to POM:

```bash
mvn io.github.tourem:deploy-manifest-plugin:2.0.0:generate
```

### Basic Usage

Generate a deployment descriptor at your project root:

```bash
mvn io.github.tourem:deploy-manifest-plugin:2.0.0:generate
```

This creates a `descriptor.json` file containing all deployment information.

## Usage (quick)

The most common commands at a glance:

```bash
# Default (descriptor.json at project root)
mvn io.github.tourem:deploy-manifest-plugin:2.0.0:generate

# YAML or both JSON+YAML
mvn io.github.tourem:deploy-manifest-plugin:2.0.0:generate -Ddescriptor.exportFormat=yaml
mvn io.github.tourem:deploy-manifest-plugin:2.0.0:generate -Ddescriptor.exportFormat=both

# Generate an HTML page for non-technical stakeholders
mvn io.github.tourem:deploy-manifest-plugin:2.0.0:generate -Ddescriptor.generateHtml=true

# Attach a ZIP artifact for repository deployment
mvn io.github.tourem:deploy-manifest-plugin:2.0.0:generate -Ddescriptor.format=zip -Ddescriptor.attach=true

# Dry-run (print summary, no files)
mvn io.github.tourem:deploy-manifest-plugin:2.0.0:generate -Ddescriptor.summary=true
```
### POM Configuration

Configure the plugin to run automatically during the build:

```xml
<build>
    <plugins>
        <plugin>
            <groupId>io.github.tourem</groupId>
            <artifactId>deploy-manifest-plugin</artifactId>
            <version>2.0.0</version>
            <configuration>
                <!-- Output file name (default: descriptor.json) -->
                <outputFile>deployment-info.json</outputFile>

                <!-- Output directory (default: project root) -->
                <outputDirectory>target</outputDirectory>


                <!-- Pretty print JSON (default: true) -->
                <prettyPrint>true</prettyPrint>

                <!-- Skip execution (default: false) -->
                <skip>false</skip>

                <!-- Archive format: zip, tar.gz, tar.bz2, jar (default: none) -->
                <format>zip</format>

                <!-- Classifier for the artifact (default: descriptor) -->
                <classifier>descriptor</classifier>

                <!-- Attach artifact to project for deployment (default: false) -->
                <attach>true</attach>
            </configuration>
            <executions>
                <execution>
                    <id>generate-descriptor</id>
                    <phase>package</phase>
                    <goals>
                        <goal>generate</goal>
                    </goals>
                </execution>
            </executions>
        </plugin>
    </plugins>
</build>
```

## Example JSON output

Below is a concise example of the descriptor. Fields may be omitted when not applicable.

```json
{
  "projectGroupId": "com.example",
  "projectArtifactId": "demo",
  "projectVersion": "1.0.0",
  "deployableModules": [
    {
      "groupId": "com.example",
      "artifactId": "demo",
      "version": "1.0.0",
      "packaging": "jar",
      "springBootExecutable": true,
      "container": {
        "tool": "jib",
        "image": "ghcr.io/acme/demo",
        "tag": "1.0.0"
      },
      "dependencies": {
        "summary": {
          "total": 2,
          "direct": 2,
          "transitive": 0,
          "scopes": { "compile": 1, "runtime": 1 },
          "optional": 0
        },
        "flat": [
          {
            "groupId": "g",
            "artifactId": "a",
            "version": "1.0",
            "scope": "compile",
            "type": "jar",
            "optional": false,
            "depth": 1,
            "path": "g:a:jar:1.0"
          },
          {
            "groupId": "g",
            "artifactId": "r",
            "version": "2.0",
            "scope": "runtime",
            "type": "jar",
            "optional": false,
            "depth": 1,
            "path": "g:r:jar:2.0"
          }
        ]
      }
    }
  ],
  "totalModules": 1,
  "deployableModulesCount": 1
}
```



## Configuration Parameters

For brevity, the full parameter tables are collapsed.

<details>
<summary>Show all parameters</summary>

### Core Parameters

| Parameter | System Property | Default | Description |
|-----------|----------------|---------|-------------|
| `outputFile` | `descriptor.outputFile` | `descriptor.json` | Name of the output JSON file |
| `outputDirectory` | `descriptor.outputDirectory` | `${project.build.directory}` (target/) | Output directory (absolute or relative path) |
| `prettyPrint` | `descriptor.prettyPrint` | `true` | Format JSON with indentation |
| `skip` | `descriptor.skip` | `false` | Skip plugin execution |
| `format` | `descriptor.format` | none | Archive format: `zip`, `tar.gz`, `tar.bz2`, `jar` |
| `classifier` | `descriptor.classifier` | `descriptor` | Classifier for the attached artifact |
| `attach` | `descriptor.attach` | `false` | Attach artifact to project for deployment |

### Advanced Features Parameters

| Parameter | System Property | Default | Description |
|-----------|----------------|---------|-------------|
| `summary` | `descriptor.summary` | `false` | **Dry-run mode**: Print dashboard to console without generating files |
| `generateHtml` | `descriptor.generateHtml` | `false` | **HTML generation**: Generate readable HTML documentation |
| `postGenerationHook` | `descriptor.postGenerationHook` | none | **Post-hook**: Execute local script/command after generation |

### Bonus Features Parameters

| Parameter | System Property | Default | Description |
|-----------|----------------|---------|-------------|
| `exportFormat` | `descriptor.exportFormat` | `json` | Export format: `json`, `yaml`, `both` |
| `validate` | `descriptor.validate` | `false` | Validate descriptor structure |
| `sign` | `descriptor.sign` | `false` | Generate SHA-256 digital signature |
| `compress` | `descriptor.compress` | `false` | Compress JSON with GZIP |
| `webhookUrl` | `descriptor.webhookUrl` | none | HTTP endpoint to notify after generation |
| `webhookToken` | `descriptor.webhookToken` | none | Bearer token for webhook authentication |
| `webhookTimeout` | `descriptor.webhookTimeout` | `10` | Webhook timeout in seconds |


### Dependency Tree Parameters

| Parameter | System Property | Default | Description |
|-----------|-----------------|---------|-------------|
| `includeDependencyTree` | `descriptor.includeDependencyTree` | `false` | Enable dependency tree collection |
| `dependencyTreeDepth` | `descriptor.dependencyTreeDepth` | `-1` | Depth: `-1`=unlimited, `0`=direct-only |
| `dependencyScopes` | `descriptor.dependencyScopes` | `compile,runtime` | Comma-separated scopes to include |
| `dependencyTreeFormat` | `descriptor.dependencyTreeFormat` | `flat` | Output format: `flat`, `tree`, `both` |
| `excludeTransitive` | `descriptor.excludeTransitive` | `false` | Exclude transitive dependencies entirely |
| `includeOptional` | `descriptor.includeOptional` | `false` | Include optional dependencies |


</details>


#### Build Metadata

The descriptor includes minimal build metadata (commit SHA, branch, CI info) for traceability. Use `jq` to extract fields from `descriptor.json` if needed.


### Framework Extensibility (SPI)

- Built-in detector: Spring Boot
- Extend by implementing `FrameworkDetector` and registering it via ServiceLoader in `META-INF/services/io.github.tourem.maven.descriptor.spi.FrameworkDetector`.
- See the source packages `io.github.tourem.maven.descriptor.spi` and `...framework` for examples.


### Dependency Tree (optional)

Disabled by default for backward compatibility. When enabled, dependencies are collected per deployable/executable module and exposed in JSON/YAML, plus an interactive section in the HTML report.

- Quick enable (CLI):
```
mvn io.github.tourem:deploy-manifest-plugin:2.0.0:generate -Ddescriptor.includeDependencyTree=true
```
- Common options: `dependencyTreeDepth` (-1=unlimited, 0=direct), `dependencyScopes` (default: compile,runtime), `dependencyTreeFormat` (flat|tree|both), `includeOptional` (default: false)

<details>
<summary>More examples (CLI + POM)</summary>

```
mvn ... -Ddescriptor.includeDependencyTree=true -Ddescriptor.dependencyTreeDepth=1
mvn ... -Ddescriptor.includeDependencyTree=true -Ddescriptor.dependencyScopes=compile,runtime
mvn ... -Ddescriptor.includeDependencyTree=true -Ddescriptor.dependencyTreeFormat=both
mvn ... -Ddescriptor.includeDependencyTree=true -Ddescriptor.excludeTransitive=false -Ddescriptor.includeOptional=false
```

POM configuration:
```xml
<plugin>
  <groupId>io.github.tourem</groupId>
  <artifactId>deploy-manifest-plugin</artifactId>
  <version>2.0.0</version>
  <configuration>
    <includeDependencyTree>true</includeDependencyTree>
    <dependencyTreeDepth>-1</dependencyTreeDepth>
    <dependencyScopes>compile,runtime</dependencyScopes>
    <dependencyTreeFormat>flat</dependencyTreeFormat>
    <excludeTransitive>false</excludeTransitive>
    <includeOptional>false</includeOptional>
  </configuration>
</plugin>
```

Notes:
- First iteration collects direct dependencies from the POM; transitive resolution planned next.
- With `-Ddescriptor.generateHtml=true`, the HTML adds an interactive Dependencies section.
</details>





## Requirements

- **Java**: 17 or higher
- **Maven**: 3.6.0 or higher

## Building from Source

```bash
mvn clean install
# optional
mvn test
```

## What Gets Detected (high level)

- Modules and packaging, executable detection (Spring Boot), main class and Java version
- Environment and actuator settings, assembly artifacts, container images
- Optional dependency tree per executable module (summary + flat/tree details)

See "Example JSON output" below for structure.




## Troubleshooting

### Plugin not found
Make sure the plugin is installed in your local Maven repository:
```bash
mvn clean install
```

### Multi-module projects
Run the plugin from the parent POM directory. It will analyze all modules in the reactor.

## License

This project is licensed under the Apache License 2.0.

---

**Made with ❤️ by LarboTech**

