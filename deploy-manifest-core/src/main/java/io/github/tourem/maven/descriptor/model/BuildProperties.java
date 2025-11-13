package io.github.tourem.maven.descriptor.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * Grouped build properties for traceability and reproducibility.
 * - project: core project identifiers and common project.* values
 * - maven: maven.* and common build flags (maven.compiler.*, skipTests, etc.)
 * - custom: user-defined properties not categorized above
 * - system: System.getProperties() (optional)
 * - environment: System.getenv() (optional)
 * - maskedCount: number of values masked for sensitivity
 * author: tourem
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class BuildProperties {
    private Map<String, String> project;
    private Map<String, String> maven;
    private Map<String, String> custom;
    private Map<String, String> system;
    private Map<String, String> environment;
    private Integer maskedCount;
}

