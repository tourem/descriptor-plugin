package io.github.tourem.maven.descriptor.service;

import lombok.extern.slf4j.Slf4j;
import org.apache.maven.model.Model;
import org.apache.maven.model.Plugin;
import org.codehaus.plexus.util.xml.Xpp3Dom;

/**
 * Service to detect Spring Boot executables in Maven projects.
 * @author tourem

 */
@Slf4j
public class SpringBootDetector {

    private static final String SPRING_BOOT_PLUGIN_GROUP_ID = "org.springframework.boot";
    private static final String SPRING_BOOT_PLUGIN_ARTIFACT_ID = "spring-boot-maven-plugin";

    /**
     * Check if a module is a Spring Boot executable.
     * A module is considered a Spring Boot executable if it has the spring-boot-maven-plugin
     * configured in its build plugins.
     *
     * @param model Maven model to check
     * @return true if this is a Spring Boot executable
     */
    public boolean isSpringBootExecutable(Model model) {
        if (model.getBuild() == null || model.getBuild().getPlugins() == null) {
            return false;
        }

        for (Plugin plugin : model.getBuild().getPlugins()) {
            if (isSpringBootPlugin(plugin)) {
                log.debug("Found Spring Boot plugin in module: {}", model.getArtifactId());
                return true;
            }
        }

        // Also check plugin management
        if (model.getBuild().getPluginManagement() != null &&
            model.getBuild().getPluginManagement().getPlugins() != null) {
            for (Plugin plugin : model.getBuild().getPluginManagement().getPlugins()) {
                if (isSpringBootPlugin(plugin)) {
                    log.debug("Found Spring Boot plugin in plugin management for module: {}",
                             model.getArtifactId());
                    return true;
                }
            }
        }

        return false;
    }

    /**
     * Get the classifier configured in Spring Boot plugin if any.
     * Some configurations use a classifier like "exec" for the executable jar.
     *
     * @param model Maven model
     * @return classifier or null
     */
    public String getSpringBootClassifier(Model model) {
        if (model.getBuild() == null || model.getBuild().getPlugins() == null) {
            return null;
        }

        for (Plugin plugin : model.getBuild().getPlugins()) {
            if (isSpringBootPlugin(plugin)) {
                return extractClassifier(plugin);
            }
        }

        return null;
    }

    /**
     * Get the custom final name from Spring Boot plugin configuration if any.
     *
     * @param model Maven model
     * @return custom final name or null
     */
    public String getSpringBootFinalName(Model model) {
        if (model.getBuild() == null || model.getBuild().getPlugins() == null) {
            return null;
        }

        for (Plugin plugin : model.getBuild().getPlugins()) {
            if (isSpringBootPlugin(plugin)) {
                return extractFinalName(plugin);
            }
        }

        return null;
    }

    /**
     * Check if a plugin is the Spring Boot Maven plugin.
     */
    private boolean isSpringBootPlugin(Plugin plugin) {
        String groupId = plugin.getGroupId() != null ? plugin.getGroupId() : "org.apache.maven.plugins";
        return SPRING_BOOT_PLUGIN_GROUP_ID.equals(groupId) &&
               SPRING_BOOT_PLUGIN_ARTIFACT_ID.equals(plugin.getArtifactId());
    }

    /**
     * Extract classifier from plugin configuration.
     * Example configuration:
     * <configuration>
     *   <classifier>exec</classifier>
     * </configuration>
     */
    private String extractClassifier(Plugin plugin) {
        if (plugin.getConfiguration() == null) {
            return null;
        }

        try {
            Xpp3Dom config = (Xpp3Dom) plugin.getConfiguration();
            Xpp3Dom classifierNode = config.getChild("classifier");
            if (classifierNode != null) {
                return classifierNode.getValue();
            }
        } catch (Exception e) {
            log.warn("Error extracting classifier from Spring Boot plugin configuration", e);
        }

        return null;
    }

    /**
     * Extract final name from plugin configuration.
     * Example configuration:
     * <configuration>
     *   <finalName>custom-name</finalName>
     * </configuration>
     */
    private String extractFinalName(Plugin plugin) {
        if (plugin.getConfiguration() == null) {
            return null;
        }

        try {
            Xpp3Dom config = (Xpp3Dom) plugin.getConfiguration();
            Xpp3Dom finalNameNode = config.getChild("finalName");
            if (finalNameNode != null) {
                return finalNameNode.getValue();
            }
        } catch (Exception e) {
            log.warn("Error extracting finalName from Spring Boot plugin configuration", e);
        }

        return null;
    }
}

