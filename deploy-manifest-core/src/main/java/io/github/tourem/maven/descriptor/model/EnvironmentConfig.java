package io.github.tourem.maven.descriptor.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;

/**
 * Configuration specific to an environment/profile.
 *
 * @param profile Environment/profile name (e.g., "dev", "hml", "prod")
 * @param serverPort Server port for this environment
 * @param contextPath Base context path for the application (e.g., /api, /app) from server.servlet.context-path or spring.webflux.base-path
 * @param actuatorEnabled Whether Spring Boot Actuator is enabled for this environment
 * @param actuatorBasePath Base path for Actuator endpoints (default: /actuator)
 * @param actuatorHealthPath Health check endpoint path (e.g., /actuator/health)
 * @param actuatorInfoPath Info endpoint path (e.g., /actuator/info)
 * @author tourem

 */
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public record EnvironmentConfig(
    String profile,
    Integer serverPort,
    String contextPath,
    Boolean actuatorEnabled,
    String actuatorBasePath,
    String actuatorHealthPath,
    String actuatorInfoPath
) {}

