package io.github.tourem.maven.descriptor.service;

import org.apache.maven.model.Build;
import org.apache.maven.model.Model;
import org.apache.maven.model.Plugin;
import org.apache.maven.model.PluginManagement;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ExecutablePluginDetectorTest {
    
    private ExecutablePluginDetector detector;
    
    @BeforeEach
    void setUp() {
        detector = new ExecutablePluginDetector();
    }
    
    @Test
    void shouldDetectSpringBootPlugin() {
        Model model = createModelWithPlugin("org.springframework.boot", "spring-boot-maven-plugin");
        
        List<String> plugins = detector.detectExecutablePlugins(model);
        
        assertThat(plugins).containsExactly("spring-boot-maven-plugin");
    }
    
    @Test
    void shouldDetectQuarkusPlugin() {
        Model model = createModelWithPlugin("io.quarkus", "quarkus-maven-plugin");
        
        List<String> plugins = detector.detectExecutablePlugins(model);
        
        assertThat(plugins).containsExactly("quarkus-maven-plugin");
    }
    
    @Test
    void shouldDetectQuarkusPlatformPlugin() {
        Model model = createModelWithPlugin("io.quarkus.platform", "quarkus-maven-plugin");
        
        List<String> plugins = detector.detectExecutablePlugins(model);
        
        assertThat(plugins).containsExactly("quarkus-maven-plugin");
    }
    
    @Test
    void shouldDetectMavenShadePlugin() {
        Model model = createModelWithPlugin("org.apache.maven.plugins", "maven-shade-plugin");
        
        List<String> plugins = detector.detectExecutablePlugins(model);
        
        assertThat(plugins).containsExactly("maven-shade-plugin");
    }
    
    @Test
    void shouldDetectMavenAssemblyPlugin() {
        Model model = createModelWithPlugin("org.apache.maven.plugins", "maven-assembly-plugin");
        
        List<String> plugins = detector.detectExecutablePlugins(model);
        
        assertThat(plugins).containsExactly("maven-assembly-plugin");
    }
    
    @Test
    void shouldDetectJibPlugin() {
        Model model = createModelWithPlugin("com.google.cloud.tools", "jib-maven-plugin");
        
        List<String> plugins = detector.detectExecutablePlugins(model);
        
        assertThat(plugins).containsExactly("jib-maven-plugin");
    }
    
    @Test
    void shouldDetectDockerfilePlugin() {
        Model model = createModelWithPlugin("com.spotify", "dockerfile-maven-plugin");
        
        List<String> plugins = detector.detectExecutablePlugins(model);
        
        assertThat(plugins).containsExactly("dockerfile-maven-plugin");
    }
    
    @Test
    void shouldDetectMultiplePlugins() {
        Model model = new Model();
        Build build = new Build();
        model.setBuild(build);
        
        List<Plugin> plugins = new ArrayList<>();
        plugins.add(createPlugin("org.springframework.boot", "spring-boot-maven-plugin"));
        plugins.add(createPlugin("org.apache.maven.plugins", "maven-assembly-plugin"));
        plugins.add(createPlugin("com.google.cloud.tools", "jib-maven-plugin"));
        build.setPlugins(plugins);
        
        List<String> detectedPlugins = detector.detectExecutablePlugins(model);
        
        assertThat(detectedPlugins).containsExactlyInAnyOrder(
            "spring-boot-maven-plugin",
            "maven-assembly-plugin",
            "jib-maven-plugin"
        );
    }
    
    @Test
    void shouldNotDetectNonExecutablePlugins() {
        Model model = createModelWithPlugin("org.apache.maven.plugins", "maven-compiler-plugin");
        
        List<String> plugins = detector.detectExecutablePlugins(model);
        
        assertThat(plugins).isEmpty();
    }
    
    @Test
    void shouldDetectPluginInPluginManagement() {
        Model model = new Model();
        Build build = new Build();
        model.setBuild(build);
        
        PluginManagement pluginManagement = new PluginManagement();
        List<Plugin> plugins = new ArrayList<>();
        plugins.add(createPlugin("org.springframework.boot", "spring-boot-maven-plugin"));
        pluginManagement.setPlugins(plugins);
        build.setPluginManagement(pluginManagement);
        
        List<String> detectedPlugins = detector.detectExecutablePlugins(model);
        
        assertThat(detectedPlugins).containsExactly("spring-boot-maven-plugin");
    }
    
    @Test
    void shouldNotDuplicatePluginsFromBuildAndPluginManagement() {
        Model model = new Model();
        Build build = new Build();
        model.setBuild(build);
        
        // Add plugin to build
        List<Plugin> buildPlugins = new ArrayList<>();
        buildPlugins.add(createPlugin("org.springframework.boot", "spring-boot-maven-plugin"));
        build.setPlugins(buildPlugins);
        
        // Add same plugin to plugin management
        PluginManagement pluginManagement = new PluginManagement();
        List<Plugin> managementPlugins = new ArrayList<>();
        managementPlugins.add(createPlugin("org.springframework.boot", "spring-boot-maven-plugin"));
        pluginManagement.setPlugins(managementPlugins);
        build.setPluginManagement(pluginManagement);
        
        List<String> detectedPlugins = detector.detectExecutablePlugins(model);
        
        assertThat(detectedPlugins).containsExactly("spring-boot-maven-plugin");
    }
    
    @Test
    void shouldReturnEmptyListWhenNoBuildSection() {
        Model model = new Model();
        
        List<String> plugins = detector.detectExecutablePlugins(model);
        
        assertThat(plugins).isEmpty();
    }
    
    @Test
    void shouldReturnEmptyListWhenNoPlugins() {
        Model model = new Model();
        Build build = new Build();
        model.setBuild(build);
        
        List<String> plugins = detector.detectExecutablePlugins(model);
        
        assertThat(plugins).isEmpty();
    }
    
    @Test
    void shouldReturnTrueWhenHasExecutablePlugins() {
        Model model = createModelWithPlugin("org.springframework.boot", "spring-boot-maven-plugin");
        
        boolean hasPlugins = detector.hasExecutablePlugins(model);
        
        assertThat(hasPlugins).isTrue();
    }
    
    @Test
    void shouldReturnFalseWhenNoExecutablePlugins() {
        Model model = createModelWithPlugin("org.apache.maven.plugins", "maven-compiler-plugin");
        
        boolean hasPlugins = detector.hasExecutablePlugins(model);
        
        assertThat(hasPlugins).isFalse();
    }
    
    @Test
    void shouldHandlePluginWithoutGroupId() {
        // When groupId is null, it defaults to org.apache.maven.plugins
        Model model = new Model();
        Build build = new Build();
        model.setBuild(build);
        
        List<Plugin> plugins = new ArrayList<>();
        Plugin plugin = new Plugin();
        plugin.setArtifactId("maven-shade-plugin");
        // No groupId set - should default to org.apache.maven.plugins
        plugins.add(plugin);
        build.setPlugins(plugins);
        
        List<String> detectedPlugins = detector.detectExecutablePlugins(model);
        
        assertThat(detectedPlugins).containsExactly("maven-shade-plugin");
    }
    
    @Test
    void shouldNotDetectPluginWithWrongGroupId() {
        Model model = createModelWithPlugin("com.wrong.groupid", "spring-boot-maven-plugin");
        
        List<String> plugins = detector.detectExecutablePlugins(model);
        
        assertThat(plugins).isEmpty();
    }
    
    // Helper methods
    
    private Model createModelWithPlugin(String groupId, String artifactId) {
        Model model = new Model();
        Build build = new Build();
        model.setBuild(build);
        
        List<Plugin> plugins = new ArrayList<>();
        plugins.add(createPlugin(groupId, artifactId));
        build.setPlugins(plugins);
        
        return model;
    }
    
    private Plugin createPlugin(String groupId, String artifactId) {
        Plugin plugin = new Plugin();
        plugin.setGroupId(groupId);
        plugin.setArtifactId(artifactId);
        return plugin;
    }
}

