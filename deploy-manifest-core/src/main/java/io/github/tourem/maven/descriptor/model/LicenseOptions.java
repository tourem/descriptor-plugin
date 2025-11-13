package io.github.tourem.maven.descriptor.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class LicenseOptions {
    /** Enable license collection (disabled by default) */
    @Builder.Default
    private boolean include = false;

    /** Generate warnings for incompatible/unknown licenses */
    @Builder.Default
    private boolean licenseWarnings = false;

    /** Licenses considered incompatible (case-insensitive) */
    @Builder.Default
    private Set<String> incompatibleLicenses = defaultIncompatibleLicenses();

    /** Include transitive dependency licenses (future use) */
    @Builder.Default
    private boolean includeTransitiveLicenses = true;

    public static Set<String> defaultIncompatibleLicenses() {
        Set<String> s = new HashSet<>();
        Collections.addAll(s, "GPL-3.0", "AGPL-3.0", "SSPL");
        return s;
    }

    public Set<String> normalizedIncompatibleSet() {
        if (incompatibleLicenses == null || incompatibleLicenses.isEmpty()) return Set.of();
        Set<String> norm = new HashSet<>();
        for (String v : incompatibleLicenses) {
            if (v != null && !v.isBlank()) norm.add(v.trim().toLowerCase());
        }
        return norm;
    }
}

