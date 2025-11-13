package io.github.tourem.maven.descriptor.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Options for collecting Maven plugins information.
 * author: tourem
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class PluginOptions {
    /** Whether to include plugin information at all. */
    @Builder.Default
    private boolean include = false;

    /** Include plugin configuration blocks (sanitized). */
    @Builder.Default
    private boolean includePluginConfiguration = true;

    /** Include pluginManagement definitions. */
    @Builder.Default
    private boolean includePluginManagement = true;

    /** Try to check Maven Central for newer plugin versions. */
    @Builder.Default
    private boolean checkPluginUpdates = false;

    /** Mask sensitive fields in configuration (password, token, apiKey, etc.). */
    @Builder.Default
    private boolean filterSensitivePluginConfig = true;

    /** Timeout in milliseconds when checking updates (network). */
    @Builder.Default
    private int updateCheckTimeoutMillis = 2000;
}

