# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

### Added

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
- **Version Logic**: Increments minor version for next SNAPSHOT (e.g., 1.0.0 → 1.1.0-SNAPSHOT)
- **Deployment**: Artifacts deployed to JFrog Artifactory
- **Git Tags**: Automatic tag creation (e.g., v1.0.0)
- **GitHub Release**: Automatic creation with Maven coordinates and usage examples

## [1.0-SNAPSHOT] - 2025-11-09

### Added
- Initial release of the Descriptor Plugin
- Multi-module Maven project structure
- Core library (`descriptor-core`) for Maven project analysis
- Maven plugin (`descriptor-plugin`) for generating deployment descriptors
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
descriptor-parent/
├── descriptor-core/         # Core analysis library
└── descriptor-plugin/       # Maven plugin
```

#### Plugin Configuration
- **groupId**: `com.larbotech`
- **artifactId**: `descriptor-plugin`
- **version**: `1.0-SNAPSHOT`

#### System Properties
- `descriptor.outputFile` - Output file name (default: `descriptor.json`)
- `descriptor.outputDirectory` - Output directory (default: project root)
- `descriptor.prettyPrint` - Pretty print JSON (default: `true`)
- `descriptor.skip` - Skip execution (default: `false`)

#### Usage
```bash
# Basic usage
mvn com.larbotech:descriptor-plugin:1.0-SNAPSHOT:generate

# With custom output
mvn com.larbotech:descriptor-plugin:1.0-SNAPSHOT:generate \
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

