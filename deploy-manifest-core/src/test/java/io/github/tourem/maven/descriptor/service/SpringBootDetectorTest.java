package io.github.tourem.maven.descriptor.service;

import org.apache.maven.model.Build;
import org.apache.maven.model.Model;
import org.apache.maven.model.Plugin;
import org.codehaus.plexus.util.xml.Xpp3Dom;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for SpringBootDetector.
 */
class SpringBootDetectorTest {
    
    private SpringBootDetector detector;
    
    @BeforeEach
    void setUp() {
        detector = new SpringBootDetector();
    }
    
    @Test
    void shouldDetectSpringBootPlugin() {
        Model model = createModelWithSpringBootPlugin();
        
        boolean isSpringBoot = detector.isSpringBootExecutable(model);
        
        assertThat(isSpringBoot).isTrue();
    }
    
    @Test
    void shouldNotDetectSpringBootWhenPluginIsMissing() {
        Model model = new Model();
        model.setBuild(new Build());
        model.getBuild().setPlugins(new ArrayList<>());
        
        boolean isSpringBoot = detector.isSpringBootExecutable(model);
        
        assertThat(isSpringBoot).isFalse();
    }
    
    @Test
    void shouldNotDetectSpringBootWhenBuildIsNull() {
        Model model = new Model();
        
        boolean isSpringBoot = detector.isSpringBootExecutable(model);
        
        assertThat(isSpringBoot).isFalse();
    }
    
    @Test
    void shouldExtractClassifierFromConfiguration() {
        Model model = createModelWithSpringBootPlugin();
        Plugin plugin = model.getBuild().getPlugins().get(0);
        
        // Add classifier configuration
        Xpp3Dom config = new Xpp3Dom("configuration");
        Xpp3Dom classifier = new Xpp3Dom("classifier");
        classifier.setValue("exec");
        config.addChild(classifier);
        plugin.setConfiguration(config);
        
        String extractedClassifier = detector.getSpringBootClassifier(model);
        
        assertThat(extractedClassifier).isEqualTo("exec");
    }
    
    @Test
    void shouldReturnNullWhenClassifierNotConfigured() {
        Model model = createModelWithSpringBootPlugin();
        
        String classifier = detector.getSpringBootClassifier(model);
        
        assertThat(classifier).isNull();
    }
    
    @Test
    void shouldExtractFinalNameFromConfiguration() {
        Model model = createModelWithSpringBootPlugin();
        Plugin plugin = model.getBuild().getPlugins().get(0);
        
        // Add finalName configuration
        Xpp3Dom config = new Xpp3Dom("configuration");
        Xpp3Dom finalName = new Xpp3Dom("finalName");
        finalName.setValue("custom-app-name");
        config.addChild(finalName);
        plugin.setConfiguration(config);
        
        String extractedFinalName = detector.getSpringBootFinalName(model);
        
        assertThat(extractedFinalName).isEqualTo("custom-app-name");
    }
    
    @Test
    void shouldReturnNullWhenFinalNameNotConfigured() {
        Model model = createModelWithSpringBootPlugin();
        
        String finalName = detector.getSpringBootFinalName(model);
        
        assertThat(finalName).isNull();
    }
    
    @Test
    void shouldDetectSpringBootInPluginManagement() {
        Model model = new Model();
        Build build = new Build();
        model.setBuild(build);
        
        // Add plugin to plugin management instead of plugins
        org.apache.maven.model.PluginManagement pluginManagement = new org.apache.maven.model.PluginManagement();
        List<Plugin> plugins = new ArrayList<>();
        Plugin springBootPlugin = new Plugin();
        springBootPlugin.setGroupId("org.springframework.boot");
        springBootPlugin.setArtifactId("spring-boot-maven-plugin");
        plugins.add(springBootPlugin);
        pluginManagement.setPlugins(plugins);
        build.setPluginManagement(pluginManagement);
        
        boolean isSpringBoot = detector.isSpringBootExecutable(model);
        
        assertThat(isSpringBoot).isTrue();
    }
    
    private Model createModelWithSpringBootPlugin() {
        Model model = new Model();
        Build build = new Build();
        model.setBuild(build);
        
        List<Plugin> plugins = new ArrayList<>();
        Plugin springBootPlugin = new Plugin();
        springBootPlugin.setGroupId("org.springframework.boot");
        springBootPlugin.setArtifactId("spring-boot-maven-plugin");
        plugins.add(springBootPlugin);
        
        build.setPlugins(plugins);
        
        return model;
    }
}

