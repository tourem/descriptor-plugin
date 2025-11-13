package io.github.tourem.maven.descriptor.model;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class DependencyTreeOptionsTest {

    @Test
    void defaultScopes_shouldReturnCompileAndRuntime() {
        Set<String> defaults = DependencyTreeOptions.defaultScopes();
        assertThat(defaults).containsExactlyInAnyOrder("compile", "runtime");
    }

    @Test
    void normalize_shouldLowercaseAndTrimScopes() {
        DependencyTreeOptions opts = DependencyTreeOptions.builder()
                .scopes(new HashSet<>(Set.of(" COMPILE ", "Runtime", "", "  ")))
                .build();
        opts.normalize();
        assertThat(opts.getScopes()).containsExactlyInAnyOrder("compile", "runtime");
    }

    @Test
    void normalize_handlesNullScopes() {
        DependencyTreeOptions opts = DependencyTreeOptions.builder()
                .scopes(null)
                .build();
        opts.normalize();
        assertThat(opts.getScopes()).isNotNull();
        assertThat(opts.getScopes()).isEmpty();
    }
}

