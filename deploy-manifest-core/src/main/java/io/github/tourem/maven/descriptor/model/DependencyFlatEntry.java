package io.github.tourem.maven.descriptor.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Entry for flat dependency representation.
 * Depth and path reflect the chain from the root module to this dependency.
 * @author tourem
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class DependencyFlatEntry {
    private String groupId;
    private String artifactId;
    private String version;
    private String scope;   // compile, runtime, provided, test, etc.
    private String type;    // jar, pom, etc.
    private boolean optional;

    // Flat-only fields
    private Integer depth;  // 1 for direct
    private String path;    // e.g., groupId:artifactId:type:version -> ...
}

