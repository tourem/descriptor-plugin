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

/**
 * Integration tests for multi-module Maven projects.
 */
class MultiModuleProjectIT {
    
    private MavenProjectAnalyzer analyzer;
    
    @TempDir
    Path tempDir;
    
    @BeforeEach
    void setUp() {
        analyzer = new MavenProjectAnalyzer();
    }
    
    @Test
    void shouldAnalyzeMultiModuleProject() throws IOException {
        // Create parent project
        Path projectDir = tempDir.resolve("multi-module-project");
        Files.createDirectories(projectDir);
        
        String parentPom = """
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
                
                <modules>
                    <module>module-api</module>
                    <module>module-service</module>
                    <module>module-web</module>
                </modules>
            </project>
            """;
        
        Files.writeString(projectDir.resolve("pom.xml"), parentPom);
        
        // Create module-api (JAR)
        Path apiModule = projectDir.resolve("module-api");
        Files.createDirectories(apiModule);
        String apiPom = """
            <?xml version="1.0" encoding="UTF-8"?>
            <project xmlns="http://maven.apache.org/POM/4.0.0"
                     xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
                     xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 
                     http://maven.apache.org/xsd/maven-4.0.0.xsd">
                <modelVersion>4.0.0</modelVersion>
                
                <parent>
                    <groupId>com.example</groupId>
                    <artifactId>parent</artifactId>
                    <version>1.0.0</version>
                </parent>
                
                <artifactId>module-api</artifactId>
                <packaging>jar</packaging>
            </project>
            """;
        Files.writeString(apiModule.resolve("pom.xml"), apiPom);
        
        // Create module-service (Spring Boot JAR)
        Path serviceModule = projectDir.resolve("module-service");
        Files.createDirectories(serviceModule);
        String servicePom = """
            <?xml version="1.0" encoding="UTF-8"?>
            <project xmlns="http://maven.apache.org/POM/4.0.0"
                     xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
                     xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 
                     http://maven.apache.org/xsd/maven-4.0.0.xsd">
                <modelVersion>4.0.0</modelVersion>
                
                <parent>
                    <groupId>com.example</groupId>
                    <artifactId>parent</artifactId>
                    <version>1.0.0</version>
                </parent>
                
                <artifactId>module-service</artifactId>
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
        Files.writeString(serviceModule.resolve("pom.xml"), servicePom);
        
        // Create module-web (WAR)
        Path webModule = projectDir.resolve("module-web");
        Files.createDirectories(webModule);
        String webPom = """
            <?xml version="1.0" encoding="UTF-8"?>
            <project xmlns="http://maven.apache.org/POM/4.0.0"
                     xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
                     xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 
                     http://maven.apache.org/xsd/maven-4.0.0.xsd">
                <modelVersion>4.0.0</modelVersion>
                
                <parent>
                    <groupId>com.example</groupId>
                    <artifactId>parent</artifactId>
                    <version>1.0.0</version>
                </parent>
                
                <artifactId>module-web</artifactId>
                <packaging>war</packaging>
            </project>
            """;
        Files.writeString(webModule.resolve("pom.xml"), webPom);
        
        // Analyze
        ProjectDescriptor descriptor = analyzer.analyzeProject(projectDir);
        
        // Verify
        assertThat(descriptor).isNotNull();
        assertThat(descriptor.projectGroupId()).isEqualTo("com.example");
        assertThat(descriptor.projectArtifactId()).isEqualTo("parent");
        assertThat(descriptor.projectVersion()).isEqualTo("1.0.0");
        
        // Parent is POM (not deployable) + 3 modules = 4 total
        assertThat(descriptor.totalModules()).isEqualTo(4);
        
        // Only 3 modules are deployable (parent is POM)
        assertThat(descriptor.deployableModulesCount()).isEqualTo(3);
        assertThat(descriptor.deployableModules()).hasSize(3);
        
        // Verify each module
        DeployableModule apiModuleDesc = descriptor.deployableModules().stream()
            .filter(m -> m.getArtifactId().equals("module-api"))
            .findFirst()
            .orElseThrow();
        
        assertThat(apiModuleDesc.getGroupId()).isEqualTo("com.example");
        assertThat(apiModuleDesc.getVersion()).isEqualTo("1.0.0");
        assertThat(apiModuleDesc.getPackaging()).isEqualTo("jar");
        assertThat(apiModuleDesc.isSpringBootExecutable()).isFalse();
        assertThat(apiModuleDesc.getRepositoryPath()).isEqualTo("com/example/module-api/1.0.0/module-api-1.0.0.jar");
        assertThat(apiModuleDesc.getModulePath()).isEqualTo("module-api");
        
        DeployableModule serviceModuleDesc = descriptor.deployableModules().stream()
            .filter(m -> m.getArtifactId().equals("module-service"))
            .findFirst()
            .orElseThrow();
        
        assertThat(serviceModuleDesc.getPackaging()).isEqualTo("jar");
        assertThat(serviceModuleDesc.isSpringBootExecutable()).isTrue();
        assertThat(serviceModuleDesc.getRepositoryPath()).isEqualTo("com/example/module-service/1.0.0/module-service-1.0.0.jar");
        assertThat(serviceModuleDesc.getModulePath()).isEqualTo("module-service");
        
        DeployableModule webModuleDesc = descriptor.deployableModules().stream()
            .filter(m -> m.getArtifactId().equals("module-web"))
            .findFirst()
            .orElseThrow();
        
        assertThat(webModuleDesc.getPackaging()).isEqualTo("war");
        assertThat(webModuleDesc.isSpringBootExecutable()).isFalse();
        assertThat(webModuleDesc.getRepositoryPath()).isEqualTo("com/example/module-web/1.0.0/module-web-1.0.0.war");
        assertThat(webModuleDesc.getModulePath()).isEqualTo("module-web");
    }
    
    @Test
    void shouldHandleCustomFinalName() throws IOException {
        Path projectDir = tempDir.resolve("custom-name-project");
        Files.createDirectories(projectDir);
        
        String pomContent = """
            <?xml version="1.0" encoding="UTF-8"?>
            <project xmlns="http://maven.apache.org/POM/4.0.0"
                     xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
                     xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 
                     http://maven.apache.org/xsd/maven-4.0.0.xsd">
                <modelVersion>4.0.0</modelVersion>
                
                <groupId>com.example</groupId>
                <artifactId>my-app</artifactId>
                <version>1.0.0</version>
                <packaging>jar</packaging>
                
                <build>
                    <finalName>custom-application</finalName>
                </build>
            </project>
            """;
        
        Files.writeString(projectDir.resolve("pom.xml"), pomContent);
        
        // Analyze
        ProjectDescriptor descriptor = analyzer.analyzeProject(projectDir);
        
        // Verify
        assertThat(descriptor.deployableModules()).hasSize(1);
        DeployableModule module = descriptor.deployableModules().get(0);

        assertThat(module.getFinalName()).isEqualTo("custom-application");
        // Maven repositories always use standard naming artifactId-version.extension
        assertThat(module.getRepositoryPath()).isEqualTo("com/example/my-app/1.0.0/my-app-1.0.0.jar");
    }
}

