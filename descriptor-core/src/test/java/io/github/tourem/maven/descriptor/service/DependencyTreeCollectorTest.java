package io.github.tourem.maven.descriptor.service;

import io.github.tourem.maven.descriptor.model.DependencyTreeFormat;
import io.github.tourem.maven.descriptor.model.DependencyTreeInfo;
import io.github.tourem.maven.descriptor.model.DependencyTreeOptions;
import org.apache.maven.model.Dependency;
import org.apache.maven.model.Model;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class DependencyTreeCollectorTest {

    private final DependencyTreeCollector collector = new DependencyTreeCollector();

    @Test
    void collect_returnsNull_whenNoDependencies() {
        Model m = new Model();
        m.setDependencies(List.of());
        DependencyTreeInfo info = collector.collect(m, Path.of("."), defaultOpts());
        assertThat(info).isNull();
    }

    @Test
    void flatFormat_filtersByScope_andExcludesOptionalByDefault() {
        Model m = new Model();
        m.setDependencies(List.of(
                dep("g","a","1.0", null, null, null),          // compile by default
                dep("g","r","2.0", "runtime", null, null),     // runtime
                dep("g","p","1.0", "provided", null, null),    // provided -> excluded
                dep("g","t","1.0", "test", null, null),        // test -> excluded
                dep("g","o","1.0", "compile", null, true)      // optional -> excluded by default
        ));

        DependencyTreeOptions opts = DependencyTreeOptions.builder()
                .include(true)
                .scopes(Set.of("compile","runtime"))
                .format(DependencyTreeFormat.FLAT)
                .includeOptional(false)
                .build();

        DependencyTreeInfo info = collector.collect(m, Path.of("."), opts);
        assertThat(info).isNotNull();
        assertThat(info.getFlat()).extracting("artifactId").containsExactlyInAnyOrder("a","r");
        assertThat(info.getTree()).isNull();
        assertThat(info.getSummary().getTotal()).isEqualTo(2);
        assertThat(info.getSummary().getScopes().get("compile")).isEqualTo(1);
        assertThat(info.getSummary().getScopes().get("runtime")).isEqualTo(1);
        assertThat(info.getSummary().getOptional()).isEqualTo(0);
        // depth and path on flat entries
        assertThat(info.getFlat()).allSatisfy(e -> {
            assertThat(e.getDepth()).isEqualTo(1);
            assertThat(e.getPath()).contains(":jar:");
        });
    }

    @Test
    void treeFormat_buildsTreeAndNoFlat() {
        Model m = new Model();
        m.setDependencies(List.of(
                dep("g","a","1.0", null, null, null),
                dep("g","r","2.0", "runtime", null, null)
        ));

        DependencyTreeOptions opts = DependencyTreeOptions.builder()
                .include(true)
                .scopes(Set.of("COMPILE","RUNTIME")) // will be normalized by collector
                .format(DependencyTreeFormat.TREE)
                .build();

        DependencyTreeInfo info = collector.collect(m, Path.of("."), opts);
        assertThat(info).isNotNull();
        assertThat(info.getFlat()).isNull();
        assertThat(info.getTree()).hasSize(2);
        assertThat(info.getTree()).extracting("artifactId")
                .containsExactlyInAnyOrder("a","r");
    }

    @Test
    void bothFormat_includesFlatAndTree_andIncludesOptionalWhenEnabled() {
        Model m = new Model();
        m.setDependencies(List.of(
                dep("g","a","1.0", null, null, true),   // optional
                dep("g","r","2.0", "runtime", null, null)
        ));

        DependencyTreeOptions opts = DependencyTreeOptions.builder()
                .include(true)
                .scopes(Set.of("compile","runtime"))
                .format(DependencyTreeFormat.BOTH)
                .includeOptional(true)
                .build();

        DependencyTreeInfo info = collector.collect(m, Path.of("."), opts);
        assertThat(info).isNotNull();
        assertThat(info.getFlat()).hasSize(2);
        assertThat(info.getTree()).hasSize(2);
        assertThat(info.getSummary().getOptional()).isEqualTo(1);
        assertThat(info.getFlat()).filteredOn(e -> e.isOptional()).hasSize(1);
    }

    @Test
    void optionsNull_usesDefaultScopes_compileAndRuntime() {
        Model m = new Model();
        m.setDependencies(List.of(
                dep("g","a","1.0", null, null, null),
                dep("g","r","2.0", "runtime", null, null),
                dep("g","p","1.0", "provided", null, null)
        ));

        DependencyTreeInfo info = collector.collect(m, Path.of("."), null); // options null
        assertThat(info).isNotNull();
        assertThat(info.getFlat()).hasSize(2);
        assertThat(info.getFlat()).extracting("artifactId")
                .containsExactlyInAnyOrder("a","r");
    }

    private static Dependency dep(String g, String a, String v, String scope, String type, Boolean optional) {
        Dependency d = new Dependency();
        d.setGroupId(g);
        d.setArtifactId(a);
        d.setVersion(v);
        if (scope != null) d.setScope(scope);
        if (type != null) d.setType(type);
        if (optional != null) d.setOptional(optional.toString());
        return d;
    }

    private static DependencyTreeOptions defaultOpts() {
        return DependencyTreeOptions.builder().include(true).scopes(Set.of("compile","runtime")).format(DependencyTreeFormat.FLAT).build();
    }
}

