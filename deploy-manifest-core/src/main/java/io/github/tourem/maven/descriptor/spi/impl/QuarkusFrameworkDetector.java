package io.github.tourem.maven.descriptor.spi.impl;

import io.github.tourem.maven.descriptor.model.DeployableModule;
import io.github.tourem.maven.descriptor.spi.FrameworkDetector;
import lombok.extern.slf4j.Slf4j;
import org.apache.maven.model.Dependency;
import org.apache.maven.model.Model;
import org.apache.maven.model.Plugin;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.stream.Stream;

/**
 * Framework detector for Quarkus applications.
 *
 * This is an example implementation showing how to extend the plugin
 * with support for Quarkus framework.
 * @author tourem

 */
@Slf4j
public class QuarkusFrameworkDetector implements FrameworkDetector {

    @Override
    public String getFrameworkName() {
        return "Quarkus";
    }

    @Override
    public boolean isApplicable(Model model, Path modulePath) {
        // Check for Quarkus dependencies
        if (model.getDependencies() != null) {
            boolean hasQuarkusDep = model.getDependencies().stream()
                .anyMatch(d -> "io.quarkus".equals(d.getGroupId()));
            if (hasQuarkusDep) {
                return true;
            }
        }

        // Check for Quarkus plugin
        if (model.getBuild() != null && model.getBuild().getPlugins() != null) {
            return model.getBuild().getPlugins().stream()
                .anyMatch(p -> "io.quarkus".equals(p.getGroupId()) &&
                              "quarkus-maven-plugin".equals(p.getArtifactId()));
        }

        return false;
    }

    @Override
    public void enrichModule(DeployableModule.DeployableModuleBuilder builder,
                            Model model,
                            Path modulePath,
                            Path projectRoot) {
        log.debug("Enriching module with Quarkus metadata: {}", model.getArtifactId());

        // Detect Quarkus version
        String quarkusVersion = detectQuarkusVersion(model);

        // Detect build mode (native vs JVM)
        boolean isNative = isNativeBuild(model);

        // Detect Quarkus profiles
        List<String> profiles = detectQuarkusProfiles(modulePath);

        // Add Quarkus-specific build plugins
        List<String> buildPlugins = new ArrayList<>(builder.build().getBuildPlugins());
        buildPlugins.add("quarkus-maven-plugin");
        builder.buildPlugins(buildPlugins);

        // Note: In a real implementation, you would add these to a custom
        // QuarkusMetadata object and include it in the module
        log.info("Detected Quarkus application: version={}, native={}, profiles={}",
                quarkusVersion, isNative, profiles);
    }

    @Override
    public int getPriority() {
        return 90; // Slightly lower than Spring Boot
    }

    /**
     * Detect Quarkus version from dependencies.
     */
    private String detectQuarkusVersion(Model model) {
        if (model.getDependencies() != null) {
            return model.getDependencies().stream()
                .filter(d -> "io.quarkus".equals(d.getGroupId()))
                .map(Dependency::getVersion)
                .filter(v -> v != null && !v.isEmpty())
                .findFirst()
                .orElse(null);
        }
        return null;
    }

    /**
     * Check if native build is configured.
     */
    private boolean isNativeBuild(Model model) {
        if (model.getProfiles() != null) {
            return model.getProfiles().stream()
                .anyMatch(p -> "native".equals(p.getId()));
        }
        return false;
    }

    /**
     * Detect Quarkus profiles from application.properties.
     */
    private List<String> detectQuarkusProfiles(Path modulePath) {
        List<String> profiles = new ArrayList<>();

        // Check src/main/resources for application-{profile}.properties
        Path resourcesDir = modulePath.resolve("src/main/resources");
        if (Files.exists(resourcesDir)) {
            try (Stream<Path> files = Files.list(resourcesDir)) {
                files.filter(f -> f.getFileName().toString().matches("application-.*\\.properties"))
                     .forEach(f -> {
                         String fileName = f.getFileName().toString();
                         String profile = fileName.substring("application-".length(),
                                                            fileName.length() - ".properties".length());
                         profiles.add(profile);
                     });
            } catch (IOException e) {
                log.warn("Failed to scan Quarkus profiles: {}", e.getMessage());
            }
        }

        return profiles;
    }
}

