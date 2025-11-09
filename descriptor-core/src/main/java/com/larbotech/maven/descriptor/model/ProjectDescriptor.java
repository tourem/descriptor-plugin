package com.larbotech.maven.descriptor.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Root descriptor for a Maven project containing all deployable modules.
 *
 * @param projectGroupId Root project groupId
 * @param projectArtifactId Root project artifactId
 * @param projectVersion Root project version
 * @param projectName Project name
 * @param projectDescription Project description
 * @param generatedAt Timestamp when the descriptor was generated
 * @param deployableModules List of all deployable modules found in the project
 * @param totalModules Total number of modules analyzed (including non-deployable)
 * @param deployableModulesCount Number of deployable modules
 * @param buildInfo Git and CI/CD metadata for traceability
 * @param mavenRepositoryUrl Base URL of the Maven repository (from distributionManagement)
 */
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ProjectDescriptor(
    String projectGroupId,
    String projectArtifactId,
    String projectVersion,
    String projectName,
    String projectDescription,
    LocalDateTime generatedAt,
    List<DeployableModule> deployableModules,
    int totalModules,
    int deployableModulesCount,
    BuildInfo buildInfo,
    String mavenRepositoryUrl
) {}

