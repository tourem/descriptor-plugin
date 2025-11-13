# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

### Added
- Nothing yet.



## [2.3.0] - 2025-11-13

### Added
- Compliance tab UX:
  - Per-module “Expand all / Collapse all” controls that toggle both Warnings and License Details at once
  - Search filter by groupId/artifactId that filters Warnings (artifact column) and License Details (Group/Artifact columns)

### Fixed
- Upgrade JGit to 6.10.0 to eliminate `NoClassDefFoundError: org/eclipse/jgit/internal/JGitText` during JVM shutdown


## [2.2.0] - 2025-11-13

### Added
- Licenses and compliance (optional):
  - Parse POM licenses for direct and transitive dependencies
  - Configurable incompatible licenses set (default: GPL-3.0, AGPL-3.0, SSPL)
  - HTML Compliance view with pie chart, filters and warning badges (when enabled)
- Build properties and profiles (optional):
  - Grouped properties: project, maven, custom, system, environment (with masked count)
  - Sensitive-key filtering and masking controls; configurable extra patterns
  - Maven runtime info (version/home) and executed goals capture
  - HTML Build Info: Properties table with search filter; Maven Profiles (default/active/available)
- Build plugins (optional):
  - Effective plugins list with version, phase, goals, source; sanitized configuration
  - Plugin Management section with version and "Used in Build" indicator
  - Optional version update checks against Maven Central (best-effort, timeout configurable)
  - HTML Build Info: “Build Plugins” section with summary badges and tables

### Changed
- Build Info tab layout: moved “Build Plugins” section after “Maven Profiles” for a more natural reading flow
- Documentation: README updated to version 2.2.0 with new parameters sections (Licenses, Build Properties, Plugins)

## [2.1.0] - 2025-11-13

### Added
- HTML Dependencies tab: one section per deployable module with Flat/Tree views and collapsible nodes
- Search highlighting with <mark> and Previous/Next navigation with counter
- Quick filters by families: Spring, Jackson, Hibernate, Quarkus, Micronaut, Apache Commons, Guava, Netty, SLF4J, Logback, JUnit
- Colored scope badges in the tree (compile, runtime, test, provided, system, import)
- Expand All / Collapse All controls for the tree view

### Changed
- Hierarchical filtering: parents remain visible when a descendant matches; matching branches auto-expand during search
- Default Tree behavior: levels > 1 collapsed on load for readability
- Dedicated "Dependencies" top-level tab; dependency sections removed from "Modules" to avoid duplication
- Documentation: README updated to reference version 2.1.0 and reflect the new UI capabilities

### Fixed
- Tab switching bug that could leave “Dependencies”, “Environments” or “Assemblies” sections empty
- Transitive dependency search in the HTML Tree view now works correctly
- Minor HTML escaping and event-handler robustness improvements



## [2.0.0] - 2025-11-13

### Changed
- Project rename: descriptor-plugin ➜ deploy-manifest-plugin
  - Artifact coordinates: `io.github.tourem:deploy-manifest-plugin`
  - Module renames: `descriptor-core` ➜ `deploy-manifest-core`, `descriptor-plugin` ➜ `deploy-manifest-plugin`
  - Updated repository URLs, badges, and CI/CD workflows
  - README rewritten to hero style and updated to new name and coordinates
  - Note: Mojo class name and `descriptor.*` parameters are kept for backward compatibility (output files still default to `descriptor.json`)
- Documentation cleanup: removed internal working docs from the repo

### Added
- Dependency Tree for executable modules (optional, disabled by default)
  - JSON/YAML: `dependencies` section with `summary`, and either `flat` and/or `tree` according to configuration
  - HTML: interactive Dependencies section per module (search, scope/depth filters, Flat/Tree views, CSV export, duplicate detection)
  - Limitation: first iteration collects only direct dependencies declared in the POM; full transitive resolution planned next

### Removed
- Internal docs files moved out of the repo: `dependency-tree-feature-prompt.md`, `maven-docker-plugins.md`, `readme-hero-section.md`



#### 🚀 Advanced Features (Feature Branch: feature/advanced-features)

- **Git and CI/CD Metadata Collection**: Complete traceability and build reproducibility
  - `BuildInfo` record with comprehensive Git metadata
    - Git commit SHA (full and short), branch, tag, dirty state
    - Git remote URL, commit message, author, timestamp
  - CI/CD provider detection from environment variables
    - Supported: GitHub Actions, GitLab CI, Jenkins, Travis CI, CircleCI, Azure Pipelines
    - Captures: provider name, build ID, build number, build URL, job name, actor, event name
  - Build context metadata: timestamp, host, user
  - `GitInfoCollector` service using JGit (6.8.0.202311291450-r)
  - Automatic integration in descriptor JSON via `buildInfo` field

- **SPI for Framework Extensibility**: Plugin architecture for framework detection
  - `FrameworkDetector` SPI interface for pluggable framework detection
    - `getFrameworkName()`: Framework identifier
    - `isApplicable(Model, Path)`: Detection logic
    - `enrichModule(builder, model, modulePath, projectRoot)`: Metadata enrichment
    - `getPriority()`: Execution order control
  - `SpringBootFrameworkDetector`: Production-ready Spring Boot detector
    - Reuses existing Spring Boot detection services
    - Priority: 100 (high)
  - `QuarkusFrameworkDetector`: Example implementation for Quarkus
    - Detects Quarkus dependencies
    - Priority: 90
    - Ready for future extension
  - ServiceLoader configuration for automatic detector registration
  - Dynamic loading with priority-based execution
  - Enables extending plugin for Quarkus, Micronaut, Jakarta EE without core modifications

- **UX/DX Improvements**: Enhanced developer experience
  - **Dry-run/Summary Mode**: `-Ddescriptor.summary=true`
    - Prints ASCII dashboard to console without generating files
    - Shows project info, modules summary, deployable modules, build info
    - Perfect for quick project overview
  - **HTML Documentation Generation**: `-Ddescriptor.generateHtml=true`
    - Generates readable HTML page from descriptor
    - Modern design with CSS styling
    - Color-coded badges (JAR, WAR, Spring Boot)
    - Responsive layout for non-technical teams
  - **Post-Generation Hooks**: `-Ddescriptor.postGenerationHook=<command>`
    - Executes local script/command after descriptor generation
    - Use cases: file copying, notifications, validation scripts
    - Non-blocking: logs output, doesn't fail build on error

#### 🎁 Bonus Features

- **Multiple Export Formats**: Support for JSON, YAML, or both formats simultaneously
  - `exportFormat` parameter: `json`, `yaml`, or `both`
  - YAML export using Jackson YAML dataformat
- **Descriptor Validation**: Validate descriptor structure before generation
  - `validate` parameter: Basic validation of required fields
- **Digital Signature**: SHA-256 signature generation for integrity verification
  - `sign` parameter: Generates `.sha256` file with hash
  - Compatible with `shasum -a 256 -c` command
- **GZIP Compression**: Compress JSON files to reduce size
  - `compress` parameter: Generates `.gz` file
  - Shows compression ratio in logs
- **Webhook Notifications**: HTTP POST notifications after descriptor generation
  - `webhookUrl` parameter: Configurable HTTP endpoint
  - `webhookToken` parameter: Bearer token authentication
  - `webhookTimeout` parameter: Timeout in seconds (default: 10)
  - Sends full descriptor JSON in request body
  - Non-blocking: warnings on failure, doesn't stop build
- **Archive support**: Generate ZIP, TAR.GZ, TAR.BZ2, JAR archives of the descriptor JSON
- **Artifact attachment**: Attach descriptor archives to Maven project for deployment
- **Classifier support**: Customize artifact classifier (default: "descriptor")
- **Format parameter**: Specify archive format (zip, tar.gz, tar.bz2, jar)
- **Attach parameter**: Control whether to attach artifact to project
- **Maven repository deployment**: Deploy descriptor archives to Nexus/JFrog automatically
- GitHub Actions CI/CD workflow for automated releases to JFrog Artifactory
- Automated release workflow with manual trigger (workflow_dispatch)
- Automatic version management: next SNAPSHOT version calculation
- GitHub Release creation with release notes and Maven coordinates
- CI workflow for continuous integration (build and test on push/PR)
- Comprehensive release documentation in README.md
- Release best practices and troubleshooting guide
- Apache Commons Compress dependency for archive creation
- Jackson YAML dataformat dependency for YAML export
- Apache HttpClient 5 dependency for webhook notifications
- Eclipse JGit dependency for Git metadata extraction



### Changed
- Fixed date serialization to ISO-8601 format (e.g., "2025-11-09T00:47:09.317185") instead of array format
- Default output directory changed from project root to `${project.build.directory}` (target/)
- Enhanced logging with archive size and deployment information

### Technical Details - Release Workflow
- **Trigger**: Manual via GitHub Actions UI
- **Parameters**: Release version, JFrog URL, JFrog username, JFrog token
- **Version Logic**: Increments minor version for next SNAPSHOT (e.g., 1.0.0 → 1.1.0.0)
- **Deployment**: Artifacts deployed to JFrog Artifactory
- **Git Tags**: Automatic tag creation (e.g., v1.0.0)
- **GitHub Release**: Automatic creation with Maven coordinates and usage examples

## [1.3.0] - 2025-11-12

### Added
- Container image detection for deployable modules (new `container` field): Jib, Spring Boot build-image, Quarkus, Fabric8, Micronaut, JKube
- HTML: Container section displayed per deployable module (tool, image, tags, registry, base/builder/run, publish)
- Documentation: README updated with container example and usage


## [1.2.2] - 2025-11-12

### Fixed
- Archive creation now includes all generated files (JSON, YAML, HTML, JSON.gz) based on `exportFormat`, `generateHtml`, and `compress` options. Previously only the JSON file was archived.
- Added unit tests covering ZIP/JAR/TAR.GZ/TAR.BZ2 and additional combinations to prevent regressions.

### Documentation
- Updated README to reference version `1.2.2`



## [1.2.1] - 2025-11-11

### Changed

- GitHub Actions workflows now use JDK 17 (aligned with project requirements)
- GitHub release notes now synchronize the Features section dynamically from README.md
- Maven coordinates in release workflows updated from `com.larbotech` to `io.github.tourem`

### Fixed
- Added retry logic (3 attempts with backoff) in the Maven Central release workflow to mitigate transient HTTP 500 errors
- Added Maven Wagon HTTP retry handler configuration (`-Dmaven.wagon.http.retryHandler.count=5 -Dmaven.wagon.http.pool=false`)
- Made `versions:set`, build, and deploy steps resilient against Maven Central network issues

### Documentation
- Updated README, USAGE and feature guide to reference version `1.2.1`

## [1.2.0] - 2025-11-11

### Added
- Java 17 support across the build. Project compiles and runs with JDK 17.
- README section with HTML output examples (screenshots in `images/`).

### Changed
- Set `maven-compiler-plugin` and `maven.javadoc.plugin` source/target to 17.
- Documentation updated to use `1.2.0` in commands and snippets; CI examples use JDK 17.

### Fixed
- Replaced a Java 21-only pattern matching usage with Java 17-compatible code in `EnvironmentConfigDetector`.

## [1.1.0] - 2025-11-10

### Added
- Enhanced executable detection covering Spring Boot apps without `spring-boot-maven-plugin` and alternative executable patterns (shade, assembly, custom main class, WAR with embedded servers)
- New model: `ExecutableInfo` and `ExecutableType` with rich metadata exposed in the descriptor
- 16 unit tests for the detector and integration into module analysis
- Documentation: `CONTRIBUTING.md`, `CODE_OF_CONDUCT.md`, and feature guide updated

### Fixed
- Javadoc build errors with Lombok-generated types by configuring the Javadoc plugin to ignore source errors

### Notes
- No breaking changes; existing fields preserved. New `executableInfo` is optional and only present when applicable.


## [1.0.0] - 2025-11-09

### Added
- Initial release of the Descriptor Plugin
- Multi-module Maven project structure
- Core library (`deploy-manifest-core`) for Maven project analysis
- Maven plugin (`deploy-manifest-plugin`) for generating deployment manifests
- Automatic detection of deployable modules (JAR, WAR, EAR)
- Spring Boot executable detection
- Environment-specific configuration extraction (dev, hml, prod)
- Actuator endpoint discovery
- Maven Assembly artifact detection
- Deployment metadata extraction (Java version, main class, server ports)
- Configurable output file name and directory
- Pretty print JSON option
- Skip execution option

### Changed
- Renamed core library from `mavenflow-core` to `descriptor-core`
- Renamed plugin from `mavenflow-maven-plugin` to `descriptor-plugin`
- Changed system properties prefix from `mavenflow.*` to `descriptor.*`
- Removed Spring Boot dependencies from core library
- Converted to pure Java library with manual dependency injection

### Technical Details

#### Module Structure
```
deploy-manifest-parent/
├── deploy-manifest-core/         # Core analysis library
└── deploy-manifest-plugin/       # Maven plugin
```

#### Plugin Configuration
- **groupId**: `io.github.tourem`
- **artifactId**: `deploy-manifest-plugin`
- **version**: `1.0.0`

#### System Properties
- `descriptor.outputFile` - Output file name (default: `descriptor.json`)
- `descriptor.outputDirectory` - Output directory (default: project root)
- `descriptor.prettyPrint` - Pretty print JSON (default: `true`)
- `descriptor.skip` - Skip execution (default: `false`)

#### Usage
```bash
# Basic usage
mvn io.github.tourem:deploy-manifest-plugin:1.0.0:generate

# With custom output
mvn io.github.tourem:deploy-manifest-plugin:1.0.0:generate \
  -Ddescriptor.outputFile=deployment.json \
  -Ddescriptor.outputDirectory=target
```

### Requirements
- Java 21 or higher
- Maven 3.6.0 or higher

### Dependencies
- Jackson 2.15.3 (JSON serialization)
- Jackson JSR310 (Java 8 date/time support)
- Maven Plugin API 3.9.5
- Maven Core 3.9.5
- Lombok 1.18.30
- SnakeYAML 2.0 (YAML parsing)
- JUnit Jupiter 5.10.1 (testing)
- AssertJ 3.24.2 (testing)

### Known Issues
- None

### Future Enhancements
- Support for additional packaging types
- Docker configuration detection
- Kubernetes manifest generation
- Custom configuration profiles
- Plugin prefix registration for shorter commands

