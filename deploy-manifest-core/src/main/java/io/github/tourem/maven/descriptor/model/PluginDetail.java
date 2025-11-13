package io.github.tourem.maven.descriptor.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

/**
 * Detailed info for a single Maven plugin used in the build.
 * author: tourem
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class PluginDetail {
    private String groupId;
    private String artifactId;
    private String version;
    /** "effective" or "management" */
    private String source;
    private Boolean inherited;
    private String phase; // primary phase inferred from executions
    private List<String> goals; // goals at plugin-level or aggregated from executions
    private Object configuration; // sanitized configuration (Map/List/primitive)
    private List<PluginExecutionInfo> executions;
    private PluginOutdatedInfo outdated;
}

