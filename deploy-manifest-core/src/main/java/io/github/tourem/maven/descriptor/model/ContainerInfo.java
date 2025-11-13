package io.github.tourem.maven.descriptor.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Information about a container image produced by this module.
 * This describes the image coordinates and useful build/publish hints
 * detected from popular container build plugins (Jib, Spring Boot, Fabric8, Quarkus, Micronaut).
 * @author tourem

 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ContainerInfo {

    /**
     * Tool/plugin used to build the image, e.g. "jib", "spring-boot", "fabric8", "quarkus", "micronaut".
     */
    private String tool;

    /**
     * Fully qualified image name without tag(s), e.g. "ghcr.io/acme/my-service" or "acme/my-service".
     */
    private String image;

    /**
     * Image registry (docker.io, ghcr.io, gcr.io, ...), if detectable.
     */
    private String registry;

    /**
     * Image group/namespace/organization if detectable.
     */
    private String group;

    /**
     * Primary tag (or null if none configured). Usually the project version.
     */
    private String tag;

    /**
     * Additional tags configured on the plugin (e.g. "latest").
     */
    private List<String> additionalTags;

    /**
     * Base image used to build the app (e.g. from.image for Jib).
     */
    private String baseImage;

    /**
     * Buildpacks builder image when using Spring Boot build-image.
     */
    private String builderImage;

    /**
     * Buildpacks run image when using Spring Boot build-image.
     */
    private String runImage;

    /**
     * Whether the plugin is configured to push/publish the image by default.
     */
    private Boolean publish;
}

