package io.github.tourem.maven.descriptor.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Container for dependency information: summary + flat/tree representations.
 * Any of flat/tree can be null depending on chosen format.
 * @author tourem
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class DependencyTreeInfo {
    private DependencySummary summary;
    private List<DependencyFlatEntry> flat;
    private List<DependencyNode> tree;
}

