package io.github.tourem.maven.descriptor.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for MavenRepositoryPathGenerator.
 */
class MavenRepositoryPathGeneratorTest {
    
    private MavenRepositoryPathGenerator generator;
    
    @BeforeEach
    void setUp() {
        generator = new MavenRepositoryPathGenerator();
    }
    
    @Test
    void shouldGenerateStandardJarPath() {
        String path = generator.generatePath(
            "com.larbotech",
            "my-app",
            "1.0.0",
            "jar"
        );
        
        assertThat(path).isEqualTo("com/larbotech/my-app/1.0.0/my-app-1.0.0.jar");
    }
    
    @Test
    void shouldGenerateSnapshotPath() {
        String path = generator.generatePath(
            "com.larbotech",
            "task",
            "1.1.2-SNAPSHOT",
            "jar"
        );
        
        assertThat(path).isEqualTo("com/larbotech/task/1.1.2-SNAPSHOT/task-1.1.2-SNAPSHOT.jar");
    }
    
    @Test
    void shouldGenerateWarPath() {
        String path = generator.generatePath(
            "com.example",
            "webapp",
            "2.0.0",
            "war"
        );
        
        assertThat(path).isEqualTo("com/example/webapp/2.0.0/webapp-2.0.0.war");
    }
    
    @Test
    void shouldGenerateEarPath() {
        String path = generator.generatePath(
            "org.mycompany",
            "enterprise-app",
            "3.5.1",
            "ear"
        );
        
        assertThat(path).isEqualTo("org/mycompany/enterprise-app/3.5.1/enterprise-app-3.5.1.ear");
    }
    
    @Test
    void shouldGeneratePathWithClassifier() {
        String path = generator.generatePathWithClassifier(
            "com.larbotech",
            "my-app",
            "1.0.0",
            "jar",
            "exec"
        );
        
        assertThat(path).isEqualTo("com/larbotech/my-app/1.0.0/my-app-1.0.0-exec.jar");
    }
    
    @Test
    void shouldGeneratePathWithCustomFinalName() {
        // Even with custom finalName, Maven repositories use standard naming
        String path = generator.generatePath(
            "com.larbotech",
            "my-app",
            "1.0.0",
            "custom-name",
            "jar",
            null
        );

        assertThat(path).isEqualTo("com/larbotech/my-app/1.0.0/my-app-1.0.0.jar");
    }

    @Test
    void shouldGeneratePathWithCustomFinalNameAndClassifier() {
        // Even with custom finalName, Maven repositories use standard naming
        String path = generator.generatePath(
            "com.larbotech",
            "my-app",
            "1.0.0",
            "custom-name",
            "jar",
            "exec"
        );

        assertThat(path).isEqualTo("com/larbotech/my-app/1.0.0/my-app-1.0.0-exec.jar");
    }
    
    @Test
    void shouldHandleDeepGroupId() {
        String path = generator.generatePath(
            "com.larbotech.platform.services",
            "auth-service",
            "1.0.0",
            "jar"
        );
        
        assertThat(path).isEqualTo("com/larbotech/platform/services/auth-service/1.0.0/auth-service-1.0.0.jar");
    }
    
    @Test
    void shouldThrowExceptionWhenGroupIdIsNull() {
        assertThatThrownBy(() -> generator.generatePath(null, "my-app", "1.0.0", "jar"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("groupId, artifactId, and version are required");
    }
    
    @Test
    void shouldThrowExceptionWhenArtifactIdIsNull() {
        assertThatThrownBy(() -> generator.generatePath("com.larbotech", null, "1.0.0", "jar"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("groupId, artifactId, and version are required");
    }
    
    @Test
    void shouldThrowExceptionWhenVersionIsNull() {
        assertThatThrownBy(() -> generator.generatePath("com.larbotech", "my-app", null, "jar"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("groupId, artifactId, and version are required");
    }
    
    @Test
    void shouldUseDefaultJarExtensionWhenPackagingIsNull() {
        String path = generator.generatePath(
            "com.larbotech",
            "my-app",
            "1.0.0",
            null,
            null,
            null
        );
        
        assertThat(path).isEqualTo("com/larbotech/my-app/1.0.0/my-app-1.0.0.jar");
    }
}

