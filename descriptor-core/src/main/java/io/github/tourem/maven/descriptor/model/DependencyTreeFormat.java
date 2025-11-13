package io.github.tourem.maven.descriptor.model;

/**
 * Format for dependency tree export.
 * - FLAT: flat list with depth/path
 * - TREE: hierarchical tree with children
 * - BOTH: include both representations
 * @author tourem
 */
public enum DependencyTreeFormat {
    FLAT,
    TREE,
    BOTH;

    public static DependencyTreeFormat fromString(String v) {
        if (v == null) return FLAT;
        switch (v.trim().toLowerCase()) {
            case "tree": return TREE;
            case "both": return BOTH;
            case "flat":
            default: return FLAT;
        }
    }
}

