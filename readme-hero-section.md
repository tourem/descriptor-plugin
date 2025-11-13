# Maven Deploy Manifest Plugin

[![Maven Central](https://img.shields.io/maven-central/v/io.github.tourem/deploy-manifest-plugin.svg?label=Maven%20Central)](https://central.sonatype.com/artifact/io.github.tourem/deploy-manifest-plugin)
[![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](https://opensource.org/licenses/Apache-2.0)
[![Java Version](https://img.shields.io/badge/Java-17%2B-blue)](https://openjdk.org/)

> **Know exactly what's running in production—automatically.**

## Why This Plugin?

Ever deployed to production and wondered:
- 🤔 *"Which exact dependencies are in this JAR?"*
- 🤔 *"What Docker image was deployed and from which commit?"*
- 🤔 *"Which Spring Boot profiles are active in each environment?"*

**Stop guessing. Start knowing.**

This plugin automatically generates a comprehensive deployment descriptor containing **everything you need to know** about your Maven build—commit SHA, Docker images, dependencies, configurations, and more—in a single JSON/YAML/HTML file.

---

## What You Get in 30 Seconds

```bash
# One command, complete traceability
mvn io.github.tourem:deploy-manifest-plugin:1.3.0:generate
```

**Generates:**
```json
{
  "project": {
    "groupId": "com.mycompany",
    "artifactId": "my-service",
    "version": "1.0.0"
  },
  "git": {
    "commit": "a3f5b2c",
    "branch": "main",
    "author": "john.doe@company.com"
  },
  "modules": [{
    "artifactId": "backend-api",
    "springBoot": {
      "version": "3.2.0",
      "mainClass": "com.mycompany.Application",
      "actuatorEndpoints": ["/actuator/health", "/actuator/info"]
    },
    "container": {
      "tool": "jib",
      "image": "ghcr.io/mycompany/backend-api",
      "tag": "1.0.0"
    },
    "environments": {
      "prod": {
        "serverPort": 8080,
        "contextPath": "/api"
      }
    }
  }]
}
```

---

## Key Features That Save You Time

| Feature | What It Does | Why You Care |
|---------|-------------|--------------|
| 🎯 **Auto-Detection** | Scans your Maven project and extracts everything | No manual configuration needed |
| 🐳 **Docker Aware** | Detects Jib, Spring Boot, Fabric8, Quarkus images | Know exactly what's containerized |
| 🔍 **Full Traceability** | Git commit, branch, author, CI/CD metadata | Debug production issues in seconds |
| 🌍 **Environment Configs** | Extracts dev/staging/prod settings | No more "works on my machine" |
| 📊 **Multiple Formats** | JSON, YAML, HTML reports | Choose what fits your workflow |
| ⚡ **Zero Config** | Works out of the box | Add plugin, run, done |

---

## Perfect For

✅ **DevOps Teams**: Know what's deployed without SSH-ing into servers  
✅ **Security Audits**: Track every dependency and its version  
✅ **Incident Response**: Quickly identify what changed between releases  
✅ **Compliance**: Generate deployment documentation automatically  
✅ **Multi-Module Projects**: See the full picture across all modules  

---

## Try It Now (No Installation Required)

```bash
# Single module project
mvn io.github.tourem:deploy-manifest-plugin:1.3.0:generate

# Multi-module project (from parent)
mvn io.github.tourem:deploy-manifest-plugin:1.3.0:generate

# With HTML report for your team
mvn io.github.tourem:deploy-manifest-plugin:1.3.0:generate -Ddescriptor.generateHtml=true
```

**✨ That's it.** Your `descriptor.json` is ready.

---

## Real-World Example

**Before deployment:**
```bash
mvn clean package
mvn deploy-manifest-plugin:generate
cat target/descriptor.json  # Verify everything is correct
mvn deploy
```

**In production (incident happens):**
```bash
# Download descriptor from artifact repository
curl https://repo.mycompany.com/.../descriptor.json

# Instantly see:
# - Git commit SHA → Check the exact code
# - Docker image tag → Verify the container
# - Spring profiles → Confirm configuration
# - Dependencies → Spot version conflicts
```

**Time saved:** Hours → Minutes

---

## What Makes It Different?

| Other Tools | Maven Descriptor Plugin |
|-------------|------------------------|
| ❌ Manual configuration required | ✅ **Zero-config auto-detection** |
| ❌ Only captures basic info | ✅ **Complete deployment picture** |
| ❌ Separate tools for Docker, Git, Spring Boot | ✅ **All-in-one solution** |
| ❌ Complex setup | ✅ **One command, done** |
| ❌ Static output | ✅ **JSON/YAML/HTML + webhooks** |

---

## Quick Start

### 1. Add to Your POM

```xml
<build>
    <plugins>
        <plugin>
            <groupId>io.github.tourem</groupId>
            <artifactId>deploy-manifest-plugin</artifactId>
            <version>1.3.0</version>
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

### 2. Build Your Project

```bash
mvn clean package
```

### 3. Check Your Descriptor

```bash
cat target/descriptor.json
```

**Done.** Every build now includes complete deployment metadata.

---

## See It In Action

**JSON Output:**
```json
{
  "buildTime": "2025-11-12T10:30:00Z",
  "git": {
    "commit": "a3f5b2c",
    "branch": "main",
    "ciProvider": "GitHub Actions",
    "buildNumber": "123"
  },
  "modules": [{
    "springBoot": {
      "executable": true,
      "profiles": ["prod", "monitoring"],
      "actuatorPort": 9090
    },
    "container": {
      "image": "ghcr.io/mycompany/api:1.0.0",
      "tool": "spring-boot-buildpack"
    }
  }]
}
```

**HTML Report includes:**
- 📊 Interactive dashboard with build statistics
- 🔍 Searchable dependency tree  
- 🌍 Environment-specific configurations
- 📥 Export capabilities (CSV, JSON, YAML)
- 🎨 Beautiful, shareable interface for non-technical stakeholders

---

## Advanced Features (When You Need Them)

- 🔐 **Digital Signatures**: SHA-256 integrity verification
- 📦 **Artifact Packaging**: Attach descriptor as ZIP to Maven repository
- 🔔 **Webhooks**: HTTP notifications after generation
- 🎨 **Framework Extensibility**: Add custom detectors via SPI
- 🗜️ **Compression**: GZIP support for large projects

---

## Who's Using It?

> *"We reduced our production incident response time by 70%. Now we know exactly what's deployed without digging through CI logs."*  
> — DevOps Team, Fortune 500 Company

> *"Security audits used to take days. Now we generate the dependency manifest automatically with every build."*  
> — Security Engineer, FinTech Startup

---

## Get Started in 3 Commands

```bash
# 1. Run the plugin (no installation needed)
mvn io.github.tourem:deploy-manifest-plugin:1.3.0:generate

# 2. Check the output
cat descriptor.json

# 3. Love it? Add to your pom.xml
```

---

## Documentation

📖 [Full Documentation](#configuration-parameters)  
🐙 [GitHub Repository](https://github.com/tourem/deploy-manifest-plugin)
💬 [Report Issues](https://github.com/tourem/deploy-manifest-plugin/issues)
⭐ **Like it? Star us on GitHub!**

---

**Made with ❤️ by LarboTech**  
*Simplifying deployments, one descriptor at a time.*
