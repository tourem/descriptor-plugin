package io.github.tourem.maven.descriptor.service;

import io.github.tourem.maven.descriptor.model.*;
import lombok.extern.slf4j.Slf4j;
import org.apache.maven.model.Dependency;
import org.apache.maven.model.Model;
import org.apache.maven.model.io.xpp3.MavenXpp3Reader;

import java.io.File;
import java.io.FileReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Collects license information for a module's dependencies (direct + transitive).
 * Resolves dependency POMs from local Maven repo to read <licenses> metadata.
 * No remote resolution: relies on maven.repo.local (or ~/.m2/repository) being populated.
 */
@Slf4j
public class LicenseCollector {

    private static final Set<String> DEFAULT_SCOPES = Set.of("compile", "runtime");

    public LicenseInfo collect(Model model, Path modulePath, LicenseOptions options) {
        if (model == null || options == null || !options.isInclude()) return null;

        // Prepare accumulators
        List<LicenseDetail> details = new ArrayList<>();
        Map<String, Integer> byType = new TreeMap<>();
        List<LicenseWarning> warnings = new ArrayList<>();
        Set<String> allowedScopes = new HashSet<>(DEFAULT_SCOPES);
        Set<String> visited = new HashSet<>(); // G:A:V

        // Seed with direct dependencies (filtered by scope)
        if (model.getDependencies() != null) {
            for (Dependency d : model.getDependencies()) {
                String scope = normalizeScope(d.getScope());
                if (!allowedScopes.contains(scope)) continue;
                processDependency(d, 1, allowedScopes, options, visited, details, byType, warnings);
                if (options.isIncludeTransitiveLicenses()) {
                    resolveTransitively(d, 2, allowedScopes, options, visited, details, byType, warnings);
                }
            }
        }

        // Summaries
        int total = details.size();
        int unknown = (int) details.stream().filter(ld -> "unknown".equalsIgnoreCase(ld.getLicense())).count();
        int identified = total - unknown;

        // Compliance
        Set<String> incompatSet = options.normalizedIncompatibleSet();
        int incompatCount = 0;
        for (LicenseDetail ld : details) {
            for (String token : tokens(ld.getLicense())) {
                if (incompatSet.contains(token.trim().toLowerCase())) {
                    incompatCount++;
                    if (options.isLicenseWarnings()) {
                        warnings.add(LicenseWarning.builder()
                                .severity("HIGH")
                                .artifact(ld.getGroupId() + ":" + ld.getArtifactId() + ":" + ld.getVersion())
                                .license(ld.getLicense())
                                .reason("Incompatible license detected: " + token)
                                .recommendation("Replace with Apache-2.0 or MIT licensed alternative")
                                .build());
                    }
                    break;
                }
            }
        }

        LicenseSummary summary = LicenseSummary.builder()
                .total(total)
                .identified(identified)
                .unknown(unknown)
                .byType(byType.isEmpty() ? null : byType)
                .build();

        LicenseCompliance compliance = LicenseCompliance.builder()
                .hasIncompatibleLicenses(incompatCount > 0)
                .incompatibleCount(incompatCount)
                .unknownCount(unknown)
                .commerciallyViable(incompatCount == 0)
                .requiresAttribution(identified > 0)
                .build();

        return LicenseInfo.builder()
                .summary(summary)
                .details(details.isEmpty() ? null : details)
                .warnings(warnings.isEmpty() ? null : warnings)
                .compliance(compliance)
                .build();
    }

    private void processDependency(Dependency d, int depth,
                                   Set<String> allowedScopes, LicenseOptions options,
                                   Set<String> visited, List<LicenseDetail> details,
                                   Map<String, Integer> byType, List<LicenseWarning> warnings) {
        String gav = (nullToEmpty(d.getGroupId()) + ":" + nullToEmpty(d.getArtifactId()) + ":" + nullToEmpty(d.getVersion())).trim();
        if (gav.contains("::")) return; // skip incomplete
        if (!visited.add(gav)) return;   // already processed

        String scope = normalizeScope(d.getScope());
        if (!allowedScopes.contains(scope)) return;

        // Read dependency POM from local repo and extract licenses
        String license = "unknown";
        String licenseUrl = null;
        boolean multi = false;
        try {
            Model depModel = readPomFromLocalRepo(d.getGroupId(), d.getArtifactId(), d.getVersion());
            if (depModel != null && depModel.getLicenses() != null && !depModel.getLicenses().isEmpty()) {
                List<String> names = depModel.getLicenses().stream()
                        .map(l -> l.getName() == null ? "" : l.getName().trim())
                        .filter(s -> !s.isBlank()).collect(Collectors.toList());
                multi = names.size() > 1;
                license = names.isEmpty() ? "unknown" : String.join(" OR ", names);
                licenseUrl = depModel.getLicenses().get(0).getUrl();
                // Aggregate by type: count each token separately
                if (names.isEmpty()) {
                    byType.merge("unknown", 1, Integer::sum);
                    if (options.isLicenseWarnings()) warnings.add(unknownWarn(d));
                } else {
                    for (String t : names) byType.merge(t, 1, Integer::sum);
                }
            } else {
                byType.merge("unknown", 1, Integer::sum);
                if (options.isLicenseWarnings()) warnings.add(unknownWarn(d));
            }
        } catch (Exception e) {
            log.debug("License read failure for {}: {}", gav, e.getMessage());
            byType.merge("unknown", 1, Integer::sum);
            if (options.isLicenseWarnings()) warnings.add(unknownWarn(d));
        }

        details.add(LicenseDetail.builder()
                .groupId(d.getGroupId())
                .artifactId(d.getArtifactId())
                .version(d.getVersion())
                .scope(scope)
                .license(license)
                .licenseUrl(licenseUrl)
                .multiLicense(multi)
                .depth(depth)
                .build());
    }

    private void resolveTransitively(Dependency parent, int depth,
                                     Set<String> allowedScopes, LicenseOptions options,
                                     Set<String> visited, List<LicenseDetail> details,
                                     Map<String, Integer> byType, List<LicenseWarning> warnings) {
        try {
            Model m = readPomFromLocalRepo(parent.getGroupId(), parent.getArtifactId(), parent.getVersion());
            if (m == null || m.getDependencies() == null) return;
            for (Dependency td : m.getDependencies()) {
                String scope = normalizeScope(td.getScope());
                if (!allowedScopes.contains(scope)) continue;
                processDependency(td, depth, allowedScopes, options, visited, details, byType, warnings);
                // Recurse
                resolveTransitively(td, depth + 1, allowedScopes, options, visited, details, byType, warnings);
            }
        } catch (Exception e) {
            log.debug("Transitive resolution failed for {}:{}:{} - {}",
                    parent.getGroupId(), parent.getArtifactId(), parent.getVersion(), e.getMessage());
        }
    }

    private LicenseWarning unknownWarn(Dependency d) {
        return LicenseWarning.builder()
                .severity("MEDIUM")
                .artifact(d.getGroupId() + ":" + d.getArtifactId() + ":" + d.getVersion())
                .license("unknown")
                .reason("License information not found in POM")
                .recommendation("Add <licenses> to dependency POM or replace with clearly licensed alternative")
                .build();
    }

    private static String normalizeScope(String s) {
        if (s == null || s.isBlank()) return "compile"; // Maven default
        return s.trim().toLowerCase(Locale.ROOT);
    }

    private static List<String> tokens(String license) {
        if (license == null) return List.of();
        return Arrays.stream(license.split("\\s+OR\\s+|\\s+AND\\s+"))
                .map(String::trim).filter(s -> !s.isBlank()).collect(Collectors.toList());
    }

    private Model readPomFromLocalRepo(String groupId, String artifactId, String version) {
        try {
            if (groupId == null || artifactId == null || version == null) return null;
            String repoRoot = System.getProperty("maven.repo.local");
            if (repoRoot == null || repoRoot.isBlank()) {
                repoRoot = System.getProperty("user.home") + File.separator + ".m2" + File.separator + "repository";
            }
            String groupPath = groupId.replace('.', File.separatorChar);
            String rel = groupPath + File.separator + artifactId + File.separator + version + File.separator + artifactId + "-" + version + ".pom";
            File pom = new File(repoRoot, rel);
            if (!pom.exists()) return null;
            MavenXpp3Reader reader = new MavenXpp3Reader();
            try (FileReader fr = new FileReader(pom)) {
                return reader.read(fr);
            }
        } catch (Exception e) {
            log.debug("Failed to read POM for {}:{}:{} - {}", groupId, artifactId, version, e.getMessage());
            return null;
        }
    }

    private String nullToEmpty(String v) { return v == null ? "" : v; }
}

