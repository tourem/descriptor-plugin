package io.github.tourem.maven.descriptor.service;

import lombok.extern.slf4j.Slf4j;
import org.apache.maven.model.Dependency;
import org.apache.maven.model.Model;
import org.apache.maven.model.Plugin;
import org.yaml.snakeyaml.Yaml;

import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

/**
 * Service to detect deployment-related metadata from Maven modules.
 * @author tourem

 */
@Slf4j
public class DeploymentMetadataDetector {

    private static final String ACTUATOR_ARTIFACT_ID = "spring-boot-starter-actuator";
    private static final String DEFAULT_ACTUATOR_BASE_PATH = "/actuator";
    private static final String HEALTH_ENDPOINT = "/health";
    private static final String INFO_ENDPOINT = "/info";

    /**
     * Detect Java version from Maven compiler configuration.
     */
    public String detectJavaVersion(Model model) {
        return detectJavaVersion(model, null);
    }

    /**
     * Detect Java version from Maven compiler configuration with parent model support.
     * This method checks the module model first, then falls back to the parent model
     * to resolve inherited properties.
     */
    public String detectJavaVersion(Model model, Model parentModel) {
        // Check maven.compiler.release property (preferred in modern Maven)
        String release = getPropertyWithInheritance(model, parentModel, "maven.compiler.release");
        if (release != null) {
            log.debug("Found Java version from maven.compiler.release: {}", release);
            return release;
        }

        // Check maven.compiler.source property
        String source = getPropertyWithInheritance(model, parentModel, "maven.compiler.source");
        if (source != null) {
            log.debug("Found Java version from maven.compiler.source: {}", source);
            return source;
        }

        // Check maven.compiler.target property
        String target = getPropertyWithInheritance(model, parentModel, "maven.compiler.target");
        if (target != null) {
            log.debug("Found Java version from maven.compiler.target: {}", target);
            return target;
        }

        // Check compiler plugin configuration in module
        if (model.getBuild() != null && model.getBuild().getPlugins() != null) {
            for (Plugin plugin : model.getBuild().getPlugins()) {
                if ("maven-compiler-plugin".equals(plugin.getArtifactId())) {
                    Object config = plugin.getConfiguration();
                    if (config != null) {
                        String version = extractJavaVersionFromPluginConfig(config);
                        if (version != null) {
                            log.debug("Found Java version from compiler plugin: {}", version);
                            return version;
                        }
                    }
                }
            }
        }

        // Check compiler plugin configuration in parent
        if (parentModel != null && parentModel.getBuild() != null && parentModel.getBuild().getPlugins() != null) {
            for (Plugin plugin : parentModel.getBuild().getPlugins()) {
                if ("maven-compiler-plugin".equals(plugin.getArtifactId())) {
                    Object config = plugin.getConfiguration();
                    if (config != null) {
                        String version = extractJavaVersionFromPluginConfig(config);
                        if (version != null) {
                            log.debug("Found Java version from parent compiler plugin: {}", version);
                            return version;
                        }
                    }
                }
            }
        }

        log.debug("No Java version found, using default");
        return null;
    }

    /**
     * Detect main class from Spring Boot plugin configuration.
     */
    public String detectMainClass(Model model) {
        if (model.getBuild() == null || model.getBuild().getPlugins() == null) {
            return null;
        }

        for (Plugin plugin : model.getBuild().getPlugins()) {
            if ("spring-boot-maven-plugin".equals(plugin.getArtifactId())) {
                Object config = plugin.getConfiguration();
                if (config != null) {
                    String mainClass = extractMainClassFromPluginConfig(config);
                    if (mainClass != null) {
                        log.debug("Found main class: {}", mainClass);
                        return mainClass;
                    }
                }
            }
        }

        return null;
    }

    /**
     * Detect server port from application configuration files.
     */
    public Integer detectServerPort(Path modulePath) {
        Path resourcesDir = modulePath.resolve("src/main/resources");
        if (!Files.exists(resourcesDir)) {
            return null;
        }

        // Try application.properties
        Integer port = detectPortFromProperties(resourcesDir.resolve("application.properties"));
        if (port != null) {
            return port;
        }

        // Try application.yml
        port = detectPortFromYaml(resourcesDir.resolve("application.yml"));
        if (port != null) {
            return port;
        }

        // Try application.yaml
        port = detectPortFromYaml(resourcesDir.resolve("application.yaml"));
        if (port != null) {
            return port;
        }

        return null;
    }

    /**
     * Detect if Spring Boot Actuator is enabled.
     */
    public Boolean detectActuatorEnabled(Model model) {
        if (model.getDependencies() == null) {
            return null;
        }

        boolean hasActuator = model.getDependencies().stream()
                .anyMatch(dep -> ACTUATOR_ARTIFACT_ID.equals(dep.getArtifactId()));

        if (hasActuator) {
            log.debug("Spring Boot Actuator detected");
            return true;
        }

        return null;
    }

    /**
     * Detect local module dependencies (dependencies within the same project).
     */
    public List<String> detectLocalDependencies(Model model, String projectGroupId) {
        if (model.getDependencies() == null) {
            return null;
        }

        List<String> localDeps = model.getDependencies().stream()
                .filter(dep -> projectGroupId.equals(dep.getGroupId()))
                .map(Dependency::getArtifactId)
                .sorted()
                .toList();

        if (localDeps.isEmpty()) {
            return null;
        }

        log.debug("Found {} local dependencies: {}", localDeps.size(), localDeps);
        return localDeps;
    }

    /**
     * Detect Actuator base path from application configuration files.
     * Returns the configured base path or the default "/actuator" if Actuator is enabled.
     */
    public String detectActuatorBasePath(Path modulePath, Boolean actuatorEnabled) {
        if (actuatorEnabled == null || !actuatorEnabled) {
            return null;
        }

        Path resourcesDir = modulePath.resolve("src/main/resources");
        if (!Files.exists(resourcesDir)) {
            log.debug("Using default Actuator base path: {}", DEFAULT_ACTUATOR_BASE_PATH);
            return DEFAULT_ACTUATOR_BASE_PATH;
        }

        // Try application.properties
        String basePath = detectActuatorBasePathFromProperties(resourcesDir.resolve("application.properties"));
        if (basePath != null) {
            return basePath;
        }

        // Try application.yml
        basePath = detectActuatorBasePathFromYaml(resourcesDir.resolve("application.yml"));
        if (basePath != null) {
            return basePath;
        }

        // Try application.yaml
        basePath = detectActuatorBasePathFromYaml(resourcesDir.resolve("application.yaml"));
        if (basePath != null) {
            return basePath;
        }

        // Return default if not configured
        log.debug("Using default Actuator base path: {}", DEFAULT_ACTUATOR_BASE_PATH);
        return DEFAULT_ACTUATOR_BASE_PATH;
    }

    /**
     * Build health check endpoint path.
     */
    public String buildActuatorHealthPath(String basePath) {
        if (basePath == null) {
            return null;
        }
        String path = basePath + HEALTH_ENDPOINT;
        log.debug("Built Actuator health path: {}", path);
        return path;
    }

    /**
     * Build info endpoint path.
     */
    public String buildActuatorInfoPath(String basePath) {
        if (basePath == null) {
            return null;
        }
        String path = basePath + INFO_ENDPOINT;
        log.debug("Built Actuator info path: {}", path);
        return path;
    }

    // Helper methods

    private String getProperty(Model model, String propertyName) {
        if (model.getProperties() == null) {
            return null;
        }
        return model.getProperties().getProperty(propertyName);
    }

    /**
     * Get property from model with inheritance support.
     * Checks the module model first, then falls back to parent model.
     */
    private String getPropertyWithInheritance(Model model, Model parentModel, String propertyName) {
        // Check module properties first
        String value = getProperty(model, propertyName);
        if (value != null) {
            return value;
        }

        // Fall back to parent properties
        if (parentModel != null) {
            return getProperty(parentModel, propertyName);
        }

        return null;
    }

    private String extractJavaVersionFromPluginConfig(Object config) {
        if (config instanceof org.codehaus.plexus.util.xml.Xpp3Dom dom) {
            org.codehaus.plexus.util.xml.Xpp3Dom releaseNode = dom.getChild("release");
            if (releaseNode != null && releaseNode.getValue() != null) {
                return releaseNode.getValue();
            }

            org.codehaus.plexus.util.xml.Xpp3Dom sourceNode = dom.getChild("source");
            if (sourceNode != null && sourceNode.getValue() != null) {
                return sourceNode.getValue();
            }

            org.codehaus.plexus.util.xml.Xpp3Dom targetNode = dom.getChild("target");
            if (targetNode != null && targetNode.getValue() != null) {
                return targetNode.getValue();
            }
        }
        return null;
    }

    private String extractMainClassFromPluginConfig(Object config) {
        if (config instanceof org.codehaus.plexus.util.xml.Xpp3Dom dom) {
            org.codehaus.plexus.util.xml.Xpp3Dom mainClassNode = dom.getChild("mainClass");
            if (mainClassNode != null && mainClassNode.getValue() != null) {
                return mainClassNode.getValue();
            }
        }
        return null;
    }

    private Integer detectPortFromProperties(Path propertiesFile) {
        if (!Files.exists(propertiesFile)) {
            return null;
        }

        try (var inputStream = new FileInputStream(propertiesFile.toFile())) {
            Properties props = new Properties();
            props.load(inputStream);

            String port = props.getProperty("server.port");
            if (port != null) {
                log.debug("Found server port in properties: {}", port);
                return Integer.parseInt(port.trim());
            }
        } catch (IOException | NumberFormatException e) {
            log.warn("Error reading port from properties file {}: {}", propertiesFile, e.getMessage());
        }

        return null;
    }

    @SuppressWarnings("unchecked")
    private Integer detectPortFromYaml(Path yamlFile) {
        if (!Files.exists(yamlFile)) {
            return null;
        }

        try (var inputStream = new FileInputStream(yamlFile.toFile())) {
            Yaml yaml = new Yaml();
            Map<String, Object> data = yaml.load(inputStream);

            if (data != null && data.get("server") instanceof Map<?, ?> serverMap) {
                @SuppressWarnings("unchecked")
                Map<String, Object> server = (Map<String, Object>) serverMap;
                if (server.containsKey("port")) {
                    Object portObj = server.get("port");
                    if (portObj instanceof Integer port) {
                        log.debug("Found server port in YAML: {}", port);
                        return port;
                    } else if (portObj instanceof String portStr) {
                        log.debug("Found server port in YAML: {}", portStr);
                        return Integer.parseInt(portStr.trim());
                    }
                }
            }
        } catch (IOException | NumberFormatException e) {
            log.warn("Error reading port from YAML file {}: {}", yamlFile, e.getMessage());
        }

        return null;
    }

    private String detectActuatorBasePathFromProperties(Path propertiesFile) {
        if (!Files.exists(propertiesFile)) {
            return null;
        }

        try (var inputStream = new FileInputStream(propertiesFile.toFile())) {
            Properties props = new Properties();
            props.load(inputStream);

            String basePath = props.getProperty("management.endpoints.web.base-path");
            if (basePath != null) {
                log.debug("Found Actuator base path in properties: {}", basePath);
                return basePath.trim();
            }
        } catch (IOException e) {
            log.warn("Error reading Actuator base path from properties file {}: {}", propertiesFile, e.getMessage());
        }

        return null;
    }

    @SuppressWarnings("unchecked")
    private String detectActuatorBasePathFromYaml(Path yamlFile) {
        if (!Files.exists(yamlFile)) {
            return null;
        }

        try (var inputStream = new FileInputStream(yamlFile.toFile())) {
            Yaml yaml = new Yaml();
            Map<String, Object> data = yaml.load(inputStream);

            if (data != null && data.get("management") instanceof Map<?, ?> managementMap) {
                @SuppressWarnings("unchecked")
                Map<String, Object> management = (Map<String, Object>) managementMap;
                if (management.get("endpoints") instanceof Map<?, ?> endpointsMap) {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> endpoints = (Map<String, Object>) endpointsMap;
                    if (endpoints.get("web") instanceof Map<?, ?> webMap) {
                        @SuppressWarnings("unchecked")
                        Map<String, Object> web = (Map<String, Object>) webMap;
                        if (web.containsKey("base-path")) {
                            Object basePathObj = web.get("base-path");
                            if (basePathObj != null) {
                                String basePath = basePathObj.toString().trim();
                                log.debug("Found Actuator base path in YAML: {}", basePath);
                                return basePath;
                            }
                        }
                    }
                }
            }
        } catch (IOException e) {
            log.warn("Error reading Actuator base path from YAML file {}: {}", yamlFile, e.getMessage());
        }

        return null;
    }
}

