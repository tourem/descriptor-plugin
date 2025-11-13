package io.github.tourem.maven.descriptor.service;

import lombok.extern.slf4j.Slf4j;

/**
 * Service to generate Maven repository paths for artifacts.
 * Follows the standard Maven repository layout:
 * groupId/artifactId/version/artifactId-version[-classifier].extension
 * @author tourem

 */
@Slf4j
public class MavenRepositoryPathGenerator {

    /**
     * Generate the repository path for an artifact.
     *
     * @param groupId Maven groupId
     * @param artifactId Maven artifactId
     * @param version Maven version
     * @param finalName Final name of the artifact (may be customized)
     * @param packaging Packaging type (jar, war, ear)
     * @param classifier Optional classifier (e.g., "exec", "sources")
     * @return Repository path in format: com/larbotech/task/1.1.2-SNAPSHOT/task-1.1.2-SNAPSHOT.jar
     */
    public String generatePath(String groupId, String artifactId, String version,
                              String finalName, String packaging, String classifier) {

        if (groupId == null || artifactId == null || version == null) {
            throw new IllegalArgumentException("groupId, artifactId, and version are required");
        }

        // Convert groupId to path (replace dots with slashes)
        String groupPath = groupId.replace('.', '/');

        // Build the artifact filename
        String filename = buildFilename(artifactId, version, finalName, packaging, classifier);

        // Combine into full path
        String fullPath = String.format("%s/%s/%s/%s", groupPath, artifactId, version, filename);

        log.debug("Generated repository path: {}", fullPath);

        return fullPath;
    }

    /**
     * Build the artifact filename.
     * Format: artifactId-version[-classifier].extension
     *
     * Note: Maven repositories (Nexus, JFrog) always use the standard naming convention
     * artifactId-version.extension, regardless of the finalName in the build configuration.
     * The finalName only affects the local build output in target/, not the deployed artifact.
     */
    private String buildFilename(String artifactId, String version, String finalName,
                                 String packaging, String classifier) {

        String extension = packaging != null ? packaging : "jar";

        // Maven repositories always use standard naming: artifactId-version
        String baseName = artifactId + "-" + version;

        // Add classifier if present
        if (classifier != null && !classifier.isEmpty()) {
            baseName = baseName + "-" + classifier;
        }

        // Add extension
        return baseName + "." + extension;
    }

    /**
     * Generate path using default final name (artifactId-version).
     */
    public String generatePath(String groupId, String artifactId, String version, String packaging) {
        return generatePath(groupId, artifactId, version, null, packaging, null);
    }

    /**
     * Generate path with classifier but default final name.
     */
    public String generatePathWithClassifier(String groupId, String artifactId, String version,
                                            String packaging, String classifier) {
        return generatePath(groupId, artifactId, version, null, packaging, classifier);
    }
}

