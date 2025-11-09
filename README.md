# Descriptor Plugin

A Maven plugin that automatically generates comprehensive JSON deployment descriptors for your Maven projects, including Spring Boot applications, multi-module projects, and environment-specific configurations.

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
            <groupId>com.larbotech</groupId>
            <artifactId>descriptor-plugin</artifactId>
            <version>1.0-SNAPSHOT</version>
        </plugin>
    </plugins>
</build>
```

### Basic Usage

Generate a deployment descriptor at your project root:

```bash
mvn com.larbotech:descriptor-plugin:1.0-SNAPSHOT:generate
```

This creates a `descriptor.json` file containing all deployment information.

## Usage Examples

### Command Line

#### 1. Generate with default settings
```bash
mvn com.larbotech:descriptor-plugin:1.0-SNAPSHOT:generate
```
Output: `descriptor.json` at project root

#### 2. Custom output file name
```bash
mvn com.larbotech:descriptor-plugin:1.0-SNAPSHOT:generate \
  -Ddescriptor.outputFile=deployment-info.json
```

#### 3. Custom output directory
```bash
mvn com.larbotech:descriptor-plugin:1.0-SNAPSHOT:generate \
  -Ddescriptor.outputDirectory=target \
  -Ddescriptor.outputFile=deployment-descriptor.json
```

#### 4. Compact JSON (no pretty print)
```bash
mvn com.larbotech:descriptor-plugin:1.0-SNAPSHOT:generate \
  -Ddescriptor.prettyPrint=false
```

#### 5. Generate ZIP archive
```bash
mvn com.larbotech:descriptor-plugin:1.0-SNAPSHOT:generate \
  -Ddescriptor.format=zip
```
Output: `target/myapp-1.0-SNAPSHOT-descriptor.zip`

#### 6. Generate TAR.GZ archive with custom classifier
```bash
mvn com.larbotech:descriptor-plugin:1.0-SNAPSHOT:generate \
  -Ddescriptor.format=tar.gz \
  -Ddescriptor.classifier=deployment
```
Output: `target/myapp-1.0-SNAPSHOT-deployment.tar.gz`

#### 7. Generate and attach to project for deployment
```bash
mvn com.larbotech:descriptor-plugin:1.0-SNAPSHOT:generate \
  -Ddescriptor.format=zip \
  -Ddescriptor.attach=true
```
The artifact will be deployed to Maven repository during `mvn deploy`

#### 8. Generate YAML format
```bash
mvn com.larbotech:descriptor-plugin:1.0-SNAPSHOT:generate \
  -Ddescriptor.exportFormat=yaml
```
Output: `target/descriptor.yaml`

#### 9. Generate both JSON and YAML
```bash
mvn com.larbotech:descriptor-plugin:1.0-SNAPSHOT:generate \
  -Ddescriptor.exportFormat=both
```
Output: `target/descriptor.json` and `target/descriptor.yaml`

#### 10. Generate with validation and digital signature
```bash
mvn com.larbotech:descriptor-plugin:1.0-SNAPSHOT:generate \
  -Ddescriptor.validate=true \
  -Ddescriptor.sign=true
```
Output: `target/descriptor.json` and `target/descriptor.json.sha256`

#### 11. Generate with compression
```bash
mvn com.larbotech:descriptor-plugin:1.0-SNAPSHOT:generate \
  -Ddescriptor.compress=true
```
Output: `target/descriptor.json` and `target/descriptor.json.gz`

#### 12. Send webhook notification
```bash
mvn com.larbotech:descriptor-plugin:1.0-SNAPSHOT:generate \
  -Ddescriptor.webhookUrl=https://api.example.com/webhooks/descriptor \
  -Ddescriptor.webhookToken=your-secret-token
```
Sends HTTP POST with descriptor content to the specified URL

#### 13. Dry-run mode (preview without generating files)
```bash
mvn com.larbotech:descriptor-plugin:1.0-SNAPSHOT:generate \
  -Ddescriptor.summary=true
```
Displays an ASCII dashboard in the console with project overview

#### 14. Generate HTML documentation
```bash
mvn com.larbotech:descriptor-plugin:1.0-SNAPSHOT:generate \
  -Ddescriptor.generateHtml=true
```
Output: `target/descriptor.html` - Readable HTML page for non-technical teams

#### 15. Execute post-generation hook
```bash
mvn com.larbotech:descriptor-plugin:1.0-SNAPSHOT:generate \
  -Ddescriptor.postGenerationHook="./scripts/notify.sh"
```
Executes a local script/command after descriptor generation

#### 16. All features combined
```bash
mvn com.larbotech:descriptor-plugin:1.0-SNAPSHOT:generate \
  -Ddescriptor.exportFormat=both \
  -Ddescriptor.validate=true \
  -Ddescriptor.sign=true \
  -Ddescriptor.compress=true \
  -Ddescriptor.format=zip \
  -Ddescriptor.attach=true \
  -Ddescriptor.generateHtml=true \
  -Ddescriptor.webhookUrl=https://api.example.com/webhooks/descriptor \
  -Ddescriptor.postGenerationHook="echo 'Descriptor generated!'"
```

### POM Configuration

Configure the plugin to run automatically during the build:

```xml
<build>
    <plugins>
        <plugin>
            <groupId>com.larbotech</groupId>
            <artifactId>descriptor-plugin</artifactId>
            <version>1.0-SNAPSHOT</version>
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

### Archive Formats and Deployment

The plugin supports creating archives of the descriptor JSON file, similar to the `maven-assembly-plugin` behavior.

#### Supported Archive Formats

| Format | Extension | Description |
|--------|-----------|-------------|
| `zip` | `.zip` | ZIP archive (most common) |
| `jar` | `.zip` | JAR archive (same as ZIP) |
| `tar.gz` | `.tar.gz` | Gzipped TAR archive |
| `tgz` | `.tar.gz` | Alias for tar.gz |
| `tar.bz2` | `.tar.bz2` | Bzip2 compressed TAR archive |
| `tbz2` | `.tar.bz2` | Alias for tar.bz2 |

#### Archive Naming Convention

Archives follow Maven's standard naming convention:

```
{artifactId}-{version}-{classifier}.{extension}
```

Examples:
- `myapp-1.0.0-descriptor.zip`
- `myapp-1.0.0-deployment.tar.gz`
- `myapp-2.1.0-SNAPSHOT-descriptor.tar.bz2`

#### Deploying to Maven Repository

When `attach=true`, the descriptor archive is attached to the Maven project and will be:

1. **Installed** to local repository (`~/.m2/repository`) during `mvn install`
2. **Deployed** to remote repository (Nexus, JFrog, etc.) during `mvn deploy`

**Example workflow:**

```bash
# 1. Build project and generate descriptor archive
mvn clean package

# 2. Install to local repository
mvn install

# 3. Deploy to remote repository (Nexus/JFrog)
mvn deploy
```

The descriptor archive will be deployed alongside your main artifact:

```
Repository structure:
com/larbotech/myapp/1.0.0/
├── myapp-1.0.0.jar                    # Main artifact
├── myapp-1.0.0.pom                    # POM file
├── myapp-1.0.0-descriptor.zip         # Descriptor archive
└── myapp-1.0.0-sources.jar            # Sources (if configured)
```

#### Downloading Descriptor from Repository

Once deployed, you can download the descriptor from your Maven repository:

```bash
# Using Maven dependency plugin
mvn dependency:get \
  -Dartifact=com.larbotech:myapp:1.0.0:zip:descriptor \
  -Ddest=./descriptor.zip

# Or using curl (Nexus example)
curl -u user:password \
  https://nexus.example.com/repository/releases/com/larbotech/myapp/1.0.0/myapp-1.0.0-descriptor.zip \
  -o descriptor.zip
```

Then simply run:
```bash
mvn clean package
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

## Output Example

```json
{
  "projectGroupId": "com.example",
  "projectArtifactId": "my-application",
  "projectVersion": "1.0.0",
  "projectName": "My Application",
  "projectDescription": "Multi-module Spring Boot application",
  "generatedAt": "2025-11-09T14:20:48.083495",
  "deployableModules": [
    {
      "groupId": "com.example",
      "artifactId": "api-service",
      "version": "1.0.0",
      "packaging": "jar",
      "repositoryPath": "com/example/api-service/1.0.0/api-service-1.0.0.jar",
      "finalName": "api-service",
      "springBootExecutable": true,
      "modulePath": "api-service",
      "environments": [
        {
          "profile": "dev",
          "serverPort": 8080,
          "contextPath": "/api",
          "actuatorEnabled": true,
          "actuatorBasePath": "/actuator",
          "actuatorHealthPath": "/actuator/health",
          "actuatorInfoPath": "/actuator/info"
        }
      ],
      "assemblyArtifacts": [
        {
          "assemblyId": "distribution",
          "format": "zip",
          "repositoryPath": "com/example/api-service/1.0.0/api-service-1.0.0.zip"
        }
      ],
      "mainClass": "com.example.api.ApiApplication",
      "buildPlugins": ["spring-boot-maven-plugin", "maven-assembly-plugin"]
    }
  ],
  "totalModules": 5,
  "deployableModulesCount": 3,
  "buildInfo": {
    "gitCommitSha": "a6b5ba8f2c1d3e4f5a6b7c8d9e0f1a2b3c4d5e6f",
    "gitCommitShortSha": "a6b5ba8",
    "gitBranch": "feature/advanced-features",
    "gitDirty": false,
    "gitRemoteUrl": "https://github.com/user/my-application.git",
    "gitCommitMessage": "feat: Add advanced features",
    "gitCommitAuthor": "John Doe",
    "gitCommitTime": "2025-11-09T13:15:30",
    "buildTimestamp": "2025-11-09T14:20:48.083495",
    "buildHost": "build-server-01",
    "buildUser": "jenkins"
  }
}
```

> **Note**: The `buildInfo` section is **automatically collected** when the plugin runs. It includes Git metadata (commit, branch, author) and build information (timestamp, host, user). If running in a CI/CD environment (GitHub Actions, GitLab CI, Jenkins, etc.), additional CI metadata will be included.

## Advanced Features Guide

### 🔍 Git and CI/CD Metadata

The plugin **automatically collects** Git and CI/CD metadata for complete traceability. No configuration needed!

#### How It Works

When you run the plugin, it automatically:
1. ✅ Detects if the project is in a Git repository
2. ✅ Collects Git metadata (commit, branch, author, etc.)
3. ✅ Detects CI/CD environment variables
4. ✅ Adds all metadata to the `buildInfo` section of the descriptor

#### Git Metadata Collected

- **Commit SHA** (full and short 7-char version)
- **Branch name** (e.g., `main`, `develop`, `feature/xyz`)
- **Tag** (if the current commit is tagged, e.g., `v1.0.0`)
- **Dirty state** (whether there are uncommitted changes)
- **Remote URL** (e.g., `https://github.com/user/repo.git`)
- **Commit message** (last commit message)
- **Commit author** (name of the author)
- **Commit timestamp** (when the commit was made)

#### Build Metadata Collected

- **Build timestamp** (when the descriptor was generated)
- **Build host** (hostname of the machine running the build)
- **Build user** (username running the build)

#### CI/CD Providers Detected

The plugin automatically detects and collects metadata from:

| Provider | Environment Variables Used |
|----------|---------------------------|
| **GitHub Actions** | `GITHUB_ACTIONS`, `GITHUB_RUN_ID`, `GITHUB_RUN_NUMBER`, `GITHUB_WORKFLOW`, `GITHUB_ACTOR`, `GITHUB_EVENT_NAME`, `GITHUB_REPOSITORY` |
| **GitLab CI** | `GITLAB_CI`, `CI_PIPELINE_ID`, `CI_PIPELINE_IID`, `CI_PIPELINE_URL`, `CI_JOB_NAME`, `CI_COMMIT_REF_NAME`, `GITLAB_USER_LOGIN` |
| **Jenkins** | `JENKINS_URL`, `BUILD_ID`, `BUILD_NUMBER`, `BUILD_URL`, `JOB_NAME`, `GIT_BRANCH`, `BUILD_USER` |
| **Travis CI** | `TRAVIS`, `TRAVIS_BUILD_ID`, `TRAVIS_BUILD_NUMBER`, `TRAVIS_BUILD_WEB_URL`, `TRAVIS_JOB_NAME`, `TRAVIS_EVENT_TYPE` |
| **CircleCI** | `CIRCLECI`, `CIRCLE_BUILD_NUM`, `CIRCLE_BUILD_URL`, `CIRCLE_JOB`, `CIRCLE_USERNAME` |
| **Azure Pipelines** | `TF_BUILD`, `BUILD_BUILDID`, `BUILD_BUILDNUMBER`, `BUILD_DEFINITIONNAME`, `BUILD_REQUESTEDFOR` |

#### Example Output (Local Build)

```json
{
  "buildInfo": {
    "gitCommitSha": "a6b5ba8f2c1d3e4f5a6b7c8d9e0f1a2b3c4d5e6f",
    "gitCommitShortSha": "a6b5ba8",
    "gitBranch": "feature/advanced-features",
    "gitDirty": false,
    "gitRemoteUrl": "https://github.com/user/repo.git",
    "gitCommitMessage": "feat: Add advanced features",
    "gitCommitAuthor": "John Doe",
    "gitCommitTime": "2025-11-09T13:15:30",
    "buildTimestamp": "2025-11-09T14:20:48.083495",
    "buildHost": "macbook-pro.local",
    "buildUser": "johndoe"
  }
}
```

#### Example Output (GitHub Actions)

```json
{
  "buildInfo": {
    "gitCommitSha": "77e6c5e7e2b98b46a5601d81d6ecbe06b2b450cc",
    "gitCommitShortSha": "77e6c5e",
    "gitBranch": "main",
    "gitTag": "v1.0.0",
    "gitDirty": false,
    "gitRemoteUrl": "https://github.com/user/repo.git",
    "gitCommitMessage": "feat: Add new feature",
    "gitCommitAuthor": "John Doe",
    "gitCommitTime": "2025-11-09T12:13:37",
    "ciProvider": "GitHub Actions",
    "ciBuildId": "123456789",
    "ciBuildNumber": "42",
    "ciBuildUrl": "https://github.com/user/repo/actions/runs/123456789",
    "ciJobName": "build",
    "ciActor": "john-doe",
    "ciEventName": "push",
    "buildTimestamp": "2025-11-09T14:06:02.951024",
    "buildHost": "runner-xyz",
    "buildUser": "runner"
  }
}
```

#### Use Cases

**Traceability**: Know exactly which Git commit was used to build each artifact
```bash
# Extract commit SHA from descriptor
jq -r '.buildInfo.gitCommitSha' descriptor.json
# Output: a6b5ba8f2c1d3e4f5a6b7c8d9e0f1a2b3c4d5e6f
```

**Reproducibility**: Rebuild the exact same version
```bash
# Get the commit and rebuild
COMMIT=$(jq -r '.buildInfo.gitCommitSha' descriptor.json)
git checkout $COMMIT
mvn clean package
```

**Audit Trail**: Track who built what and when
```bash
# Show build information
jq '.buildInfo | {author: .gitCommitAuthor, timestamp: .buildTimestamp, host: .buildHost}' descriptor.json
```

### 🔌 Framework Extensibility (SPI)

The plugin uses a Service Provider Interface (SPI) for framework detection, making it easy to extend:

**Built-in Detectors:**
- **SpringBootFrameworkDetector**: Detects Spring Boot applications
- **QuarkusFrameworkDetector**: Example for Quarkus (ready for extension)

**Creating a Custom Detector:**

1. Implement the `FrameworkDetector` interface:
```java
public class MicronautFrameworkDetector implements FrameworkDetector {
    @Override
    public String getFrameworkName() {
        return "Micronaut";
    }

    @Override
    public boolean isApplicable(Model model, Path modulePath) {
        // Check for Micronaut dependencies
        return model.getDependencies().stream()
            .anyMatch(d -> d.getGroupId().equals("io.micronaut"));
    }

    @Override
    public void enrichModule(DeployableModule.DeployableModuleBuilder builder,
                            Model model, Path modulePath, Path projectRoot) {
        // Add Micronaut-specific metadata
        builder.mainClass(detectMainClass(model));
    }

    @Override
    public int getPriority() {
        return 80; // Execution priority
    }
}
```

2. Register via ServiceLoader in `META-INF/services/com.larbotech.maven.descriptor.spi.FrameworkDetector`:
```
com.example.MicronautFrameworkDetector
```

### 🎨 UX/DX Improvements

#### Dry-Run Mode

Preview the descriptor without generating files:

```bash
mvn descriptor:generate -Ddescriptor.summary=true
```

**Output:**
```
╔═══════════════════════════════════════════════════════════════════════╗
║                    DESCRIPTOR SUMMARY (DRY-RUN)                       ║
╚═══════════════════════════════════════════════════════════════════════╝

  Project: My Application
  Group ID: com.example
  Artifact ID: my-app
  Version: 1.0.0
  Generated At: 2025-11-09T14:05:39.037985

┌───────────────────────────────────────────────────────────────────────┐
│ MODULES SUMMARY                                                       │
├───────────────────────────────────────────────────────────────────────┤
│ Total Modules:      5                                                 │
│ Deployable Modules: 3                                                 │
└───────────────────────────────────────────────────────────────────────┘

┌───────────────────────────────────────────────────────────────────────┐
│ DEPLOYABLE MODULES                                                    │
├───────────────────────────────────────────────────────────────────────┤
│ • api-service (jar)
│   Path: com/example/api-service/1.0.0/api-service-1.0.0.jar
│   Type: Spring Boot Executable
│   Main Class: com.example.api.ApiApplication
│   Environments: 3
└───────────────────────────────────────────────────────────────────────┘
```

#### HTML Documentation

Generate a readable HTML page for non-technical teams:

```bash
mvn descriptor:generate -Ddescriptor.generateHtml=true
```

Creates `target/descriptor.html` with:
- Modern, responsive design
- Color-coded badges (JAR, WAR, Spring Boot)
- Project information grid
- Deployable modules list
- Build information

#### Post-Generation Hooks

Execute custom scripts after descriptor generation:

```bash
# Simple notification
mvn descriptor:generate \
  -Ddescriptor.postGenerationHook="echo 'Descriptor generated!'"

# Copy to deployment directory
mvn descriptor:generate \
  -Ddescriptor.postGenerationHook="cp target/descriptor.json /deploy/"

# Execute custom script
mvn descriptor:generate \
  -Ddescriptor.postGenerationHook="./scripts/process-descriptor.sh"
```

**Use Cases:**
- Copy descriptor to shared location
- Send local notifications
- Trigger validation scripts
- Generate derived files
- Update configuration management systems

## Use Cases

### CI/CD Integration

#### GitHub Actions
```yaml
name: Deploy

on:
  push:
    branches: [main]

jobs:
  deploy:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v3
      
      - name: Set up JDK 21
        uses: actions/setup-java@v3
        with:
          java-version: '21'
          
      - name: Build and generate descriptor
        run: mvn clean package
        
      - name: Parse and deploy
        run: |
          MODULES=$(jq -r '.deployableModules[] | select(.springBootExecutable == true) | .artifactId' target/descriptor.json)
          for module in $MODULES; do
            echo "Deploying $module..."
            # Your deployment logic here
          done
```

#### GitLab CI
```yaml
deploy:
  stage: deploy
  script:
    - mvn com.larbotech:descriptor-plugin:1.0-SNAPSHOT:generate
    - |
      jq -r '.deployableModules[]' descriptor.json | while read -r module; do
        echo "Processing module: $module"
        # Deployment logic
      done
```

### Deployment Scripts

```bash
#!/bin/bash
# deploy.sh - Automated deployment script

# Generate deployment descriptor
mvn com.larbotech:descriptor-plugin:1.0-SNAPSHOT:generate

# Extract deployable modules
MODULES=$(jq -r '.deployableModules[] | select(.springBootExecutable == true)' descriptor.json)

# Deploy each module
echo "$MODULES" | jq -c '.' | while read -r module; do
    ARTIFACT_ID=$(echo "$module" | jq -r '.artifactId')
    REPO_PATH=$(echo "$module" | jq -r '.repositoryPath')
    MAIN_CLASS=$(echo "$module" | jq -r '.mainClass')
    
    echo "Deploying $ARTIFACT_ID..."
    echo "  Repository path: $REPO_PATH"
    echo "  Main class: $MAIN_CLASS"
    
    # Your deployment commands here
    # scp, kubectl, docker, etc.
done
```

## Project Structure

```
descriptor-parent/
├── descriptor-core/             # Core library
│   └── src/main/java/
│       └── com/larbotech/maven/descriptor/
│           ├── model/           # Data models
│           └── service/         # Analysis services
│
└── descriptor-plugin/           # Maven plugin
    └── src/main/java/
        └── com/larbotech/maven/plugin/
            └── GenerateDescriptorMojo.java
```

## Requirements

- **Java**: 21 or higher
- **Maven**: 3.6.0 or higher

## Building from Source

```bash
# Clone the repository
git clone https://github.com/larbotech/mavenflow.git
cd mavenflow

# Build and install
mvn clean install

# Run tests
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

## Release Process

### Overview

The project uses GitHub Actions for automated releases to JFrog Artifactory. The release workflow:

1. ✅ Builds and tests the project
2. ✅ Sets the release version
3. ✅ Deploys artifacts to JFrog Artifactory
4. ✅ Creates a Git tag
5. ✅ Automatically calculates and sets the next SNAPSHOT version
6. ✅ Creates a GitHub Release

### Automatic Version Management

The release workflow automatically calculates the next SNAPSHOT version based on the release version:

| Release Version | Next SNAPSHOT Version |
|----------------|----------------------|
| `1.0.0` | `1.1.0-SNAPSHOT` |
| `1.5.0` | `1.6.0-SNAPSHOT` |
| `2.0.0` | `2.1.0-SNAPSHOT` |
| `2.3.5` | `2.4.0-SNAPSHOT` |

**Logic**: The workflow increments the **minor version** and resets the patch to 0.

### How to Create a Release

#### Prerequisites

1. **JFrog Artifactory Access**:
   - JFrog Artifactory URL (e.g., `https://myjfrog.com/artifactory`)
   - JFrog username
   - JFrog API token or password

2. **GitHub Permissions**:
   - Write access to the repository
   - Ability to trigger GitHub Actions workflows

#### Step-by-Step Release Process

1. **Navigate to GitHub Actions**:
   - Go to your repository on GitHub
   - Click on the **Actions** tab
   - Select **Release Descriptor Plugin** workflow

2. **Trigger the Release**:
   - Click **Run workflow** button
   - Fill in the required parameters:

   | Parameter | Description | Example |
   |-----------|-------------|---------|
   | **Release version** | The version to release (X.Y.Z format) | `1.0.0` |
   | **JFrog Artifactory URL** | Your JFrog instance URL | `https://myjfrog.com/artifactory` |
   | **JFrog username** | Your JFrog username | `admin` |
   | **JFrog token** | Your JFrog API token or password | `AKCp...` |

3. **Monitor the Release**:
   - The workflow will execute automatically
   - Monitor the progress in the Actions tab
   - Check for any errors in the logs

4. **Verify the Release**:
   - Check JFrog Artifactory for the deployed artifacts
   - Verify the Git tag was created: `v1.0.0`
   - Check the GitHub Releases page for the new release
   - Confirm the repository is now on the next SNAPSHOT version

### Release Workflow Details

The GitHub Actions workflow performs the following steps:

```yaml
# 1. Checkout code
# 2. Set up JDK 21
# 3. Calculate next SNAPSHOT version (e.g., 1.0.0 → 1.1.0-SNAPSHOT)
# 4. Set release version in POMs
# 5. Build and test the project
# 6. Configure Maven settings for JFrog
# 7. Deploy to JFrog Artifactory
# 8. Commit release version and create Git tag
# 9. Set next SNAPSHOT version in POMs
# 10. Commit next SNAPSHOT version
# 11. Push changes and tags to GitHub
# 12. Create GitHub Release
```

### Using Released Versions

After a successful release, you can use the plugin in your projects:

#### Maven Dependency
```xml
<dependency>
    <groupId>com.larbotech</groupId>
    <artifactId>descriptor-plugin</artifactId>
    <version>1.0.0</version>
</dependency>
```

#### Maven Plugin
```xml
<build>
    <plugins>
        <plugin>
            <groupId>com.larbotech</groupId>
            <artifactId>descriptor-plugin</artifactId>
            <version>1.0.0</version>
        </plugin>
    </plugins>
</build>
```

#### Command Line
```bash
mvn com.larbotech:descriptor-plugin:1.0.0:generate
```

### Configuring Your Maven Settings

To use artifacts from your JFrog Artifactory, add this to your `~/.m2/settings.xml`:

```xml
<settings>
    <servers>
        <server>
            <id>jfrog-releases</id>
            <username>YOUR_JFROG_USERNAME</username>
            <password>YOUR_JFROG_TOKEN</password>
        </server>
    </servers>

    <profiles>
        <profile>
            <id>jfrog</id>
            <repositories>
                <repository>
                    <id>jfrog-releases</id>
                    <url>https://myjfrog.com/artifactory/libs-release-local</url>
                    <releases>
                        <enabled>true</enabled>
                    </releases>
                    <snapshots>
                        <enabled>false</enabled>
                    </snapshots>
                </repository>
            </repositories>
            <pluginRepositories>
                <pluginRepository>
                    <id>jfrog-releases</id>
                    <url>https://myjfrog.com/artifactory/libs-release-local</url>
                    <releases>
                        <enabled>true</enabled>
                    </releases>
                    <snapshots>
                        <enabled>false</enabled>
                    </snapshots>
                </pluginRepository>
            </pluginRepositories>
        </profile>
    </profiles>

    <activeProfiles>
        <activeProfile>jfrog</activeProfile>
    </activeProfiles>
</settings>
```

### Release Best Practices

1. **Version Numbering**:
   - Follow [Semantic Versioning](https://semver.org/): `MAJOR.MINOR.PATCH`
   - Increment MAJOR for breaking changes
   - Increment MINOR for new features (backward compatible)
   - Increment PATCH for bug fixes

2. **Before Releasing**:
   - ✅ Ensure all tests pass
   - ✅ Update CHANGELOG.md with release notes
   - ✅ Review and merge all pending PRs
   - ✅ Verify the current SNAPSHOT version builds successfully

3. **After Releasing**:
   - ✅ Verify artifacts in JFrog Artifactory
   - ✅ Test the released version in a sample project
   - ✅ Update documentation if needed
   - ✅ Announce the release to your team

### Troubleshooting Releases

#### Release workflow fails at deployment
- **Cause**: Invalid JFrog credentials or URL
- **Solution**: Verify your JFrog username, token, and URL are correct

#### Version already exists in Artifactory
- **Cause**: Trying to release a version that already exists
- **Solution**: Use a different version number or delete the existing version in Artifactory

#### Git push fails
- **Cause**: Insufficient permissions or branch protection rules
- **Solution**: Ensure the GitHub Actions bot has write permissions and branch protection allows pushes from workflows

#### Tests fail during release
- **Cause**: Code issues or flaky tests
- **Solution**: Fix the failing tests before attempting the release again

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

