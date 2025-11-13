package io.github.tourem.maven.descriptor.service;

import io.github.tourem.maven.descriptor.model.AssemblyArtifact;
import org.apache.maven.model.Build;
import org.apache.maven.model.Model;
import org.apache.maven.model.Plugin;
import org.apache.maven.model.PluginExecution;
import org.codehaus.plexus.util.xml.Xpp3Dom;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class MavenAssemblyDetectorTest {

    private MavenAssemblyDetector detector;
    private MavenRepositoryPathGenerator pathGenerator;

    @BeforeEach
    void setUp() {
        pathGenerator = new MavenRepositoryPathGenerator();
        detector = new MavenAssemblyDetector(pathGenerator);
    }

    @Test
    void shouldReturnEmptyListWhenNoAssemblyPlugin() {
        // Given
        Model model = new Model();
        model.setBuild(new Build());
        Path modulePath = Path.of(".");

        // When
        List<AssemblyArtifact> assemblies = detector.detectAssemblies(
                modulePath, model, "com.test", "my-app", "1.0.0");

        // Then
        assertThat(assemblies).isEmpty();
    }

    @Test
    void shouldReturnEmptyListWhenAssemblyPluginHasNoExecutions() {
        // Given
        Model model = createModelWithAssemblyPlugin();
        Path modulePath = Path.of(".");

        // When
        List<AssemblyArtifact> assemblies = detector.detectAssemblies(
                modulePath, model, "com.test", "my-app", "1.0.0");

        // Then
        assertThat(assemblies).isEmpty();
    }

    @Test
    void shouldDetectAssemblyWithDescriptorRef(@TempDir Path tempDir) {
        // Given
        Model model = createModelWithDescriptorRef("jar-with-dependencies");

        // When
        List<AssemblyArtifact> assemblies = detector.detectAssemblies(
                tempDir, model, "com.test", "my-app", "1.0.0");

        // Then
        assertThat(assemblies).hasSize(1);
        AssemblyArtifact assembly = assemblies.get(0);
        assertThat(assembly.assemblyId()).isEqualTo("jar-with-dependencies");
        assertThat(assembly.format()).isEqualTo("zip");
        assertThat(assembly.repositoryPath())
                .isEqualTo("com/test/my-app/1.0.0/my-app-1.0.0-jar-with-dependencies.zip");
    }

    @Test
    void shouldDetectAssemblyWithDescriptorRefAndAppendAssemblyIdFalse(@TempDir Path tempDir) {
        // Given
        Model model = createModelWithDescriptorRef("jar-with-dependencies", false);

        // When
        List<AssemblyArtifact> assemblies = detector.detectAssemblies(
                tempDir, model, "com.test", "my-app", "1.0.0");

        // Then
        assertThat(assemblies).hasSize(1);
        AssemblyArtifact assembly = assemblies.get(0);
        assertThat(assembly.assemblyId()).isEqualTo("jar-with-dependencies");
        assertThat(assembly.format()).isEqualTo("zip");
        assertThat(assembly.repositoryPath())
                .isEqualTo("com/test/my-app/1.0.0/my-app-1.0.0.zip");
    }

    @Test
    void shouldDetectAssemblyFromDescriptorFile(@TempDir Path tempDir) throws IOException {
        // Given
        createAssemblyDescriptor(tempDir, "conf-dev.xml", "conf-dev", "zip");
        Model model = createModelWithDescriptor("src/assembly/conf-dev.xml");

        // When
        List<AssemblyArtifact> assemblies = detector.detectAssemblies(
                tempDir, model, "com.larbotech", "task-batch", "1.0-SNAPSHOT");

        // Then
        assertThat(assemblies).hasSize(1);
        AssemblyArtifact assembly = assemblies.get(0);
        assertThat(assembly.assemblyId()).isEqualTo("conf-dev");
        assertThat(assembly.format()).isEqualTo("zip");
        assertThat(assembly.repositoryPath())
                .isEqualTo("com/larbotech/task-batch/1.0-SNAPSHOT/task-batch-1.0-SNAPSHOT-conf-dev.zip");
    }

    @Test
    void shouldDetectMultipleAssembliesFromDescriptorFiles(@TempDir Path tempDir) throws IOException {
        // Given
        createAssemblyDescriptor(tempDir, "conf-dev.xml", "conf-dev", "zip");
        createAssemblyDescriptor(tempDir, "conf-prd.xml", "conf-prd", "zip");
        createAssemblyDescriptor(tempDir, "distribution.xml", "distribution", "tar.gz");

        Model model = createModelWithMultipleDescriptors(
                "src/assembly/conf-dev.xml",
                "src/assembly/conf-prd.xml",
                "src/assembly/distribution.xml"
        );

        // When
        List<AssemblyArtifact> assemblies = detector.detectAssemblies(
                tempDir, model, "com.larbotech", "task-api", "1.0-SNAPSHOT");

        // Then
        assertThat(assemblies).hasSize(3);
        assertThat(assemblies)
                .extracting(AssemblyArtifact::assemblyId)
                .containsExactlyInAnyOrder("conf-dev", "conf-prd", "distribution");
        assertThat(assemblies)
                .extracting(AssemblyArtifact::format)
                .containsExactlyInAnyOrder("zip", "zip", "tar.gz");
    }

    @Test
    void shouldHandleAssemblyWithMultipleFormats(@TempDir Path tempDir) throws IOException {
        // Given
        String descriptorContent = """
                <assembly>
                    <id>multi-format</id>
                    <formats>
                        <format>zip</format>
                        <format>tar.gz</format>
                        <format>tar.bz2</format>
                    </formats>
                </assembly>
                """;
        Files.createDirectories(tempDir.resolve("src/assembly"));
        Files.writeString(tempDir.resolve("src/assembly/multi.xml"), descriptorContent);

        Model model = createModelWithDescriptor("src/assembly/multi.xml");

        // When
        List<AssemblyArtifact> assemblies = detector.detectAssemblies(
                tempDir, model, "com.test", "my-app", "2.0.0");

        // Then
        assertThat(assemblies).hasSize(3);
        assertThat(assemblies)
                .extracting(AssemblyArtifact::format)
                .containsExactlyInAnyOrder("zip", "tar.gz", "tar.bz2");
        assertThat(assemblies)
                .allMatch(a -> a.assemblyId().equals("multi-format"));
    }

    @Test
    void shouldDefaultToZipWhenNoFormatSpecified(@TempDir Path tempDir) throws IOException {
        // Given
        String descriptorContent = """
                <assembly>
                    <id>no-format</id>
                </assembly>
                """;
        Files.createDirectories(tempDir.resolve("src/assembly"));
        Files.writeString(tempDir.resolve("src/assembly/no-format.xml"), descriptorContent);

        Model model = createModelWithDescriptor("src/assembly/no-format.xml");

        // When
        List<AssemblyArtifact> assemblies = detector.detectAssemblies(
                tempDir, model, "com.test", "my-app", "1.0.0");

        // Then
        assertThat(assemblies).hasSize(1);
        assertThat(assemblies.get(0).format()).isEqualTo("zip");
    }

    @Test
    void shouldHandleMissingDescriptorFile(@TempDir Path tempDir) {
        // Given
        Model model = createModelWithDescriptor("src/assembly/missing.xml");

        // When
        List<AssemblyArtifact> assemblies = detector.detectAssemblies(
                tempDir, model, "com.test", "my-app", "1.0.0");

        // Then
        assertThat(assemblies).isEmpty();
    }

    @Test
    void shouldRespectAppendAssemblyIdFalse(@TempDir Path tempDir) throws IOException {
        // Given
        createAssemblyDescriptor(tempDir, "distribution.xml", "distribution", "zip");
        Model model = createModelWithDescriptor("src/assembly/distribution.xml", false);

        // When
        List<AssemblyArtifact> assemblies = detector.detectAssemblies(
                tempDir, model, "com.larbotech", "task-batch", "1.0-SNAPSHOT");

        // Then
        assertThat(assemblies).hasSize(1);
        AssemblyArtifact assembly = assemblies.get(0);
        assertThat(assembly.repositoryPath())
                .isEqualTo("com/larbotech/task-batch/1.0-SNAPSHOT/task-batch-1.0-SNAPSHOT.zip");
    }

    // Helper methods

    private Model createModelWithAssemblyPlugin() {
        Model model = new Model();
        Build build = new Build();
        Plugin plugin = new Plugin();
        plugin.setArtifactId("maven-assembly-plugin");
        build.addPlugin(plugin);
        model.setBuild(build);
        return model;
    }

    private Model createModelWithDescriptorRef(String descriptorRef) {
        return createModelWithDescriptorRef(descriptorRef, true);
    }

    private Model createModelWithDescriptorRef(String descriptorRef, boolean appendAssemblyId) {
        Model model = createModelWithAssemblyPlugin();
        Plugin plugin = model.getBuild().getPlugins().get(0);

        PluginExecution execution = new PluginExecution();
        execution.setId("make-assembly");

        Xpp3Dom config = new Xpp3Dom("configuration");
        Xpp3Dom descriptorRefs = new Xpp3Dom("descriptorRefs");
        Xpp3Dom descriptorRefNode = new Xpp3Dom("descriptorRef");
        descriptorRefNode.setValue(descriptorRef);
        descriptorRefs.addChild(descriptorRefNode);
        config.addChild(descriptorRefs);

        if (!appendAssemblyId) {
            Xpp3Dom appendNode = new Xpp3Dom("appendAssemblyId");
            appendNode.setValue("false");
            config.addChild(appendNode);
        }

        execution.setConfiguration(config);
        plugin.addExecution(execution);

        return model;
    }

    private Model createModelWithDescriptor(String descriptorPath) {
        return createModelWithDescriptor(descriptorPath, true);
    }

    private Model createModelWithDescriptor(String descriptorPath, boolean appendAssemblyId) {
        Model model = createModelWithAssemblyPlugin();
        Plugin plugin = model.getBuild().getPlugins().get(0);

        PluginExecution execution = new PluginExecution();
        execution.setId("make-assembly");

        Xpp3Dom config = new Xpp3Dom("configuration");
        Xpp3Dom descriptors = new Xpp3Dom("descriptors");
        Xpp3Dom descriptorNode = new Xpp3Dom("descriptor");
        descriptorNode.setValue(descriptorPath);
        descriptors.addChild(descriptorNode);
        config.addChild(descriptors);

        if (!appendAssemblyId) {
            Xpp3Dom appendNode = new Xpp3Dom("appendAssemblyId");
            appendNode.setValue("false");
            config.addChild(appendNode);
        }

        execution.setConfiguration(config);
        plugin.addExecution(execution);

        return model;
    }

    private Model createModelWithMultipleDescriptors(String... descriptorPaths) {
        Model model = createModelWithAssemblyPlugin();
        Plugin plugin = model.getBuild().getPlugins().get(0);

        for (String descriptorPath : descriptorPaths) {
            PluginExecution execution = new PluginExecution();
            execution.setId("make-assembly-" + descriptorPath.hashCode());

            Xpp3Dom config = new Xpp3Dom("configuration");
            Xpp3Dom descriptors = new Xpp3Dom("descriptors");
            Xpp3Dom descriptorNode = new Xpp3Dom("descriptor");
            descriptorNode.setValue(descriptorPath);
            descriptors.addChild(descriptorNode);
            config.addChild(descriptors);

            execution.setConfiguration(config);
            plugin.addExecution(execution);
        }

        return model;
    }

    private void createAssemblyDescriptor(Path tempDir, String filename, String id, String format) throws IOException {
        String content = String.format("""
                <assembly>
                    <id>%s</id>
                    <formats>
                        <format>%s</format>
                    </formats>
                </assembly>
                """, id, format);

        Path assemblyDir = tempDir.resolve("src/assembly");
        Files.createDirectories(assemblyDir);
        Files.writeString(assemblyDir.resolve(filename), content);
    }
}
