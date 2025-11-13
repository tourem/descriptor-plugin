package io.github.tourem.maven.descriptor.service;

import io.github.tourem.maven.descriptor.model.ExecutableInfo;
import io.github.tourem.maven.descriptor.model.ExecutableType;
import org.apache.maven.model.Build;
import org.apache.maven.model.Dependency;
import org.apache.maven.model.Model;
import org.apache.maven.model.Plugin;
import org.apache.maven.model.PluginExecution;
import org.codehaus.plexus.util.xml.Xpp3Dom;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for EnhancedExecutableDetector.
 */
class EnhancedExecutableDetectorTest {

    private EnhancedExecutableDetector detector;

    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() {
        detector = new EnhancedExecutableDetector();
    }

    // ==================== Spring Boot with Plugin Tests ====================

    @Test
    void shouldDetectSpringBootJarWithPlugin() {
        Model model = createModel("jar");
        addSpringBootPlugin(model);
        addSpringBootDependency(model);

        ExecutableInfo info = detector.detectExecutable(model, tempDir);

        assertTrue(info.isExecutable());
        assertEquals(ExecutableType.JAR, info.getType());
        assertEquals("spring-boot-maven-plugin", info.getMethod());
        assertEquals("jar-in-jar", info.getStructure());
        assertTrue(info.isSpringBootApplication());
        assertEquals("org.springframework.boot.loader.JarLauncher", info.getLauncherClass());
    }

    @Test
    void shouldDetectSpringBootWarWithPlugin() {
        Model model = createModel("war");
        addSpringBootPlugin(model);
        addSpringBootDependency(model);

        ExecutableInfo info = detector.detectExecutable(model, tempDir);

        assertTrue(info.isExecutable());
        assertEquals(ExecutableType.WAR, info.getType());
        assertEquals("spring-boot-maven-plugin", info.getMethod());
        assertTrue(info.isSpringBootApplication());
        assertTrue(info.isServletInitializer());
        assertEquals("org.springframework.boot.loader.WarLauncher", info.getLauncherClass());
    }

    @Test
    void shouldDetectSpringBootProfilesWithPlugin() throws Exception {
        Model model = createModel("jar");
        addSpringBootPlugin(model);
        addSpringBootDependency(model);

        // Create Spring Boot profile files
        Path resourcesDir = tempDir.resolve("src/main/resources");
        Files.createDirectories(resourcesDir);
        Files.createFile(resourcesDir.resolve("application-dev.properties"));
        Files.createFile(resourcesDir.resolve("application-prod.yml"));
        Files.createFile(resourcesDir.resolve("application-test.yaml"));

        ExecutableInfo info = detector.detectExecutable(model, tempDir);

        assertTrue(info.isExecutable());
        assertTrue(info.isSpringBootApplication());
        assertNotNull(info.getSpringBootProfiles());
        assertEquals(3, info.getSpringBootProfiles().size());
        assertTrue(info.getSpringBootProfiles().contains("dev"));
        assertTrue(info.getSpringBootProfiles().contains("prod"));
        assertTrue(info.getSpringBootProfiles().contains("test"));
    }

    // ==================== Spring Boot WITHOUT Plugin Tests ====================

    @Test
    void shouldDetectSpringBootJarWithoutPlugin() {
        Model model = createModel("jar");
        addSpringBootDependency(model);
        // No Spring Boot plugin

        ExecutableInfo info = detector.detectExecutable(model, tempDir);

        assertTrue(info.isExecutable());
        assertEquals(ExecutableType.JAR, info.getType());
        assertEquals("spring-boot-dependencies (no plugin)", info.getMethod());
        assertTrue(info.isSpringBootApplication());
        assertNotNull(info.getWarning());
        assertTrue(info.getWarning().contains("no spring-boot-maven-plugin"));
    }

    @Test
    void shouldDetectSpringBootWarWithoutPlugin() {
        Model model = createModel("war");
        addSpringBootDependency(model);
        // No Spring Boot plugin

        ExecutableInfo info = detector.detectExecutable(model, tempDir);

        assertFalse(info.isExecutable());
        assertEquals(ExecutableType.WAR, info.getType());
        assertEquals("spring-boot-dependencies (no plugin)", info.getMethod());
        assertTrue(info.isSpringBootApplication());
        assertTrue(info.isDeploymentOnly());
        assertTrue(info.isRequiresExternalServer());
        assertNotNull(info.getWarning());
    }

    @Test
    void shouldDetectSpringBootProfilesWithoutPlugin() throws Exception {
        Model model = createModel("jar");
        addSpringBootDependency(model);

        // Create Spring Boot profile files
        Path resourcesDir = tempDir.resolve("src/main/resources");
        Files.createDirectories(resourcesDir);
        Files.createFile(resourcesDir.resolve("application-staging.properties"));

        ExecutableInfo info = detector.detectExecutable(model, tempDir);

        assertTrue(info.isSpringBootApplication());
        assertNotNull(info.getSpringBootProfiles());
        assertEquals(1, info.getSpringBootProfiles().size());
        assertTrue(info.getSpringBootProfiles().contains("staging"));
    }

    // ==================== Maven Shade Plugin Tests ====================

    @Test
    void shouldDetectMavenShadePlugin() {
        Model model = createModel("jar");
        addShadePlugin(model, "com.example.Main");

        ExecutableInfo info = detector.detectExecutable(model, tempDir);

        assertTrue(info.isExecutable());
        assertEquals(ExecutableType.JAR, info.getType());
        assertEquals("maven-shade-plugin", info.getMethod());
        assertEquals("flat-jar", info.getStructure());
        assertEquals("com.example.Main", info.getMainClass());
    }

    @Test
    void shouldDetectMavenShadePluginWithSpringBoot() {
        Model model = createModel("jar");
        addShadePlugin(model, "com.example.Application");
        addSpringBootDependency(model);

        ExecutableInfo info = detector.detectExecutable(model, tempDir);

        assertTrue(info.isExecutable());
        assertEquals("maven-shade-plugin", info.getMethod());
        assertTrue(info.isSpringBootApplication());
        assertEquals("flat-jar", info.getStructure());
    }

    // ==================== Maven Assembly Plugin Tests ====================

    @Test
    void shouldDetectMavenAssemblyPlugin() {
        Model model = createModel("jar");
        addAssemblyPlugin(model, "com.example.Main");

        ExecutableInfo info = detector.detectExecutable(model, tempDir);

        assertTrue(info.isExecutable());
        assertEquals(ExecutableType.JAR, info.getType());
        assertEquals("maven-assembly-plugin", info.getMethod());
        assertEquals("flat-jar", info.getStructure());
        assertEquals("com.example.Main", info.getMainClass());
    }

    // ==================== Maven Jar + Dependency Plugin Tests ====================

    @Test
    void shouldDetectJarPlusDependencyPlugin() {
        Model model = createModel("jar");
        addJarPlugin(model, "com.example.Main");
        addDependencyPlugin(model);

        ExecutableInfo info = detector.detectExecutable(model, tempDir);

        assertTrue(info.isExecutable());
        assertEquals(ExecutableType.JAR, info.getType());
        assertEquals("maven-jar-plugin + maven-dependency-plugin", info.getMethod());
        assertEquals("com.example.Main", info.getMainClass());
    }

    // ==================== WAR Plugin Tests ====================

    @Test
    void shouldDetectJettyWar() {
        Model model = createModel("war");
        addJettyPlugin(model);

        ExecutableInfo info = detector.detectExecutable(model, tempDir);

        assertFalse(info.isExecutable());
        assertEquals(ExecutableType.WAR, info.getType());
        assertEquals("jetty-maven-plugin", info.getMethod());
        assertEquals("Jetty", info.getEmbeddedServer());
        assertEquals("mvn jetty:run", info.getRunCommand());
    }

    @Test
    void shouldDetectTomcatWar() {
        Model model = createModel("war");
        addTomcatPlugin(model);

        ExecutableInfo info = detector.detectExecutable(model, tempDir);

        assertFalse(info.isExecutable());
        assertEquals(ExecutableType.WAR, info.getType());
        assertEquals("tomcat7-maven-plugin", info.getMethod());
        assertEquals("Tomcat", info.getEmbeddedServer());
        assertEquals("mvn tomcat7:run", info.getRunCommand());
    }

    // ==================== EAR Plugin Tests ====================

    @Test
    void shouldDetectEar() {
        Model model = createModel("ear");
        addEarPlugin(model);

        ExecutableInfo info = detector.detectExecutable(model, tempDir);

        assertFalse(info.isExecutable());
        assertEquals(ExecutableType.EAR, info.getType());
        assertEquals("maven-ear-plugin", info.getMethod());
        assertTrue(info.isDeploymentOnly());
        assertTrue(info.isRequiresExternalServer());
    }

    // ==================== OneJar Plugin Tests (Obsolete) ====================

    @Test
    void shouldDetectOneJarPluginAsObsolete() {
        Model model = createModel("jar");
        addOneJarPlugin(model);

        ExecutableInfo info = detector.detectExecutable(model, tempDir);

        assertTrue(info.isExecutable());
        assertEquals(ExecutableType.JAR, info.getType());
        assertEquals("onejar-maven-plugin", info.getMethod());
        assertTrue(info.isObsolete());
        assertNotNull(info.getWarning());
        assertTrue(info.getWarning().contains("obsolete"));
    }

    // ==================== Non-Executable Tests ====================

    @Test
    void shouldReturnNotExecutableForPlainJar() {
        Model model = createModel("jar");
        // No executable plugins

        ExecutableInfo info = detector.detectExecutable(model, tempDir);

        assertFalse(info.isExecutable());
        assertNull(info.getType());
    }

    @Test
    void shouldReturnNotExecutableForPom() {
        Model model = createModel("pom");

        ExecutableInfo info = detector.detectExecutable(model, tempDir);

        assertFalse(info.isExecutable());
    }

    // ==================== Helper Methods ====================

    private Model createModel(String packaging) {
        Model model = new Model();
        model.setGroupId("com.example");
        model.setArtifactId("test-module");
        model.setVersion("1.0.0");
        model.setPackaging(packaging);
        model.setBuild(new Build());
        return model;
    }

    private void addSpringBootPlugin(Model model) {
        Plugin plugin = new Plugin();
        plugin.setGroupId("org.springframework.boot");
        plugin.setArtifactId("spring-boot-maven-plugin");
        plugin.setVersion("3.2.0");

        PluginExecution execution = new PluginExecution();
        execution.setGoals(Collections.singletonList("repackage"));
        plugin.setExecutions(Collections.singletonList(execution));

        model.getBuild().addPlugin(plugin);
    }

    private void addSpringBootDependency(Model model) {
        Dependency dep = new Dependency();
        dep.setGroupId("org.springframework.boot");
        dep.setArtifactId("spring-boot-starter-web");
        dep.setVersion("3.2.0");
        model.addDependency(dep);
    }

    private void addShadePlugin(Model model, String mainClass) {
        Plugin plugin = new Plugin();
        plugin.setGroupId("org.apache.maven.plugins");
        plugin.setArtifactId("maven-shade-plugin");
        plugin.setVersion("3.5.0");

        PluginExecution execution = new PluginExecution();
        execution.setGoals(Collections.singletonList("shade"));
        plugin.setExecutions(Collections.singletonList(execution));

        // Add transformer with main class
        Xpp3Dom config = new Xpp3Dom("configuration");
        Xpp3Dom transformers = new Xpp3Dom("transformers");
        Xpp3Dom transformer = new Xpp3Dom("transformer");
        transformer.setAttribute("implementation", "org.apache.maven.plugins.shade.resource.ManifestResourceTransformer");
        Xpp3Dom mainClassNode = new Xpp3Dom("mainClass");
        mainClassNode.setValue(mainClass);
        transformer.addChild(mainClassNode);
        transformers.addChild(transformer);
        config.addChild(transformers);
        plugin.setConfiguration(config);

        model.getBuild().addPlugin(plugin);
    }

    private void addAssemblyPlugin(Model model, String mainClass) {
        Plugin plugin = new Plugin();
        plugin.setGroupId("org.apache.maven.plugins");
        plugin.setArtifactId("maven-assembly-plugin");
        plugin.setVersion("3.6.0");

        PluginExecution execution = new PluginExecution();
        execution.setGoals(Collections.singletonList("single"));
        plugin.setExecutions(Collections.singletonList(execution));

        // Add archive configuration with main class
        Xpp3Dom config = new Xpp3Dom("configuration");
        Xpp3Dom archive = new Xpp3Dom("archive");
        Xpp3Dom manifest = new Xpp3Dom("manifest");
        Xpp3Dom mainClassNode = new Xpp3Dom("mainClass");
        mainClassNode.setValue(mainClass);
        manifest.addChild(mainClassNode);
        archive.addChild(manifest);
        config.addChild(archive);
        plugin.setConfiguration(config);

        model.getBuild().addPlugin(plugin);
    }

    private void addJarPlugin(Model model, String mainClass) {
        Plugin plugin = new Plugin();
        plugin.setGroupId("org.apache.maven.plugins");
        plugin.setArtifactId("maven-jar-plugin");
        plugin.setVersion("3.3.0");

        // Add archive configuration with main class
        Xpp3Dom config = new Xpp3Dom("configuration");
        Xpp3Dom archive = new Xpp3Dom("archive");
        Xpp3Dom manifest = new Xpp3Dom("manifest");
        Xpp3Dom mainClassNode = new Xpp3Dom("mainClass");
        mainClassNode.setValue(mainClass);
        manifest.addChild(mainClassNode);
        archive.addChild(manifest);
        config.addChild(archive);
        plugin.setConfiguration(config);

        model.getBuild().addPlugin(plugin);
    }

    private void addDependencyPlugin(Model model) {
        Plugin plugin = new Plugin();
        plugin.setGroupId("org.apache.maven.plugins");
        plugin.setArtifactId("maven-dependency-plugin");
        plugin.setVersion("3.6.0");

        PluginExecution execution = new PluginExecution();
        execution.setGoals(Collections.singletonList("copy-dependencies"));
        plugin.setExecutions(Collections.singletonList(execution));

        model.getBuild().addPlugin(plugin);
    }

    private void addJettyPlugin(Model model) {
        Plugin plugin = new Plugin();
        plugin.setGroupId("org.eclipse.jetty");
        plugin.setArtifactId("jetty-maven-plugin");
        plugin.setVersion("11.0.15");
        model.getBuild().addPlugin(plugin);
    }

    private void addTomcatPlugin(Model model) {
        Plugin plugin = new Plugin();
        plugin.setGroupId("org.apache.tomcat.maven");
        plugin.setArtifactId("tomcat7-maven-plugin");
        plugin.setVersion("2.2");
        model.getBuild().addPlugin(plugin);
    }

    private void addEarPlugin(Model model) {
        Plugin plugin = new Plugin();
        plugin.setGroupId("org.apache.maven.plugins");
        plugin.setArtifactId("maven-ear-plugin");
        plugin.setVersion("3.3.0");
        model.getBuild().addPlugin(plugin);
    }

    private void addOneJarPlugin(Model model) {
        Plugin plugin = new Plugin();
        plugin.setGroupId("com.jolira");
        plugin.setArtifactId("onejar-maven-plugin");
        plugin.setVersion("1.4.4");

        PluginExecution execution = new PluginExecution();
        execution.setGoals(Collections.singletonList("one-jar"));
        plugin.setExecutions(Collections.singletonList(execution));

        model.getBuild().addPlugin(plugin);
    }
}
