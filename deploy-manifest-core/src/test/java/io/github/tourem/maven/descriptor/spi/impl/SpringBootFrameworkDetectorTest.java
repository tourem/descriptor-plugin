package io.github.tourem.maven.descriptor.spi.impl;

import io.github.tourem.maven.descriptor.model.DeployableModule;
import org.apache.maven.model.Build;
import org.apache.maven.model.Dependency;
import org.apache.maven.model.Model;
import org.apache.maven.model.Plugin;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for SpringBootFrameworkDetector.
 */
class SpringBootFrameworkDetectorTest {

    private SpringBootFrameworkDetector detector;

    @BeforeEach
    void setUp() {
        detector = new SpringBootFrameworkDetector();
    }

    @Test
    void shouldReturnCorrectFrameworkName() {
        // When
        String frameworkName = detector.getFrameworkName();
        
        // Then
        assertThat(frameworkName).isEqualTo("Spring Boot");
    }

    @Test
    void shouldHaveHighPriority() {
        // When
        int priority = detector.getPriority();
        
        // Then: Spring Boot should have high priority (100)
        assertThat(priority).isEqualTo(100);
    }

    @Test
    void shouldBeApplicableForSpringBootProject() {
        // Given: A Maven model with Spring Boot plugin
        Model model = createModelWithSpringBootPlugin();
        
        // When
        boolean applicable = detector.isApplicable(model, Path.of("."));
        
        // Then
        assertThat(applicable).isTrue();
    }

    @Test
    void shouldNotBeApplicableForNonSpringBootProject() {
        // Given: A Maven model without Spring Boot plugin
        Model model = new Model();
        model.setBuild(new Build());
        
        // When
        boolean applicable = detector.isApplicable(model, Path.of("."));
        
        // Then
        assertThat(applicable).isFalse();
    }

    @Test
    void shouldEnrichModuleWithSpringBootMetadata(@TempDir Path tempDir) {
        // Given: A Spring Boot model
        Model model = createModelWithSpringBootPlugin();
        model.setGroupId("com.example");
        model.setArtifactId("my-app");
        model.setVersion("1.0.0");
        
        DeployableModule.DeployableModuleBuilder builder = DeployableModule.builder();
        
        // When: Enriching the module
        detector.enrichModule(builder, model, tempDir, tempDir);
        
        // Then: Module should be marked as Spring Boot executable
        DeployableModule module = builder.build();
        assertThat(module.isSpringBootExecutable()).isTrue();
    }



    // Helper methods

    private Model createModelWithSpringBootPlugin() {
        Model model = new Model();
        Build build = new Build();
        
        Plugin springBootPlugin = new Plugin();
        springBootPlugin.setGroupId("org.springframework.boot");
        springBootPlugin.setArtifactId("spring-boot-maven-plugin");
        
        List<Plugin> plugins = new ArrayList<>();
        plugins.add(springBootPlugin);
        build.setPlugins(plugins);
        
        model.setBuild(build);
        return model;
    }

    private Model createModelWithSpringBootDependency() {
        Model model = new Model();
        
        Dependency springBootStarter = new Dependency();
        springBootStarter.setGroupId("org.springframework.boot");
        springBootStarter.setArtifactId("spring-boot-starter");
        
        List<Dependency> dependencies = new ArrayList<>();
        dependencies.add(springBootStarter);
        model.setDependencies(dependencies);
        
        return model;
    }
}

