package io.github.tourem.maven.descriptor.service;

import lombok.extern.slf4j.Slf4j;
import org.apache.maven.model.Dependency;
import org.apache.maven.model.Model;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Service to detect Spring Boot profiles from application configuration files.
 * Scans for application-{profile}.properties, application-{profile}.yml, application-{profile}.yaml
 * in the module and its dependencies.
 * @author tourem

 */
@Slf4j
public class SpringBootProfileDetector {

    private static final Pattern PROFILE_PATTERN = Pattern.compile("application-([a-zA-Z0-9_-]+)\\.(properties|yml|yaml)");

    /**
     * Detect all Spring Boot profiles for a module.
     * This includes profiles from:
     * 1. The module's own resources
     * 2. Dependencies' resources (if they are part of the same project)
     *
     * @param modulePath Path to the module directory
     * @param model Maven model of the module
     * @param projectRootPath Root path of the Maven project
     * @return List of detected profile names (sorted, unique)
     */
    public List<String> detectProfiles(Path modulePath, Model model, Path projectRootPath) {
        Set<String> profiles = new HashSet<>();

        // 1. Detect profiles in the module itself
        profiles.addAll(detectProfilesInModule(modulePath));

        // 2. Detect profiles in dependencies (only local modules)
        if (model.getDependencies() != null) {
            for (Dependency dependency : model.getDependencies()) {
                profiles.addAll(detectProfilesInDependency(dependency, projectRootPath));
            }
        }

        List<String> sortedProfiles = new ArrayList<>(profiles);
        Collections.sort(sortedProfiles);

        log.debug("Detected {} profiles for module {}: {}",
            sortedProfiles.size(), modulePath.getFileName(), sortedProfiles);

        return sortedProfiles;
    }

    /**
     * Detect profiles in a specific module's resources directory.
     */
    private Set<String> detectProfilesInModule(Path modulePath) {
        Set<String> profiles = new HashSet<>();

        Path resourcesPath = modulePath.resolve("src/main/resources");
        if (!Files.exists(resourcesPath) || !Files.isDirectory(resourcesPath)) {
            log.debug("No resources directory found at: {}", resourcesPath);
            return profiles;
        }

        try (Stream<Path> files = Files.walk(resourcesPath, 1)) {
            files.filter(Files::isRegularFile)
                .forEach(file -> {
                    String fileName = file.getFileName().toString();
                    Matcher matcher = PROFILE_PATTERN.matcher(fileName);
                    if (matcher.matches()) {
                        String profile = matcher.group(1);
                        profiles.add(profile);
                        log.debug("Found profile '{}' in file: {}", profile, fileName);
                    }
                });
        } catch (IOException e) {
            log.warn("Error scanning resources directory {}: {}", resourcesPath, e.getMessage());
        }

        return profiles;
    }

    /**
     * Detect profiles in a dependency module (if it's a local module in the same project).
     */
    private Set<String> detectProfilesInDependency(Dependency dependency, Path projectRootPath) {
        Set<String> profiles = new HashSet<>();

        // Try to find the dependency as a local module
        // Common patterns: artifactId as directory name, or nested in modules/
        String artifactId = dependency.getArtifactId();

        List<Path> possiblePaths = Arrays.asList(
            projectRootPath.resolve(artifactId),
            projectRootPath.resolve("modules").resolve(artifactId),
            projectRootPath.resolve("libs").resolve(artifactId)
        );

        for (Path possiblePath : possiblePaths) {
            if (Files.exists(possiblePath) && Files.isDirectory(possiblePath)) {
                Path pomFile = possiblePath.resolve("pom.xml");
                if (Files.exists(pomFile)) {
                    log.debug("Found local dependency module: {}", artifactId);
                    profiles.addAll(detectProfilesInModule(possiblePath));
                    break;
                }
            }
        }

        return profiles;
    }

    /**
     * Detect profiles in a module by scanning all subdirectories.
     * This is useful when the exact module structure is unknown.
     */
    public List<String> detectProfilesRecursive(Path projectRootPath) {
        Set<String> profiles = new HashSet<>();

        try (Stream<Path> paths = Files.walk(projectRootPath)) {
            paths.filter(path -> path.toString().contains("src/main/resources"))
                .filter(Files::isDirectory)
                .forEach(resourcesPath -> {
                    try (Stream<Path> files = Files.list(resourcesPath)) {
                        files.filter(Files::isRegularFile)
                            .forEach(file -> {
                                String fileName = file.getFileName().toString();
                                Matcher matcher = PROFILE_PATTERN.matcher(fileName);
                                if (matcher.matches()) {
                                    String profile = matcher.group(1);
                                    profiles.add(profile);
                                }
                            });
                    } catch (IOException e) {
                        log.warn("Error scanning directory {}: {}", resourcesPath, e.getMessage());
                    }
                });
        } catch (IOException e) {
            log.warn("Error walking project tree {}: {}", projectRootPath, e.getMessage());
        }

        return profiles.stream().sorted().toList();
    }
}

