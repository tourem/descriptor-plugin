package io.github.tourem.maven.descriptor.service;

import io.github.tourem.maven.descriptor.model.DeployableModule;
import io.github.tourem.maven.descriptor.model.ProjectDescriptor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Integration tests for MavenProjectAnalyzer.
 */
class MavenProjectAnalyzerIT {
    
    private MavenProjectAnalyzer analyzer;
    
    @TempDir
    Path tempDir;
    
    @BeforeEach
    void setUp() {
        analyzer = new MavenProjectAnalyzer();
    }
    
    @Test
    void shouldAnalyzeSimpleJarProject() throws IOException {
        // Create a simple Maven project
        Path projectDir = tempDir.resolve("simple-jar-project");
        Files.createDirectories(projectDir);
        
        String pomContent = """
            <?xml version="1.0" encoding="UTF-8"?>
            <project xmlns="http://maven.apache.org/POM/4.0.0"
                     xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
                     xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 
                     http://maven.apache.org/xsd/maven-4.0.0.xsd">
                <modelVersion>4.0.0</modelVersion>
                
                <groupId>com.test</groupId>
                <artifactId>simple-app</artifactId>
                <version>1.0.0</version>
                <packaging>jar</packaging>
                
                <name>Simple Application</name>
                <description>A simple test application</description>
            </project>
            """;
        
        Files.writeString(projectDir.resolve("pom.xml"), pomContent);
        
        // Analyze
        ProjectDescriptor descriptor = analyzer.analyzeProject(projectDir);
        
        // Verify
        assertThat(descriptor).isNotNull();
        assertThat(descriptor.projectGroupId()).isEqualTo("com.test");
        assertThat(descriptor.projectArtifactId()).isEqualTo("simple-app");
        assertThat(descriptor.projectVersion()).isEqualTo("1.0.0");
        assertThat(descriptor.projectName()).isEqualTo("Simple Application");
        assertThat(descriptor.totalModules()).isEqualTo(1);
        assertThat(descriptor.deployableModulesCount()).isEqualTo(1);
        assertThat(descriptor.deployableModules()).hasSize(1);
        
        DeployableModule module = descriptor.deployableModules().get(0);
        assertThat(module.getGroupId()).isEqualTo("com.test");
        assertThat(module.getArtifactId()).isEqualTo("simple-app");
        assertThat(module.getVersion()).isEqualTo("1.0.0");
        assertThat(module.getPackaging()).isEqualTo("jar");
        assertThat(module.getRepositoryPath()).isEqualTo("com/test/simple-app/1.0.0/simple-app-1.0.0.jar");
        assertThat(module.isSpringBootExecutable()).isFalse();
    }
    
    @Test
    void shouldAnalyzeSpringBootProject() throws IOException {
        Path projectDir = tempDir.resolve("spring-boot-project");
        Files.createDirectories(projectDir);
        
        String pomContent = """
            <?xml version="1.0" encoding="UTF-8"?>
            <project xmlns="http://maven.apache.org/POM/4.0.0"
                     xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
                     xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 
                     http://maven.apache.org/xsd/maven-4.0.0.xsd">
                <modelVersion>4.0.0</modelVersion>
                
                <groupId>com.larbotech</groupId>
                <artifactId>task</artifactId>
                <version>1.1.2-SNAPSHOT</version>
                <packaging>jar</packaging>
                
                <build>
                    <plugins>
                        <plugin>
                            <groupId>org.springframework.boot</groupId>
                            <artifactId>spring-boot-maven-plugin</artifactId>
                        </plugin>
                    </plugins>
                </build>
            </project>
            """;
        
        Files.writeString(projectDir.resolve("pom.xml"), pomContent);
        
        // Analyze
        ProjectDescriptor descriptor = analyzer.analyzeProject(projectDir);
        
        // Verify
        assertThat(descriptor.deployableModules()).hasSize(1);
        
        DeployableModule module = descriptor.deployableModules().get(0);
        assertThat(module.getGroupId()).isEqualTo("com.larbotech");
        assertThat(module.getArtifactId()).isEqualTo("task");
        assertThat(module.getVersion()).isEqualTo("1.1.2-SNAPSHOT");
        assertThat(module.getRepositoryPath()).isEqualTo("com/larbotech/task/1.1.2-SNAPSHOT/task-1.1.2-SNAPSHOT.jar");
        assertThat(module.isSpringBootExecutable()).isTrue();
    }
    
    @Test
    void shouldAnalyzeWarProject() throws IOException {
        Path projectDir = tempDir.resolve("war-project");
        Files.createDirectories(projectDir);
        
        String pomContent = """
            <?xml version="1.0" encoding="UTF-8"?>
            <project xmlns="http://maven.apache.org/POM/4.0.0"
                     xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
                     xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 
                     http://maven.apache.org/xsd/maven-4.0.0.xsd">
                <modelVersion>4.0.0</modelVersion>
                
                <groupId>com.example</groupId>
                <artifactId>webapp</artifactId>
                <version>2.0.0</version>
                <packaging>war</packaging>
            </project>
            """;
        
        Files.writeString(projectDir.resolve("pom.xml"), pomContent);
        
        // Analyze
        ProjectDescriptor descriptor = analyzer.analyzeProject(projectDir);
        
        // Verify
        assertThat(descriptor.deployableModules()).hasSize(1);
        
        DeployableModule module = descriptor.deployableModules().get(0);
        assertThat(module.getPackaging()).isEqualTo("war");
        assertThat(module.getRepositoryPath()).isEqualTo("com/example/webapp/2.0.0/webapp-2.0.0.war");
    }
    
    @Test
    void shouldNotIncludePomModules() throws IOException {
        Path projectDir = tempDir.resolve("pom-project");
        Files.createDirectories(projectDir);
        
        String pomContent = """
            <?xml version="1.0" encoding="UTF-8"?>
            <project xmlns="http://maven.apache.org/POM/4.0.0"
                     xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
                     xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 
                     http://maven.apache.org/xsd/maven-4.0.0.xsd">
                <modelVersion>4.0.0</modelVersion>
                
                <groupId>com.example</groupId>
                <artifactId>parent</artifactId>
                <version>1.0.0</version>
                <packaging>pom</packaging>
            </project>
            """;
        
        Files.writeString(projectDir.resolve("pom.xml"), pomContent);
        
        // Analyze
        ProjectDescriptor descriptor = analyzer.analyzeProject(projectDir);
        
        // Verify - POM packaging should not be deployable
        assertThat(descriptor.totalModules()).isEqualTo(1);
        assertThat(descriptor.deployableModulesCount()).isEqualTo(0);
        assertThat(descriptor.deployableModules()).isEmpty();
    }
    
    @Test
    void shouldThrowExceptionForInvalidPath() {
        Path invalidPath = tempDir.resolve("non-existent");
        
        assertThatThrownBy(() -> analyzer.analyzeProject(invalidPath))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Invalid project path");
    }
    
    @Test
    void shouldThrowExceptionWhenNoPomExists() throws IOException {
        Path projectDir = tempDir.resolve("no-pom");
        Files.createDirectories(projectDir);
        
        assertThatThrownBy(() -> analyzer.analyzeProject(projectDir))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("No pom.xml found");
    }
}

