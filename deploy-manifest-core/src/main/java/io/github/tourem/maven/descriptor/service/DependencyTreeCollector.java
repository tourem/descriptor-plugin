package io.github.tourem.maven.descriptor.service;

import io.github.tourem.maven.descriptor.model.*;
import lombok.extern.slf4j.Slf4j;
import org.apache.maven.model.Dependency;
import org.apache.maven.model.Model;

import java.nio.file.Path;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Collects Maven dependency information for a module based on provided options.
 *
 * NOTE: First iteration collects direct dependencies from the POM (no transitive resolution)
 * to avoid adding heavy resolver dependencies. Structure and options are in place to
 * support a full tree in a follow-up iteration.
 *
 * Only executable modules should request collection from the analyzer layer.
 * @author tourem
 */
@Slf4j
public class DependencyTreeCollector {

    public DependencyTreeInfo collect(Model model, Path modulePath, DependencyTreeOptions options) {
        try {
            if (model.getDependencies() == null || model.getDependencies().isEmpty()) {
                return null;
            }

            // Normalize options
            if (options == null) {
                options = DependencyTreeOptions.builder()
                        .include(false)
                        .depth(-1)
                        .scopes(DependencyTreeOptions.defaultScopes())
                        .format(DependencyTreeFormat.FLAT)
                        .excludeTransitive(false)
                        .includeOptional(false)
                        .build();
            }
            options.normalize();
            Set<String> scopes = options.getScopes();
            if (scopes == null || scopes.isEmpty()) {
                scopes = DependencyTreeOptions.defaultScopes();
            }
            final Set<String> allowedScopes = scopes;
            final boolean includeOptional = options.isIncludeOptional();

            // Filter direct dependencies by scope/optional
            List<Dependency> direct = model.getDependencies();
            List<Dependency> filtered = direct.stream()
                    .filter(d -> includeScope(allowedScopes, d.getScope()))
                    .filter(d -> includeOptional || !isOptional(d))
                    .collect(Collectors.toList());

            // Build summary (transitive = 0 in this iteration)
            Map<String, Integer> scopeCounts = new TreeMap<>();
            int optionalCount = 0;
            for (Dependency d : filtered) {
                String scope = normalizedScope(d.getScope());
                scopeCounts.put(scope, scopeCounts.getOrDefault(scope, 0) + 1);
                if (isOptional(d)) optionalCount++;
            }

            DependencySummary summary = DependencySummary.builder()
                    .total(filtered.size())
                    .direct(filtered.size())
                    .transitive(0)
                    .scopes(scopeCounts)
                    .optional(optionalCount)
                    .build();

            List<DependencyFlatEntry> flat = null;
            List<DependencyNode> tree = null;

            if (options.getFormat() == DependencyTreeFormat.FLAT || options.getFormat() == DependencyTreeFormat.BOTH) {
                flat = filtered.stream().map(d -> DependencyFlatEntry.builder()
                        .groupId(nullToEmpty(d.getGroupId()))
                        .artifactId(nullToEmpty(d.getArtifactId()))
                        .version(nullToEmpty(d.getVersion()))
                        .scope(normalizedScope(d.getScope()))
                        .type(nullToEmpty(d.getType()))
                        .optional(isOptional(d))
                        .depth(1)
                        .path(flatPath(d))
                        .build()).collect(Collectors.toList());
            }

            if (options.getFormat() == DependencyTreeFormat.TREE || options.getFormat() == DependencyTreeFormat.BOTH) {
                tree = filtered.stream().map(d -> DependencyNode.builder()
                        .groupId(nullToEmpty(d.getGroupId()))
                        .artifactId(nullToEmpty(d.getArtifactId()))
                        .version(nullToEmpty(d.getVersion()))
                        .scope(normalizedScope(d.getScope()))
                        .type(nullToEmpty(d.getType()))
                        .optional(isOptional(d))
                        .children(Collections.emptyList())
                        .build()).collect(Collectors.toList());
            }

            return DependencyTreeInfo.builder()
                    .summary(summary)
                    .flat(flat)
                    .tree(tree)
                    .build();
        } catch (Exception e) {
            log.debug("Dependency tree collection error: {}", e.getMessage(), e);
            return null;
        }
    }

    private boolean includeScope(Set<String> allowed, String scope) {
        String s = normalizedScope(scope);
        return allowed.contains(s);
    }

    private String normalizedScope(String scope) {
        return scope == null || scope.isBlank() ? "compile" : scope.trim().toLowerCase();
    }

    private boolean isOptional(Dependency d) {
        String opt = d.getOptional();
        return opt != null && Boolean.parseBoolean(opt);
    }

    private String nullToEmpty(String v) { return v == null ? "" : v; }

    private String flatPath(Dependency d) {
        String type = (d.getType() == null || d.getType().isBlank()) ? "jar" : d.getType();
        String ver = d.getVersion() == null ? "" : d.getVersion();
        return String.join(":",
                nullToEmpty(d.getGroupId()),
                nullToEmpty(d.getArtifactId()),
                type,
                ver
        );
    }
}

