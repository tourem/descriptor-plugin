package io.github.tourem.maven.descriptor.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 * Options controlling dependency tree collection.
 * This is configured by the Maven plugin then passed into the analyzer.
 *
 * Defaults are chosen for backward compatibility (feature disabled by default).
 * @author tourem
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DependencyTreeOptions {
    /** Enable/disable the feature (disabled by default). */
    @Builder.Default
    private boolean include = false;

    /** Depth: -1 = unlimited, 0 = only direct (default: -1). */
    @Builder.Default
    private int depth = -1;

    /** Scopes to include (compile,runtime by default). */
    @Builder.Default
    private Set<String> scopes = new HashSet<>();

    /** Output format (flat/tree/both). */
    @Builder.Default
    private DependencyTreeFormat format = DependencyTreeFormat.FLAT;

    /** Exclude transitive dependencies entirely (default: false). */
    @Builder.Default
    private boolean excludeTransitive = false;

    /** Include optional dependencies (default: false). */
    @Builder.Default
    private boolean includeOptional = false;

    /** Utility to normalize scope names to lower-case. */
    public void normalize() {
        if (scopes == null) { scopes = new HashSet<>(); }
        Set<String> lowered = new HashSet<>();
        for (String s : scopes) {
            if (s != null) lowered.add(s.trim().toLowerCase());
        }
        scopes.clear();
        scopes.addAll(lowered);
    }

    /** Common default scopes compile + runtime. */
    public static Set<String> defaultScopes() {
        Set<String> s = new HashSet<>();
        Collections.addAll(s, "compile", "runtime");
        return s;
    }
}

