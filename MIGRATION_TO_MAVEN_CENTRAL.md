# Migration to Maven Central - Summary

This document summarizes all changes made to migrate the project from `com.larbotech` to `io.github.tourem` for publication on Maven Central.

## 📋 Overview

**Date:** 2025-11-09  
**Migration Type:** GroupId change + Maven Central preparation  
**Old GroupId:** `com.larbotech`  
**New GroupId:** `io.github.tourem`  
**Old Version:** `1.0-SNAPSHOT`  
**New Version:** `1.0.0-SNAPSHOT`

---

## 🔄 Changes Made

### 1. POM Files Updated

#### Parent POM (`pom.xml`)
- ✅ Changed `groupId` from `com.larbotech` to `io.github.tourem`
- ✅ Changed `version` from `1.0-SNAPSHOT` to `1.0.0-SNAPSHOT`
- ✅ Added Maven Central required metadata:
  - Project URL: `https://github.com/tourem/descriptor-plugin`
  - License: Apache License 2.0
  - Developers: Mamadou Touré
  - SCM: GitHub repository information
  - Issue Management: GitHub Issues
- ✅ Configured plugins for Maven Central:
  - `maven-source-plugin` - Generate sources JAR
  - `maven-javadoc-plugin` - Generate javadoc JAR (with Lombok error suppression)
  - `maven-gpg-plugin` - Sign artifacts with GPG
  - `central-publishing-maven-plugin` - Deploy to Sonatype Central Portal
- ✅ Created `release` profile for GPG signing

#### Module POMs
- ✅ `descriptor-core/pom.xml` - Updated parent groupId and version
- ✅ `descriptor-plugin/pom.xml` - Updated parent groupId, version, and dependency groupId

### 2. Java Package Renaming

All Java packages renamed from `com.larbotech.maven.descriptor.*` to `io.github.tourem.maven.descriptor.*`:

**Source Files:**
- ✅ `descriptor-core/src/main/java/**/*.java` - 18 files
- ✅ `descriptor-core/src/test/java/**/*.java` - 13 files
- ✅ `descriptor-plugin/src/main/java/**/*.java` - 1 file

**Directory Structure:**
```
OLD: src/main/java/com/larbotech/maven/descriptor/
NEW: src/main/java/io/github/tourem/maven/descriptor/
```

**Packages Renamed:**
- `com.larbotech.maven.descriptor.model` → `io.github.tourem.maven.descriptor.model`
- `com.larbotech.maven.descriptor.service` → `io.github.tourem.maven.descriptor.service`
- `com.larbotech.maven.descriptor.spi` → `io.github.tourem.maven.descriptor.spi`
- `com.larbotech.maven.descriptor.spi.impl` → `io.github.tourem.maven.descriptor.spi.impl`
- `com.larbotech.maven.plugin` → `io.github.tourem.maven.plugin`

### 3. ServiceLoader Configuration

- ✅ Renamed: `META-INF/services/com.larbotech.maven.descriptor.spi.FrameworkDetector`
- ✅ To: `META-INF/services/io.github.tourem.maven.descriptor.spi.FrameworkDetector`
- ✅ Updated content to reference new package names

### 4. Configuration Files

- ✅ `descriptor-core/src/main/resources/application.yml` - Updated logging package from `com.larbotech` to `io.github.tourem`

### 5. Documentation Updated

- ✅ `README.md` - Updated all Maven coordinates and examples
  - Added Maven Central badge
  - Updated installation instructions
  - Updated all command-line examples
  - Updated POM configuration examples
- ✅ `CHANGELOG.md` - Updated all references to new groupId
- ✅ `USAGE.md` - Updated all examples and references
- ✅ Created `MAVEN_CENTRAL_RELEASE.md` - Complete release guide
- ✅ Created `.github/SECRETS_SETUP.md` - GitHub Secrets configuration guide
- ✅ Deleted `RELEASE.md` - Replaced by MAVEN_CENTRAL_RELEASE.md

### 6. GitHub Actions Workflows

- ✅ Created `.github/workflows/maven-central-release.yml` - Automated release workflow
  - Manual trigger with version input
  - GPG signing from GitHub Secrets
  - Deploy to Maven Central
  - Create Git tag and GitHub Release
  - Bump to next SNAPSHOT version

### 7. Old Workflow (Not Modified)

- ⚠️ `.github/workflows/release.yml` - Still references old groupId (for JFrog Artifactory)
  - **Note:** This workflow may need to be deprecated or updated in the future

---

## 🧪 Verification

### Build Verification
```bash
mvn clean install -DskipTests -B
```
**Result:** ✅ SUCCESS

### Test Verification
```bash
mvn clean test -B
```
**Result:** ✅ SUCCESS (117 tests passed)

### Plugin Execution Test
```bash
mvn io.github.tourem:descriptor-plugin:1.0.0-SNAPSHOT:generate
```
**Result:** ✅ SUCCESS - Descriptor generated correctly

---

## 📦 New Maven Coordinates

### Plugin Usage

**Command Line:**
```bash
mvn io.github.tourem:descriptor-plugin:1.0.0:generate
```

**POM Configuration:**
```xml
<build>
    <plugins>
        <plugin>
            <groupId>io.github.tourem</groupId>
            <artifactId>descriptor-plugin</artifactId>
            <version>1.0.0</version>
        </plugin>
    </plugins>
</build>
```

**Dependency:**
```xml
<dependency>
    <groupId>io.github.tourem</groupId>
    <artifactId>descriptor-core</artifactId>
    <version>1.0.0</version>
</dependency>
```

---

## 🔐 Required GitHub Secrets

Before releasing to Maven Central, configure these secrets in GitHub:

1. **SONATYPE_USERNAME** - Sonatype Central username (from generated token)
2. **SONATYPE_TOKEN** - Sonatype Central password (from generated token)
3. **GPG_PRIVATE_KEY** - GPG private key for signing artifacts
4. **GPG_PASSPHRASE** - Passphrase for GPG key

See `.github/SECRETS_SETUP.md` for detailed setup instructions.

---

## 🚀 Release Process

### 1. Prepare for Release

Ensure all changes are committed and pushed:
```bash
git add -A
git commit -m "chore: Migrate to io.github.tourem for Maven Central publication"
git push origin feature/advanced-features
```

### 2. Trigger Release

1. Go to GitHub Actions: https://github.com/tourem/descriptor-plugin/actions
2. Select "Maven Central Release" workflow
3. Click "Run workflow"
4. Enter version (e.g., `1.0.0`)
5. Click "Run workflow"

### 3. Verify Release

After successful workflow execution:

1. **Check Maven Central:**
   - https://central.sonatype.com/artifact/io.github.tourem/descriptor-plugin

2. **Verify artifacts:**
   ```bash
   mvn dependency:get -Dartifact=io.github.tourem:descriptor-plugin:1.0.0
   ```

3. **Test the released plugin:**
   ```bash
   mvn io.github.tourem:descriptor-plugin:1.0.0:generate
   ```

---

## 📊 Migration Statistics

- **Files Modified:** 45
- **Java Files Moved:** 32
- **Packages Renamed:** 5
- **Documentation Updated:** 4 files
- **New Files Created:** 3
- **Build Status:** ✅ SUCCESS
- **Tests Status:** ✅ 117/117 PASSED

---

## ⚠️ Breaking Changes

### For Users

If you were using the old groupId `com.larbotech`, you need to update:

**Old:**
```xml
<groupId>com.larbotech</groupId>
<artifactId>descriptor-plugin</artifactId>
<version>1.0-SNAPSHOT</version>
```

**New:**
```xml
<groupId>io.github.tourem</groupId>
<artifactId>descriptor-plugin</artifactId>
<version>1.0.0</version>
```

**Old Command:**
```bash
mvn com.larbotech:descriptor-plugin:1.0-SNAPSHOT:generate
```

**New Command:**
```bash
mvn io.github.tourem:descriptor-plugin:1.0.0:generate
```

### For Developers/Contributors

If you have custom framework detectors using the SPI:

**Old:**
```
META-INF/services/com.larbotech.maven.descriptor.spi.FrameworkDetector
```

**New:**
```
META-INF/services/io.github.tourem.maven.descriptor.spi.FrameworkDetector
```

**Old Import:**
```java
import com.larbotech.maven.descriptor.spi.FrameworkDetector;
```

**New Import:**
```java
import io.github.tourem.maven.descriptor.spi.FrameworkDetector;
```

---

## 📝 Next Steps

1. ✅ **Configure GitHub Secrets** (see `.github/SECRETS_SETUP.md`)
2. ✅ **Merge feature branch** to main
3. ✅ **Trigger first release** (version 1.0.0)
4. ✅ **Verify publication** on Maven Central
5. ✅ **Update project documentation** with Maven Central links
6. ✅ **Announce release** to users

---

## 🔗 Useful Links

- **Maven Central Portal:** https://central.sonatype.com/
- **GitHub Repository:** https://github.com/tourem/descriptor-plugin
- **Maven Central Publishing Guide:** https://central.sonatype.org/publish/publish-guide/
- **GPG Setup Guide:** https://central.sonatype.org/publish/requirements/gpg/

---

## ✅ Checklist

- [x] Update all POM files with new groupId
- [x] Rename all Java packages
- [x] Update ServiceLoader configuration
- [x] Update configuration files
- [x] Update documentation (README, CHANGELOG, USAGE)
- [x] Create Maven Central release workflow
- [x] Create GitHub Secrets setup guide
- [x] Verify build succeeds
- [x] Verify all tests pass
- [x] Verify plugin execution works
- [ ] Configure GitHub Secrets
- [ ] Merge to main branch
- [ ] Trigger first release
- [ ] Verify Maven Central publication

---

**Migration completed successfully! 🎉**

