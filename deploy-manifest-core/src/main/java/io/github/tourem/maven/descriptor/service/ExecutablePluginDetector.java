package io.github.tourem.maven.descriptor.service;

import lombok.extern.slf4j.Slf4j;
import org.apache.maven.model.Model;
import org.apache.maven.model.Plugin;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Service to detect Maven plugins that indicate a module is potentially executable.
 * This includes plugins like spring-boot-maven-plugin, quarkus-maven-plugin, maven-shade-plugin, etc.
 * @author tourem

 */
@Slf4j
public class ExecutablePluginDetector {

    /**
     * Map of plugin artifact IDs to their common group IDs.
     * Some plugins can have multiple group IDs (e.g., different organizations).
     */
    private static final Map<String, List<String>> EXECUTABLE_PLUGINS = Map.of(
        "spring-boot-maven-plugin", List.of("org.springframework.boot"),
        "quarkus-maven-plugin", List.of("io.quarkus", "io.quarkus.platform"),
        "maven-shade-plugin", List.of("org.apache.maven.plugins"),
        "maven-assembly-plugin", List.of("org.apache.maven.plugins"),
        "jib-maven-plugin", List.of("com.google.cloud.tools"),
        "dockerfile-maven-plugin", List.of("com.spotify")
    );

    /**
     * Detect all executable-related plugins configured in a Maven module.
     * Returns a list of plugin artifact IDs that were found.
     *
     * @param model Maven model to analyze
     * @return List of detected plugin artifact IDs (e.g., "spring-boot-maven-plugin", "quarkus-maven-plugin")
     */
    public List<String> detectExecutablePlugins(Model model) {
        List<String> detectedPlugins = new ArrayList<>();

        if (model.getBuild() == null) {
            return detectedPlugins;
        }

        // Check build plugins
        if (model.getBuild().getPlugins() != null) {
            for (Plugin plugin : model.getBuild().getPlugins()) {
                String pluginId = matchExecutablePlugin(plugin);
                if (pluginId != null && !detectedPlugins.contains(pluginId)) {
                    detectedPlugins.add(pluginId);
                    log.debug("Found executable plugin '{}' in module: {}", pluginId, model.getArtifactId());
                }
            }
        }

        // Also check plugin management (plugins may be declared there)
        if (model.getBuild().getPluginManagement() != null &&
            model.getBuild().getPluginManagement().getPlugins() != null) {
            for (Plugin plugin : model.getBuild().getPluginManagement().getPlugins()) {
                String pluginId = matchExecutablePlugin(plugin);
                if (pluginId != null && !detectedPlugins.contains(pluginId)) {
                    detectedPlugins.add(pluginId);
                    log.debug("Found executable plugin '{}' in plugin management for module: {}",
                             pluginId, model.getArtifactId());
                }
            }
        }

        return detectedPlugins;
    }

    /**
     * Check if a plugin matches one of the known executable plugins.
     * Returns the plugin artifact ID if matched, null otherwise.
     */
    private String matchExecutablePlugin(Plugin plugin) {
        String artifactId = plugin.getArtifactId();

        // Check if this artifact ID is in our list of executable plugins
        if (!EXECUTABLE_PLUGINS.containsKey(artifactId)) {
            return null;
        }

        // Get the plugin's group ID (default to org.apache.maven.plugins if not specified)
        String groupId = plugin.getGroupId() != null ?
                        plugin.getGroupId() : "org.apache.maven.plugins";

        // Verify the group ID matches one of the expected group IDs for this plugin
        List<String> expectedGroupIds = EXECUTABLE_PLUGINS.get(artifactId);
        if (expectedGroupIds.contains(groupId)) {
            return artifactId;
        }

        return null;
    }

    /**
     * Check if a module has any executable plugins configured.
     *
     * @param model Maven model to check
     * @return true if at least one executable plugin is found
     */
    public boolean hasExecutablePlugins(Model model) {
        return !detectExecutablePlugins(model).isEmpty();
    }
}

