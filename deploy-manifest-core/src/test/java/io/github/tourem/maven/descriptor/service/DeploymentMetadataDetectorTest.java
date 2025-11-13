package io.github.tourem.maven.descriptor.service;

import org.apache.maven.model.Dependency;
import org.apache.maven.model.Model;
import org.apache.maven.model.Plugin;
import org.codehaus.plexus.util.xml.Xpp3Dom;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;

class DeploymentMetadataDetectorTest {

    private DeploymentMetadataDetector detector;

    @BeforeEach
    void setUp() {
        detector = new DeploymentMetadataDetector();
    }

    @Test
    void shouldDetectJavaVersionFromMavenCompilerRelease() {
        // Given
        Model model = new Model();
        Properties props = new Properties();
        props.setProperty("maven.compiler.release", "17");
        model.setProperties(props);

        // When
        String javaVersion = detector.detectJavaVersion(model);

        // Then
        assertThat(javaVersion).isEqualTo("17");
    }

    @Test
    void shouldDetectJavaVersionFromMavenCompilerSource() {
        // Given
        Model model = new Model();
        Properties props = new Properties();
        props.setProperty("maven.compiler.source", "21");
        model.setProperties(props);

        // When
        String javaVersion = detector.detectJavaVersion(model);

        // Then
        assertThat(javaVersion).isEqualTo("21");
    }

    @Test
    void shouldDetectJavaVersionFromMavenCompilerTarget() {
        // Given
        Model model = new Model();
        Properties props = new Properties();
        props.setProperty("maven.compiler.target", "11");
        model.setProperties(props);

        // When
        String javaVersion = detector.detectJavaVersion(model);

        // Then
        assertThat(javaVersion).isEqualTo("11");
    }

    @Test
    void shouldReturnNullWhenNoJavaVersionFound() {
        // Given
        Model model = new Model();

        // When
        String javaVersion = detector.detectJavaVersion(model);

        // Then
        assertThat(javaVersion).isNull();
    }

    @Test
    void shouldDetectMainClassFromSpringBootPlugin() {
        // Given
        Model model = createModelWithSpringBootPlugin("com.example.MyApplication");

        // When
        String mainClass = detector.detectMainClass(model);

        // Then
        assertThat(mainClass).isEqualTo("com.example.MyApplication");
    }

    @Test
    void shouldReturnNullWhenNoSpringBootPlugin() {
        // Given
        Model model = new Model();

        // When
        String mainClass = detector.detectMainClass(model);

        // Then
        assertThat(mainClass).isNull();
    }

    @Test
    void shouldDetectServerPortFromPropertiesFile(@TempDir Path tempDir) throws IOException {
        // Given
        Path resourcesDir = tempDir.resolve("src/main/resources");
        Files.createDirectories(resourcesDir);
        Path propsFile = resourcesDir.resolve("application.properties");
        Files.writeString(propsFile, "server.port=9090\n");

        // When
        Integer port = detector.detectServerPort(tempDir);

        // Then
        assertThat(port).isEqualTo(9090);
    }

    @Test
    void shouldDetectServerPortFromYamlFile(@TempDir Path tempDir) throws IOException {
        // Given
        Path resourcesDir = tempDir.resolve("src/main/resources");
        Files.createDirectories(resourcesDir);
        Path yamlFile = resourcesDir.resolve("application.yml");
        Files.writeString(yamlFile, "server:\n  port: 8080\n");

        // When
        Integer port = detector.detectServerPort(tempDir);

        // Then
        assertThat(port).isEqualTo(8080);
    }

    @Test
    void shouldReturnNullWhenNoServerPortConfigured(@TempDir Path tempDir) throws IOException {
        // Given
        Path resourcesDir = tempDir.resolve("src/main/resources");
        Files.createDirectories(resourcesDir);
        Path propsFile = resourcesDir.resolve("application.properties");
        Files.writeString(propsFile, "spring.application.name=myapp\n");

        // When
        Integer port = detector.detectServerPort(tempDir);

        // Then
        assertThat(port).isNull();
    }

    @Test
    void shouldReturnNullWhenNoResourcesDirectory(@TempDir Path tempDir) {
        // When
        Integer port = detector.detectServerPort(tempDir);

        // Then
        assertThat(port).isNull();
    }

    @Test
    void shouldDetectActuatorEnabled() {
        // Given
        Model model = new Model();
        Dependency actuator = new Dependency();
        actuator.setGroupId("org.springframework.boot");
        actuator.setArtifactId("spring-boot-starter-actuator");
        model.addDependency(actuator);

        // When
        Boolean actuatorEnabled = detector.detectActuatorEnabled(model);

        // Then
        assertThat(actuatorEnabled).isTrue();
    }

    @Test
    void shouldReturnNullWhenActuatorNotPresent() {
        // Given
        Model model = new Model();
        Dependency web = new Dependency();
        web.setGroupId("org.springframework.boot");
        web.setArtifactId("spring-boot-starter-web");
        model.addDependency(web);

        // When
        Boolean actuatorEnabled = detector.detectActuatorEnabled(model);

        // Then
        assertThat(actuatorEnabled).isNull();
    }

    @Test
    void shouldDetectLocalDependencies() {
        // Given
        Model model = new Model();
        
        Dependency localDep1 = new Dependency();
        localDep1.setGroupId("com.larbotech");
        localDep1.setArtifactId("common");
        model.addDependency(localDep1);
        
        Dependency localDep2 = new Dependency();
        localDep2.setGroupId("com.larbotech");
        localDep2.setArtifactId("shared");
        model.addDependency(localDep2);
        
        Dependency externalDep = new Dependency();
        externalDep.setGroupId("org.springframework.boot");
        externalDep.setArtifactId("spring-boot-starter-web");
        model.addDependency(externalDep);

        // When
        List<String> localDeps = detector.detectLocalDependencies(model, "com.larbotech");

        // Then
        assertThat(localDeps).containsExactly("common", "shared");
    }

    @Test
    void shouldReturnNullWhenNoLocalDependencies() {
        // Given
        Model model = new Model();
        Dependency externalDep = new Dependency();
        externalDep.setGroupId("org.springframework.boot");
        externalDep.setArtifactId("spring-boot-starter-web");
        model.addDependency(externalDep);

        // When
        List<String> localDeps = detector.detectLocalDependencies(model, "com.larbotech");

        // Then
        assertThat(localDeps).isNull();
    }

    @Test
    void shouldDetectServerPortFromYamlFileWithStringValue(@TempDir Path tempDir) throws IOException {
        // Given
        Path resourcesDir = tempDir.resolve("src/main/resources");
        Files.createDirectories(resourcesDir);
        Path yamlFile = resourcesDir.resolve("application.yaml");
        Files.writeString(yamlFile, "server:\n  port: \"8888\"\n");

        // When
        Integer port = detector.detectServerPort(tempDir);

        // Then
        assertThat(port).isEqualTo(8888);
    }

    @Test
    void shouldPreferPropertiesOverYaml(@TempDir Path tempDir) throws IOException {
        // Given
        Path resourcesDir = tempDir.resolve("src/main/resources");
        Files.createDirectories(resourcesDir);

        Path propsFile = resourcesDir.resolve("application.properties");
        Files.writeString(propsFile, "server.port=7070\n");

        Path yamlFile = resourcesDir.resolve("application.yml");
        Files.writeString(yamlFile, "server:\n  port: 8080\n");

        // When
        Integer port = detector.detectServerPort(tempDir);

        // Then
        assertThat(port).isEqualTo(7070);
    }

    @Test
    void shouldDetectDefaultActuatorBasePath(@TempDir Path tempDir) throws IOException {
        // Given
        Path resourcesDir = tempDir.resolve("src/main/resources");
        Files.createDirectories(resourcesDir);
        Path propsFile = resourcesDir.resolve("application.properties");
        Files.writeString(propsFile, "server.port=8080\n");

        // When
        String basePath = detector.detectActuatorBasePath(tempDir, true);

        // Then
        assertThat(basePath).isEqualTo("/actuator");
    }

    @Test
    void shouldDetectCustomActuatorBasePathFromProperties(@TempDir Path tempDir) throws IOException {
        // Given
        Path resourcesDir = tempDir.resolve("src/main/resources");
        Files.createDirectories(resourcesDir);
        Path propsFile = resourcesDir.resolve("application.properties");
        Files.writeString(propsFile, "management.endpoints.web.base-path=/management\n");

        // When
        String basePath = detector.detectActuatorBasePath(tempDir, true);

        // Then
        assertThat(basePath).isEqualTo("/management");
    }

    @Test
    void shouldDetectCustomActuatorBasePathFromYaml(@TempDir Path tempDir) throws IOException {
        // Given
        Path resourcesDir = tempDir.resolve("src/main/resources");
        Files.createDirectories(resourcesDir);
        Path yamlFile = resourcesDir.resolve("application.yml");
        Files.writeString(yamlFile,
            "management:\n" +
            "  endpoints:\n" +
            "    web:\n" +
            "      base-path: /custom-actuator\n");

        // When
        String basePath = detector.detectActuatorBasePath(tempDir, true);

        // Then
        assertThat(basePath).isEqualTo("/custom-actuator");
    }

    @Test
    void shouldReturnNullActuatorBasePathWhenActuatorDisabled(@TempDir Path tempDir) {
        // When
        String basePath = detector.detectActuatorBasePath(tempDir, false);

        // Then
        assertThat(basePath).isNull();
    }

    @Test
    void shouldReturnNullActuatorBasePathWhenActuatorNull(@TempDir Path tempDir) {
        // When
        String basePath = detector.detectActuatorBasePath(tempDir, null);

        // Then
        assertThat(basePath).isNull();
    }

    @Test
    void shouldBuildActuatorHealthPath() {
        // When
        String healthPath = detector.buildActuatorHealthPath("/actuator");

        // Then
        assertThat(healthPath).isEqualTo("/actuator/health");
    }

    @Test
    void shouldBuildActuatorInfoPath() {
        // When
        String infoPath = detector.buildActuatorInfoPath("/actuator");

        // Then
        assertThat(infoPath).isEqualTo("/actuator/info");
    }

    @Test
    void shouldBuildActuatorHealthPathWithCustomBasePath() {
        // When
        String healthPath = detector.buildActuatorHealthPath("/management");

        // Then
        assertThat(healthPath).isEqualTo("/management/health");
    }

    @Test
    void shouldReturnNullHealthPathWhenBasePathNull() {
        // When
        String healthPath = detector.buildActuatorHealthPath(null);

        // Then
        assertThat(healthPath).isNull();
    }

    @Test
    void shouldReturnNullInfoPathWhenBasePathNull() {
        // When
        String infoPath = detector.buildActuatorInfoPath(null);

        // Then
        assertThat(infoPath).isNull();
    }

    // Helper methods

    private Model createModelWithSpringBootPlugin(String mainClass) {
        Model model = new Model();
        org.apache.maven.model.Build build = new org.apache.maven.model.Build();

        Plugin plugin = new Plugin();
        plugin.setGroupId("org.springframework.boot");
        plugin.setArtifactId("spring-boot-maven-plugin");

        Xpp3Dom config = new Xpp3Dom("configuration");
        Xpp3Dom mainClassNode = new Xpp3Dom("mainClass");
        mainClassNode.setValue(mainClass);
        config.addChild(mainClassNode);

        plugin.setConfiguration(config);
        build.addPlugin(plugin);
        model.setBuild(build);

        return model;
    }
}

