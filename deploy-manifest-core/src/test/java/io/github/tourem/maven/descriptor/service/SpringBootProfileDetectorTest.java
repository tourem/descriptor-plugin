package io.github.tourem.maven.descriptor.service;

import org.apache.maven.model.Dependency;
import org.apache.maven.model.Model;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class SpringBootProfileDetectorTest {
    
    private SpringBootProfileDetector detector;
    
    @BeforeEach
    void setUp() {
        detector = new SpringBootProfileDetector();
    }
    
    @Test
    void shouldDetectProfilesFromPropertiesFiles(@TempDir Path tempDir) throws IOException {
        // Create resources directory
        Path resourcesDir = tempDir.resolve("src/main/resources");
        Files.createDirectories(resourcesDir);
        
        // Create profile files
        Files.createFile(resourcesDir.resolve("application.properties"));
        Files.createFile(resourcesDir.resolve("application-dev.properties"));
        Files.createFile(resourcesDir.resolve("application-prod.properties"));
        Files.createFile(resourcesDir.resolve("application-test.properties"));
        
        Model model = new Model();
        List<String> profiles = detector.detectProfiles(tempDir, model, tempDir);
        
        assertThat(profiles).containsExactlyInAnyOrder("dev", "prod", "test");
    }
    
    @Test
    void shouldDetectProfilesFromYmlFiles(@TempDir Path tempDir) throws IOException {
        Path resourcesDir = tempDir.resolve("src/main/resources");
        Files.createDirectories(resourcesDir);
        
        Files.createFile(resourcesDir.resolve("application.yml"));
        Files.createFile(resourcesDir.resolve("application-dev.yml"));
        Files.createFile(resourcesDir.resolve("application-staging.yml"));
        
        Model model = new Model();
        List<String> profiles = detector.detectProfiles(tempDir, model, tempDir);
        
        assertThat(profiles).containsExactlyInAnyOrder("dev", "staging");
    }
    
    @Test
    void shouldDetectProfilesFromYamlFiles(@TempDir Path tempDir) throws IOException {
        Path resourcesDir = tempDir.resolve("src/main/resources");
        Files.createDirectories(resourcesDir);
        
        Files.createFile(resourcesDir.resolve("application.yaml"));
        Files.createFile(resourcesDir.resolve("application-hml.yaml"));
        Files.createFile(resourcesDir.resolve("application-prod.yaml"));
        
        Model model = new Model();
        List<String> profiles = detector.detectProfiles(tempDir, model, tempDir);
        
        assertThat(profiles).containsExactlyInAnyOrder("hml", "prod");
    }
    
    @Test
    void shouldDetectProfilesFromMixedFiles(@TempDir Path tempDir) throws IOException {
        Path resourcesDir = tempDir.resolve("src/main/resources");
        Files.createDirectories(resourcesDir);
        
        Files.createFile(resourcesDir.resolve("application-dev.properties"));
        Files.createFile(resourcesDir.resolve("application-prod.yml"));
        Files.createFile(resourcesDir.resolve("application-staging.yaml"));
        
        Model model = new Model();
        List<String> profiles = detector.detectProfiles(tempDir, model, tempDir);
        
        assertThat(profiles).containsExactlyInAnyOrder("dev", "prod", "staging");
    }
    
    @Test
    void shouldReturnEmptyListWhenNoProfilesFound(@TempDir Path tempDir) throws IOException {
        Path resourcesDir = tempDir.resolve("src/main/resources");
        Files.createDirectories(resourcesDir);
        
        Files.createFile(resourcesDir.resolve("application.properties"));
        Files.createFile(resourcesDir.resolve("logback.xml"));
        
        Model model = new Model();
        List<String> profiles = detector.detectProfiles(tempDir, model, tempDir);
        
        assertThat(profiles).isEmpty();
    }
    
    @Test
    void shouldReturnEmptyListWhenNoResourcesDirectory(@TempDir Path tempDir) {
        Model model = new Model();
        List<String> profiles = detector.detectProfiles(tempDir, model, tempDir);
        
        assertThat(profiles).isEmpty();
    }
    
    @Test
    void shouldDetectProfilesInDependency(@TempDir Path tempDir) throws IOException {
        // Create main module
        Path mainModule = tempDir.resolve("main-module");
        Path mainResources = mainModule.resolve("src/main/resources");
        Files.createDirectories(mainResources);
        Files.createFile(mainResources.resolve("application-dev.properties"));
        
        // Create dependency module
        Path depModule = tempDir.resolve("common");
        Path depResources = depModule.resolve("src/main/resources");
        Files.createDirectories(depResources);
        Files.createFile(depModule.resolve("pom.xml"));
        Files.createFile(depResources.resolve("application-shared.yml"));
        
        // Create model with dependency
        Model model = new Model();
        Dependency dependency = new Dependency();
        dependency.setArtifactId("common");
        model.addDependency(dependency);
        
        List<String> profiles = detector.detectProfiles(mainModule, model, tempDir);
        
        assertThat(profiles).containsExactlyInAnyOrder("dev", "shared");
    }
    
    @Test
    void shouldSortProfilesAlphabetically(@TempDir Path tempDir) throws IOException {
        Path resourcesDir = tempDir.resolve("src/main/resources");
        Files.createDirectories(resourcesDir);
        
        Files.createFile(resourcesDir.resolve("application-zzz.properties"));
        Files.createFile(resourcesDir.resolve("application-aaa.properties"));
        Files.createFile(resourcesDir.resolve("application-mmm.properties"));
        
        Model model = new Model();
        List<String> profiles = detector.detectProfiles(tempDir, model, tempDir);
        
        assertThat(profiles).containsExactly("aaa", "mmm", "zzz");
    }
    
    @Test
    void shouldHandleProfilesWithHyphensAndUnderscores(@TempDir Path tempDir) throws IOException {
        Path resourcesDir = tempDir.resolve("src/main/resources");
        Files.createDirectories(resourcesDir);
        
        Files.createFile(resourcesDir.resolve("application-dev-local.properties"));
        Files.createFile(resourcesDir.resolve("application-prod_eu.yml"));
        Files.createFile(resourcesDir.resolve("application-test_123.yaml"));
        
        Model model = new Model();
        List<String> profiles = detector.detectProfiles(tempDir, model, tempDir);
        
        assertThat(profiles).containsExactlyInAnyOrder("dev-local", "prod_eu", "test_123");
    }
    
    @Test
    void shouldIgnoreNonProfileFiles(@TempDir Path tempDir) throws IOException {
        Path resourcesDir = tempDir.resolve("src/main/resources");
        Files.createDirectories(resourcesDir);
        
        Files.createFile(resourcesDir.resolve("application-dev.properties"));
        Files.createFile(resourcesDir.resolve("messages.properties"));
        Files.createFile(resourcesDir.resolve("banner.txt"));
        Files.createFile(resourcesDir.resolve("application.xml"));
        
        Model model = new Model();
        List<String> profiles = detector.detectProfiles(tempDir, model, tempDir);
        
        assertThat(profiles).containsExactly("dev");
    }
}

