package io.github.tourem.maven.descriptor.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Node for tree dependency representation.
 * Children holds transitive dependencies (may be empty).
 * @author tourem
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class DependencyNode {
    private String groupId;
    private String artifactId;
    private String version;
    private String scope;
    private String type;
    private boolean optional;

    private List<DependencyNode> children; // may be empty or null
}

