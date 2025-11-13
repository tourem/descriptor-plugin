# Maven Descriptor Plugin

[![Maven Central](https://img.shields.io/maven-central/v/io.github.tourem/descriptor-plugin.svg?label=Maven%20Central)](https://central.sonatype.com/artifact/io.github.tourem/descriptor-plugin)
[![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](https://opensource.org/licenses/Apache-2.0)
[![Java Version](https://img.shields.io/badge/Java-17%2B-blue)](https://openjdk.org/)

A Maven plugin that automatically generates comprehensive deployment descriptors for your Maven projects, including Spring Boot applications, multi-module projects, and environment-specific configurations.

**Published on Maven Central:** `io.github.tourem:descriptor-plugin`

## Features

### 🎯 Core Features
✅ **Automatic Module Detection**: Identifies deployable modules (JAR, WAR, EAR)
✅ **Spring Boot Support**: Detects Spring Boot executables, profiles, and configurations
✅ **Environment Configurations**: Extracts dev, hml, prod environment settings
✅ **Actuator Endpoints**: Discovers health, info, and metrics endpoints
✅ **Maven Assembly**: Detects assembly artifacts (ZIP, TAR.GZ)
✅ **Deployment Metadata**: Java version, main class, server ports, context paths
✅ **Multi-Module Projects**: Full support for Maven reactor builds

### 🚀 Advanced Features
✅ **Git & CI/CD Metadata**: Complete traceability with commit SHA, branch, author, CI provider
✅ **Framework Extensibility (SPI)**: Pluggable framework detection (Spring Boot, Quarkus, Micronaut)
✅ **Dry-Run Mode**: Preview descriptor in console without generating files
✅ **HTML Documentation**: Generate readable HTML reports for non-technical teams
✅ **Post-Generation Hooks**: Execute custom scripts after descriptor generation
- ✅ Container Images: Detect maintained Maven container plugins (Jib, Spring Boot build-image, Fabric8, Quarkus, Micronaut, JKube) and include image coordinates in the descriptor

- ✅ Dependency Tree (optional): Collect per-executable module dependencies with summary; interactive HTML (search, filters, table/tree views, CSV export, duplicates detection)


### 🎁 Bonus Features
✅ **Multiple Export Formats**: JSON, YAML, or both
✅ **Validation**: Validate descriptor structure before generation
✅ **Digital Signature**: SHA-256 signature for integrity verification
✅ **Compression**: GZIP compression to reduce file size
✅ **Webhook Notifications**: HTTP POST notifications with configurable endpoint
✅ **Archive Support**: ZIP, TAR.GZ, TAR.BZ2 formats with Maven deployment

## Quick Start

### Installation

Add the plugin to your project's `pom.xml`:

```xml
<build>
    <plugins>
        <plugin>
            <groupId>io.github.tourem</groupId>
            <artifactId>descriptor-plugin</artifactId>
            <version>1.3.0</version>
        </plugin>
    </plugins>
</build>
```

Or use it directly without adding to POM:

```bash
mvn io.github.tourem:descriptor-plugin:1.3.0:generate
```

### Basic Usage

Generate a deployment descriptor at your project root:

```bash
mvn io.github.tourem:descriptor-plugin:1.3.0:generate
```

This creates a `descriptor.json` file containing all deployment information.

## Usage (quick)

The most common commands at a glance:

```bash
# Default (descriptor.json at project root)
mvn io.github.tourem:descriptor-plugin:1.3.0:generate

# YAML or both JSON+YAML
mvn io.github.tourem:descriptor-plugin:1.3.0:generate -Ddescriptor.exportFormat=yaml
mvn io.github.tourem:descriptor-plugin:1.3.0:generate -Ddescriptor.exportFormat=both

# Generate an HTML page for non-technical stakeholders
mvn io.github.tourem:descriptor-plugin:1.3.0:generate -Ddescriptor.generateHtml=true

# Attach a ZIP artifact for repository deployment
mvn io.github.tourem:descriptor-plugin:1.3.0:generate -Ddescriptor.format=zip -Ddescriptor.attach=true

# Dry-run (print summary, no files)
mvn io.github.tourem:descriptor-plugin:1.3.0:generate -Ddescriptor.summary=true
```
### POM Configuration

Configure the plugin to run automatically during the build:

```xml
<build>
    <plugins>
        <plugin>
            <groupId>io.github.tourem</groupId>
            <artifactId>descriptor-plugin</artifactId>
            <version>1.3.0</version>
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


## Configuration Parameters

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



#### Build Metadata

The descriptor includes minimal build metadata (commit SHA, branch, CI info) for traceability. Use `jq` to extract fields from `descriptor.json` if needed.


### Framework Extensibility (SPI)

- Built-in detector: Spring Boot
- Extend by implementing `FrameworkDetector` and registering it via ServiceLoader in `META-INF/services/io.github.tourem.maven.descriptor.spi.FrameworkDetector`.
- See the source packages `io.github.tourem.maven.descriptor.spi` and `...framework` for examples.

### HTML Output

Generate a readable HTML report alongside the JSON/YAML descriptor:

```bash
mvn io.github.tourem:descriptor-plugin:1.3.0:generate -Ddescriptor.generateHtml=true
```

Screenshots:
- ![Descriptor HTML – Overview](images/html1.jpg)
- ![Descriptor HTML – Modules](images/html2.jpg)

### Dependency Tree Feature (optional)

Disabled by default to preserve backward compatibility. When enabled, the plugin collects dependencies for each deployable/executable module and exposes them in the JSON/YAML descriptor, plus an interactive section in the HTML report.

- Enable feature (CLI):
```
mvn io.github.tourem:descriptor-plugin:1.3.0:generate -Ddescriptor.includeDependencyTree=true
```
- Control depth (-1=unlimited, 0=direct only):
```
mvn ... -Ddescriptor.includeDependencyTree=true -Ddescriptor.dependencyTreeDepth=1
```
- Filter by scopes (CSV, default: compile,runtime):
```
mvn ... -Ddescriptor.includeDependencyTree=true -Ddescriptor.dependencyScopes=compile,runtime
```
- Choose format: flat | tree | both (default: flat)
```
mvn ... -Ddescriptor.includeDependencyTree=true -Ddescriptor.dependencyTreeFormat=both
```
- Exclude transitives / include optional flags:
```
mvn ... -Ddescriptor.includeDependencyTree=true -Ddescriptor.excludeTransitive=false -Ddescriptor.includeOptional=false
```

POM configuration example:

```
<plugin>
  <groupId>io.github.tourem</groupId>
  <artifactId>descriptor-plugin</artifactId>
  <version>1.3.0</version>
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
- First iteration collects direct dependencies declared in the POM; transitive resolution is planned in a subsequent iteration.
- The HTML report (when `-Ddescriptor.generateHtml=true`) adds an interactive Dependencies section inside each module card: search, scope/depth filters, view selector (Flat/Tree), CSV export, and duplicate detection.





## Requirements

- **Java**: 17 or higher
- **Maven**: 3.6.0 or higher

## Building from Source

```bash
mvn clean install
# optional
mvn test
```

## What Gets Detected

The plugin automatically analyzes your Maven project and detects:

- **Module Information**: Group ID, Artifact ID, Version, Packaging type
- **Spring Boot Applications**: Executables, main class, profiles
- **Environment Configurations**: Server port, context path, actuator settings
- **Actuator Endpoints**: Health, info, metrics endpoints
- **Maven Assembly**: Assembly descriptors, formats, repository paths
- **Build Plugins**: spring-boot-maven-plugin, maven-assembly-plugin
- **Container Images**: image coordinates (registry/group/name), tag(s), tool used (jib, spring-boot, fabric8, quarkus, micronaut, jkube), base or builder images when available

- **Dependency Tree (optional)**: Per executable module; summary (total/direct/transitive/scopes/optional) and details in flat and/or tree formats

Example snippet in descriptor.json for a module:

```json
{
  "container": {
    "tool": "jib",
    "image": "ghcr.io/acme/demo",
    "tag": "1.0.0",
    "additionalTags": ["latest"]
  }
}
```




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

