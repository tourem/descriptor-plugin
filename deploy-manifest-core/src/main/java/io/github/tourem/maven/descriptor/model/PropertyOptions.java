package io.github.tourem.maven.descriptor.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

/**
 * Options for build properties collection.
 * author: tourem
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class PropertyOptions {
    @Builder.Default
    private boolean include = false;
    @Builder.Default
    private boolean includeSystemProperties = true;
    @Builder.Default
    private boolean includeEnvironmentVariables = false;
    @Builder.Default
    private boolean filterSensitiveProperties = true;
    @Builder.Default
    private boolean maskSensitiveValues = true;
    @Builder.Default
    private Set<String> propertyExclusions = defaultSensitiveKeys();

    public Set<String> normalizedExclusions() {
        Set<String> out = new HashSet<>();
        if (propertyExclusions != null) {
            for (String s : propertyExclusions) {
                if (s != null) out.add(s.toLowerCase(Locale.ROOT));
            }
        }
        return out;
    }

    public static Set<String> defaultSensitiveKeys() {
        Set<String> keys = new HashSet<>();
        keys.add("password");
        keys.add("secret");
        keys.add("token");
        keys.add("apikey");
        keys.add("api-key");
        keys.add("api_key");
        keys.add("credentials");
        keys.add("auth");
        keys.add("key");
        return keys;
    }
}

