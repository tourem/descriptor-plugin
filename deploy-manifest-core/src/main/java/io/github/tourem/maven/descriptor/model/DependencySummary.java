package io.github.tourem.maven.descriptor.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * Summary counts for dependency tree section.
 * Includes totals and breakdown by scope.
 * @author tourem
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DependencySummary {
    private int total;
    private int direct;
    private int transitive;
    private Map<String, Integer> scopes; // e.g. compile, runtime, provided, test
    private int optional;
}

