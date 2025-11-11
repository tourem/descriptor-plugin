package io.github.tourem.maven.descriptor.service;

import io.github.tourem.maven.descriptor.model.EnvironmentConfig;
import lombok.extern.slf4j.Slf4j;
import org.yaml.snakeyaml.Yaml;

import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

/**
 * Service to detect environment-specific configurations from Spring Boot configuration files.
 * Reads both common application.yml/properties and profile-specific files.
 */
@Slf4j
public class EnvironmentConfigDetector {

    private static final String DEFAULT_ACTUATOR_BASE_PATH = "/actuator";
    private static final String HEALTH_ENDPOINT = "/health";
    private static final String INFO_ENDPOINT = "/info";

    /**
     * Detect environment configurations for all profiles.
     * Reads common configuration first, then profile-specific overrides.
     */
    public List<EnvironmentConfig> detectEnvironmentConfigs(Path modulePath, List<String> profiles, Boolean actuatorEnabled) {
        if (profiles == null || profiles.isEmpty()) {
            return null;
        }

        Path resourcesDir = modulePath.resolve("src/main/resources");
        if (!Files.exists(resourcesDir)) {
            log.debug("No resources directory found at: {}", resourcesDir);
            return null;
        }

        // Read common configuration (application.yml/properties)
        Map<String, Object> commonConfig = readCommonConfiguration(resourcesDir);

        List<EnvironmentConfig> configs = new ArrayList<>();
        for (String profile : profiles) {
            EnvironmentConfig config = detectEnvironmentConfig(resourcesDir, profile, commonConfig, actuatorEnabled);
            if (config != null) {
                configs.add(config);
            }
        }

        return configs.isEmpty() ? null : configs;
    }

    /**
     * Read common configuration from application.yml/properties.
     */
    private Map<String, Object> readCommonConfiguration(Path resourcesDir) {
        Map<String, Object> config = new HashMap<>();

        // Try application.yml
        Path yamlFile = resourcesDir.resolve("application.yml");
        if (Files.exists(yamlFile)) {
            Map<String, Object> yamlConfig = readYamlFile(yamlFile);
            if (yamlConfig != null) {
                config.putAll(yamlConfig);
            }
        }

        // Try application.yaml
        Path yamlFile2 = resourcesDir.resolve("application.yaml");
        if (Files.exists(yamlFile2)) {
            Map<String, Object> yamlConfig = readYamlFile(yamlFile2);
            if (yamlConfig != null) {
                config.putAll(yamlConfig);
            }
        }

        // Try application.properties
        Path propsFile = resourcesDir.resolve("application.properties");
        if (Files.exists(propsFile)) {
            Map<String, Object> propsConfig = readPropertiesFile(propsFile);
            if (propsConfig != null) {
                config.putAll(propsConfig);
            }
        }

        return config;
    }

    /**
     * Detect configuration for a specific profile.
     */
    private EnvironmentConfig detectEnvironmentConfig(Path resourcesDir, String profile,
                                                      Map<String, Object> commonConfig, Boolean actuatorEnabled) {
        // Start with common configuration
        Map<String, Object> config = deepCopy(commonConfig);

        // Override with profile-specific configuration (deep merge)
        Map<String, Object> profileConfig = readProfileConfiguration(resourcesDir, profile);
        if (profileConfig != null) {
            deepMerge(config, profileConfig);
        }

        // Extract values
        Integer serverPort = extractServerPort(config);
        String contextPath = extractContextPath(config);
        String actuatorBasePath = extractActuatorBasePath(config, actuatorEnabled);
        String actuatorHealthPath = actuatorBasePath != null ? actuatorBasePath + HEALTH_ENDPOINT : null;
        String actuatorInfoPath = actuatorBasePath != null ? actuatorBasePath + INFO_ENDPOINT : null;

        return EnvironmentConfig.builder()
                .profile(profile)
                .serverPort(serverPort)
                .contextPath(contextPath)
                .actuatorEnabled(actuatorEnabled)
                .actuatorBasePath(actuatorBasePath)
                .actuatorHealthPath(actuatorHealthPath)
                .actuatorInfoPath(actuatorInfoPath)
                .build();
    }

    /**
     * Deep copy a map (including nested maps).
     */
    @SuppressWarnings("unchecked")
    private Map<String, Object> deepCopy(Map<String, Object> source) {
        Map<String, Object> copy = new HashMap<>();
        for (Map.Entry<String, Object> entry : source.entrySet()) {
            if (entry.getValue() instanceof Map<?, ?> nestedMap) {
                copy.put(entry.getKey(), deepCopy((Map<String, Object>) nestedMap));
            } else {
                copy.put(entry.getKey(), entry.getValue());
            }
        }
        return copy;
    }

    /**
     * Deep merge two maps. Values from 'source' override values in 'target'.
     * For nested maps, merge recursively instead of replacing.
     */
    @SuppressWarnings("unchecked")
    private void deepMerge(Map<String, Object> target, Map<String, Object> source) {
        for (Map.Entry<String, Object> entry : source.entrySet()) {
            String key = entry.getKey();
            Object sourceValue = entry.getValue();

            if (sourceValue instanceof Map<?, ?> sourceMap && target.get(key) instanceof Map<?, ?> targetMap) {
                // Both are maps, merge recursively
                deepMerge((Map<String, Object>) targetMap, (Map<String, Object>) sourceMap);
            } else {
                // Override with source value
                target.put(key, sourceValue);
            }
        }
    }

    /**
     * Read profile-specific configuration.
     */
    private Map<String, Object> readProfileConfiguration(Path resourcesDir, String profile) {
        Map<String, Object> config = new HashMap<>();

        // Try application-{profile}.yml
        Path yamlFile = resourcesDir.resolve("application-" + profile + ".yml");
        if (Files.exists(yamlFile)) {
            Map<String, Object> yamlConfig = readYamlFile(yamlFile);
            if (yamlConfig != null) {
                config.putAll(yamlConfig);
            }
        }

        // Try application-{profile}.yaml
        Path yamlFile2 = resourcesDir.resolve("application-" + profile + ".yaml");
        if (Files.exists(yamlFile2)) {
            Map<String, Object> yamlConfig = readYamlFile(yamlFile2);
            if (yamlConfig != null) {
                config.putAll(yamlConfig);
            }
        }

        // Try application-{profile}.properties
        Path propsFile = resourcesDir.resolve("application-" + profile + ".properties");
        if (Files.exists(propsFile)) {
            Map<String, Object> propsConfig = readPropertiesFile(propsFile);
            if (propsConfig != null) {
                config.putAll(propsConfig);
            }
        }

        return config;
    }

    /**
     * Read YAML file into a map.
     */
    @SuppressWarnings("unchecked")
    private Map<String, Object> readYamlFile(Path yamlFile) {
        try (var inputStream = new FileInputStream(yamlFile.toFile())) {
            Yaml yaml = new Yaml();
            Object data = yaml.load(inputStream);
            if (data instanceof Map<?, ?> map) {
                return (Map<String, Object>) map;
            }
        } catch (IOException e) {
            log.warn("Error reading YAML file {}: {}", yamlFile, e.getMessage());
        }
        return null;
    }

    /**
     * Read properties file into a map.
     */
    private Map<String, Object> readPropertiesFile(Path propsFile) {
        try (var inputStream = new FileInputStream(propsFile.toFile())) {
            Properties props = new Properties();
            props.load(inputStream);
            Map<String, Object> map = new HashMap<>();
            for (String key : props.stringPropertyNames()) {
                map.put(key, props.getProperty(key));
            }
            return map;
        } catch (IOException e) {
            log.warn("Error reading properties file {}: {}", propsFile, e.getMessage());
        }
        return null;
    }

    /**
     * Extract server port from configuration.
     */
    @SuppressWarnings("unchecked")
    private Integer extractServerPort(Map<String, Object> config) {
        // Try server.port from YAML structure
        if (config.get("server") instanceof Map<?, ?> serverMap) {
            Map<String, Object> server = (Map<String, Object>) serverMap;
            if (server.containsKey("port")) {
                Object portObj = server.get("port");
                if (portObj instanceof Integer port) {
                    return port;
                } else if (portObj != null) {
                    try {
                        return Integer.parseInt(portObj.toString());
                    } catch (NumberFormatException e) {
                        log.warn("Invalid server port value: {}", portObj);
                    }
                }
            }
        }

        // Try server.port from properties
        Object portObj = config.get("server.port");
        if (portObj instanceof Integer port) {
            return port;
        } else if (portObj != null) {
            try {
                return Integer.parseInt(portObj.toString());
            } catch (NumberFormatException e) {
                log.warn("Invalid server port value: {}", portObj);
            }
        }

        return null;
    }

    /**
     * Extract context path from configuration.
     */
    @SuppressWarnings("unchecked")
    private String extractContextPath(Map<String, Object> config) {
        // Try server.servlet.context-path (Spring MVC)
        if (config.get("server") instanceof Map<?, ?> serverMap) {
            Map<String, Object> server = (Map<String, Object>) serverMap;
            if (server.get("servlet") instanceof Map<?, ?> servletMap) {
                Map<String, Object> servlet = (Map<String, Object>) servletMap;
                if (servlet.containsKey("context-path")) {
                    return servlet.get("context-path").toString();
                }
            }
        }

        // Try server.servlet.context-path from properties
        if (config.containsKey("server.servlet.context-path")) {
            return config.get("server.servlet.context-path").toString();
        }

        // Try spring.webflux.base-path (Spring WebFlux)
        if (config.get("spring") instanceof Map<?, ?> springMap) {
            Map<String, Object> spring = (Map<String, Object>) springMap;
            if (spring.get("webflux") instanceof Map<?, ?> webfluxMap) {
                Map<String, Object> webflux = (Map<String, Object>) webfluxMap;
                if (webflux.containsKey("base-path")) {
                    return webflux.get("base-path").toString();
                }
            }
        }

        // Try spring.webflux.base-path from properties
        if (config.containsKey("spring.webflux.base-path")) {
            return config.get("spring.webflux.base-path").toString();
        }

        return null;
    }

    /**
     * Extract Actuator base path from configuration.
     */
    @SuppressWarnings("unchecked")
    private String extractActuatorBasePath(Map<String, Object> config, Boolean actuatorEnabled) {
        if (actuatorEnabled == null || !actuatorEnabled) {
            return null;
        }

        // Try management.endpoints.web.base-path from YAML structure
        if (config.get("management") instanceof Map<?, ?> managementMap) {
            Map<String, Object> management = (Map<String, Object>) managementMap;
            if (management.get("endpoints") instanceof Map<?, ?> endpointsMap) {
                Map<String, Object> endpoints = (Map<String, Object>) endpointsMap;
                if (endpoints.get("web") instanceof Map<?, ?> webMap) {
                    Map<String, Object> web = (Map<String, Object>) webMap;
                    if (web.containsKey("base-path")) {
                        return web.get("base-path").toString();
                    }
                }
            }
        }

        // Try management.endpoints.web.base-path from properties
        if (config.containsKey("management.endpoints.web.base-path")) {
            return config.get("management.endpoints.web.base-path").toString();
        }

        // Return default
        return DEFAULT_ACTUATOR_BASE_PATH;
    }
}

