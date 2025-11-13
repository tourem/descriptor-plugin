package io.github.tourem.maven.descriptor.service;

import io.github.tourem.maven.descriptor.model.AssemblyArtifact;
import lombok.extern.slf4j.Slf4j;
import org.apache.maven.model.Model;
import org.apache.maven.model.Plugin;
import org.apache.maven.model.PluginExecution;
import org.codehaus.plexus.util.xml.Xpp3Dom;
import org.codehaus.plexus.util.xml.Xpp3DomBuilder;

import java.io.FileReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Service to detect Maven Assembly Plugin configurations and extract assembly artifacts.
 * @author tourem

 */
@Slf4j
public class MavenAssemblyDetector {

    private static final String ASSEMBLY_PLUGIN_ARTIFACT_ID = "maven-assembly-plugin";
    private final MavenRepositoryPathGenerator pathGenerator;

    /**
     * Constructor with dependencies.
     */
    public MavenAssemblyDetector(MavenRepositoryPathGenerator pathGenerator) {
        this.pathGenerator = pathGenerator;
    }

    /**
     * Detect all assembly artifacts configured in a Maven module.
     *
     * @param modulePath Path to the module directory
     * @param model Maven model of the module
     * @param groupId Module groupId
     * @param artifactId Module artifactId
     * @param version Module version
     * @return List of assembly artifacts
     */
    public List<AssemblyArtifact> detectAssemblies(Path modulePath, Model model,
                                                    String groupId, String artifactId, String version) {
        List<AssemblyArtifact> assemblies = new ArrayList<>();

        Plugin assemblyPlugin = findAssemblyPlugin(model);
        if (assemblyPlugin == null) {
            log.debug("No maven-assembly-plugin found in module: {}", artifactId);
            return assemblies;
        }

        log.debug("Found maven-assembly-plugin in module: {}", artifactId);

        // Process each execution
        if (assemblyPlugin.getExecutions() != null) {
            for (PluginExecution execution : assemblyPlugin.getExecutions()) {
                assemblies.addAll(processExecution(execution, modulePath, groupId, artifactId, version));
            }
        }

        return assemblies;
    }

    /**
     * Find the maven-assembly-plugin in the model.
     */
    private Plugin findAssemblyPlugin(Model model) {
        if (model.getBuild() != null && model.getBuild().getPlugins() != null) {
            for (Plugin plugin : model.getBuild().getPlugins()) {
                if (ASSEMBLY_PLUGIN_ARTIFACT_ID.equals(plugin.getArtifactId())) {
                    return plugin;
                }
            }
        }
        return null;
    }

    /**
     * Process a plugin execution to extract assembly configurations.
     */
    private List<AssemblyArtifact> processExecution(PluginExecution execution, Path modulePath,
                                                     String groupId, String artifactId, String version) {
        List<AssemblyArtifact> assemblies = new ArrayList<>();

        Object config = execution.getConfiguration();
        if (!(config instanceof Xpp3Dom)) {
            return assemblies;
        }

        Xpp3Dom configuration = (Xpp3Dom) config;

        // Check if appendAssemblyId is false (assembly ID won't be in filename)
        boolean appendAssemblyId = true;
        Xpp3Dom appendNode = configuration.getChild("appendAssemblyId");
        if (appendNode != null && "false".equals(appendNode.getValue())) {
            appendAssemblyId = false;
        }

        // Get descriptors
        Xpp3Dom descriptorsNode = configuration.getChild("descriptors");
        if (descriptorsNode != null) {
            Xpp3Dom[] descriptorNodes = descriptorsNode.getChildren("descriptor");
            for (Xpp3Dom descriptorNode : descriptorNodes) {
                String descriptorPath = descriptorNode.getValue();
                assemblies.addAll(parseAssemblyDescriptor(modulePath, descriptorPath,
                                                          groupId, artifactId, version, appendAssemblyId));
            }
        }

        // Get descriptorRefs (predefined descriptors like jar-with-dependencies)
        Xpp3Dom descriptorRefsNode = configuration.getChild("descriptorRefs");
        if (descriptorRefsNode != null) {
            Xpp3Dom[] descriptorRefNodes = descriptorRefsNode.getChildren("descriptorRef");
            for (Xpp3Dom descriptorRefNode : descriptorRefNodes) {
                String descriptorRef = descriptorRefNode.getValue();
                // For descriptorRefs, we assume zip format and use the ref as ID
                assemblies.add(createAssemblyArtifact(descriptorRef, "zip",
                                                      groupId, artifactId, version, appendAssemblyId));
            }
        }

        return assemblies;
    }

    /**
     * Parse an assembly descriptor XML file to extract ID and format.
     */
    private List<AssemblyArtifact> parseAssemblyDescriptor(Path modulePath, String descriptorPath,
                                                           String groupId, String artifactId,
                                                           String version, boolean appendAssemblyId) {
        List<AssemblyArtifact> assemblies = new ArrayList<>();

        Path descriptorFile = modulePath.resolve(descriptorPath);
        if (!Files.exists(descriptorFile)) {
            log.warn("Assembly descriptor not found: {}", descriptorFile);
            return assemblies;
        }

        try (FileReader reader = new FileReader(descriptorFile.toFile())) {
            Xpp3Dom dom = Xpp3DomBuilder.build(reader);

            // Extract assembly ID
            Xpp3Dom idNode = dom.getChild("id");
            String assemblyId = idNode != null ? idNode.getValue() : "assembly";

            // Extract formats
            Xpp3Dom formatsNode = dom.getChild("formats");
            if (formatsNode != null) {
                Xpp3Dom[] formatNodes = formatsNode.getChildren("format");
                for (Xpp3Dom formatNode : formatNodes) {
                    String format = formatNode.getValue();
                    assemblies.add(createAssemblyArtifact(assemblyId, format,
                                                          groupId, artifactId, version, appendAssemblyId));
                }
            } else {
                // Default to zip if no format specified
                assemblies.add(createAssemblyArtifact(assemblyId, "zip",
                                                      groupId, artifactId, version, appendAssemblyId));
            }

            log.debug("Parsed assembly descriptor: {} with ID: {}", descriptorPath, assemblyId);

        } catch (Exception e) {
            log.warn("Error parsing assembly descriptor {}: {}", descriptorFile, e.getMessage());
        }

        return assemblies;
    }

    /**
     * Create an AssemblyArtifact with repository path.
     */
    private AssemblyArtifact createAssemblyArtifact(String assemblyId, String format,
                                                    String groupId, String artifactId,
                                                    String version, boolean appendAssemblyId) {
        // Generate repository path
        String classifier = appendAssemblyId ? assemblyId : null;
        String repositoryPath = pathGenerator.generatePath(groupId, artifactId, version,
                                                           null, format, classifier);

        log.debug("Created assembly artifact: {} with path: {}", assemblyId, repositoryPath);

        return AssemblyArtifact.builder()
                .assemblyId(assemblyId)
                .format(format)
                .repositoryPath(repositoryPath)
                .build();
    }
}

