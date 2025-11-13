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
import java.util.regex.Matcher;
import java.util.regex.Pattern;


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
                processDependency(d, 1, model, modulePath, allowedScopes, options, visited, details, byType, warnings);
                if (options.isIncludeTransitiveLicenses()) {
                    resolveTransitively(d, 2, model, modulePath, allowedScopes, options, visited, details, byType, warnings);
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
                                   Model contextModel, Path modulePath,
                                   Set<String> allowedScopes, LicenseOptions options,
                                   Set<String> visited, List<LicenseDetail> details,
                                   Map<String, Integer> byType, List<LicenseWarning> warnings) {
        String version = (contextModel != null) ? resolveVersion(d, contextModel, modulePath) : d.getVersion();
        String gav = (nullToEmpty(d.getGroupId()) + ":" + nullToEmpty(d.getArtifactId()) + ":" + nullToEmpty(version)).trim();
        if (gav.contains("::")) return; // skip incomplete
        if (!visited.add(gav)) return;   // already processed

        String scope = normalizeScope(d.getScope());
        if (!allowedScopes.contains(scope)) return;

        // Read dependency POM from local repo and extract licenses
        String license = "unknown";
        String licenseUrl = null;
        boolean multi = false;
        try {
            Model depModel = readPomFromLocalRepo(d.getGroupId(), d.getArtifactId(), version);
            java.util.List<org.apache.maven.model.License> licenseNodes = java.util.Collections.emptyList();
            if (depModel != null) {
                licenseNodes = collectLicensesWithFallback(depModel);
            }
            if (licenseNodes != null && !licenseNodes.isEmpty()) {
                java.util.List<String> names = licenseNodes.stream()
                        .map(l -> l.getName() == null ? "" : l.getName().trim())
                        .filter(s -> !s.isBlank()).collect(java.util.stream.Collectors.toList());
                multi = names.size() > 1;
                license = names.isEmpty() ? "unknown" : String.join(" OR ", names);
                licenseUrl = licenseNodes.get(0).getUrl();
                // Aggregate by type: count each token separately
                if (names.isEmpty()) {
                    byType.merge("unknown", 1, Integer::sum);
                    if (options.isLicenseWarnings()) warnings.add(unknownWarn(d, contextModel, modulePath, version));
                } else {
                    for (String t : names) byType.merge(t, 1, Integer::sum);
                }
            } else {
                byType.merge("unknown", 1, Integer::sum);
                if (options.isLicenseWarnings()) warnings.add(unknownWarn(d, contextModel, modulePath, version));
            }
        } catch (Exception e) {
            log.debug("License read failure for {}: {}", gav, e.getMessage());
            byType.merge("unknown", 1, Integer::sum);
            if (options.isLicenseWarnings()) warnings.add(unknownWarn(d, contextModel, modulePath, version));
        }

        // Resolve placeholders for coordinates to avoid entries like ${hibernate.groupId}.orm or ${antlr}
        java.util.Properties props2 = new java.util.Properties();
        collectPropertiesRecursive(contextModel, modulePath, props2, new java.util.HashSet<>());
        // Also pull in imported BOM properties
        gatherManagedVersions(contextModel, modulePath, new java.util.LinkedHashMap<>(), props2, new java.util.HashSet<>());
        String resolvedGroupId = resolveProperty(d.getGroupId(), props2);
        String resolvedArtifactId = resolveProperty(d.getArtifactId(), props2);

        details.add(LicenseDetail.builder()
                .groupId(resolvedGroupId != null ? resolvedGroupId : d.getGroupId())
                .artifactId(resolvedArtifactId != null ? resolvedArtifactId : d.getArtifactId())
                .version(version)
                .scope(scope)
                .license(license)
                .licenseUrl(licenseUrl)
                .multiLicense(multi)
                .depth(depth)
                .build());
    }

    private void resolveTransitively(Dependency parent, int depth,
                                     Model contextModel, Path modulePath,
                                     Set<String> allowedScopes, LicenseOptions options,
                                     Set<String> visited, List<LicenseDetail> details,
                                     Map<String, Integer> byType, List<LicenseWarning> warnings) {
        try {
            String parentVersion = resolveVersion(parent, contextModel, modulePath);
            Model m = readPomFromLocalRepo(parent.getGroupId(), parent.getArtifactId(), parentVersion);
            if (m == null || m.getDependencies() == null) return;
            for (Dependency td : m.getDependencies()) {
                String scope = normalizeScope(td.getScope());
                if (!allowedScopes.contains(scope)) continue;
                processDependency(td, depth, m, null, allowedScopes, options, visited, details, byType, warnings);
                // Recurse using current dependency's model as context for deeper levels
                resolveTransitively(td, depth + 1, m, null, allowedScopes, options, visited, details, byType, warnings);
            }
        } catch (Exception e) {
            log.debug("Transitive resolution failed for {}:{}:{} - {}",
                    parent.getGroupId(), parent.getArtifactId(), parent.getVersion(), e.getMessage());
        }
    }

    private LicenseWarning unknownWarn(Dependency d, Model contextModel, Path modulePath, String resolvedVersion) {
        java.util.Properties props = new java.util.Properties();
        collectPropertiesRecursive(contextModel, modulePath, props, new java.util.HashSet<>());
        // Pull in properties from imported BOMs to resolve placeholders like ${hibernate.groupId}
        gatherManagedVersions(contextModel, modulePath, new java.util.LinkedHashMap<>(), props, new java.util.HashSet<>());
        String g = resolveProperty(d.getGroupId(), props);
        String a = resolveProperty(d.getArtifactId(), props);
        String v = (resolvedVersion != null && !resolvedVersion.isBlank())
                ? resolvedVersion
                : resolveProperty(d.getVersion(), props);
        if (v == null || v.isBlank()) {
            try {
                String sys = System.getProperty("deploy.manifest.resolved.ga." + g + ":" + a);
                if (sys != null && !sys.isBlank()) v = sys;
            } catch (Throwable ignore) {}
        }
        return LicenseWarning.builder()
                .severity("MEDIUM")
                .artifact((g == null ? "" : g) + ":" + (a == null ? "" : a) + ":" + (v == null ? "" : v))
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

    private String resolveVersion(Dependency d, Model contextModel, Path modulePath) {
        if (d == null) return null;
        Properties props = new Properties();
        collectPropertiesRecursive(contextModel, modulePath, props, new HashSet<>());
        // If the dependency declares a version, try to resolve property placeholders first
        if (d.getVersion() != null && !d.getVersion().isBlank()) {
            String resolved = resolveProperty(d.getVersion(), props);
            if (resolved != null && !resolved.isBlank()) return resolved;
        }
        String key = d.getGroupId() + ":" + d.getArtifactId();
        // Fast-path: use pre-resolved GA->V mapping published by the plugin
        try {
            String sys = System.getProperty("deploy.manifest.resolved.ga." + key);
            if (sys != null && !sys.isBlank()) {
                return sys;
            }
        } catch (Throwable ignore) {}
        Map<String, String> managed = new LinkedHashMap<>();
        gatherManagedVersions(contextModel, modulePath, managed, props, new HashSet<>());
        String v = managed.get(key);
        if (v != null) v = resolveProperty(v, props);
        if ("org.springframework.boot".equals(d.getGroupId()) && ("spring-boot-starter-web".equals(d.getArtifactId()) || "spring-boot-starter-data-jpa".equals(d.getArtifactId()))) {
            try { System.out.println("[deploy-manifest] resolveVersion GA=" + key + " -> " + v + ", managed size=" + managed.size()); } catch (Throwable ignore) {}
        }
        return v;
    }

    private void gatherManagedVersions(Model m, Path modulePath,
                                       Map<String, String> managed, Properties props,
                                       Set<String> visitedModels) {
        if (m == null) return;
        try { System.out.println("[deploy-manifest] GM visiting " + ((m.getGroupId()!=null?m.getGroupId():(m.getParent()!=null?m.getParent().getGroupId():""))) + ":" + m.getArtifactId() + ":" + (m.getVersion()!=null?m.getVersion():(m.getParent()!=null?m.getParent().getVersion():"")) + ", hasDM=" + (m.getDependencyManagement()!=null)); } catch (Throwable ignore) {}
        if (m.getDependencyManagement() != null && m.getDependencyManagement().getDependencies() != null) {
            // Debug: trace which model is providing DM
            try { System.out.println("[deploy-manifest] gather DM from " + (m.getGroupId()!=null?m.getGroupId():(m.getParent()!=null?m.getParent().getGroupId():"")) + ":" + m.getArtifactId()); } catch (Throwable ignore) {}
            for (Dependency dmDep : m.getDependencyManagement().getDependencies()) {
                String type = dmDep.getType();
                String scope = dmDep.getScope();
                if ("pom".equalsIgnoreCase(type != null ? type : "") && "import".equalsIgnoreCase(scope != null ? scope : "")) {
                    String bomVer = resolveProperty(dmDep.getVersion(), props);
                    Model bom = readPomFromLocalRepo(dmDep.getGroupId(), dmDep.getArtifactId(), bomVer);
                    if (bom != null && bom.getDependencyManagement() != null && bom.getDependencyManagement().getDependencies() != null) {
                        Properties bomProps = new Properties();
                        collectPropertiesRecursive(bom, null, bomProps, new HashSet<>());
                        // Merge BOM properties so we can resolve placeholders like ${hibernate}
                        props.putAll(bomProps);
                        for (Dependency b : bom.getDependencyManagement().getDependencies()) {
                            String bKey = b.getGroupId() + ":" + b.getArtifactId();
                            String bVer = resolveProperty(b.getVersion(), bomProps);
                            if (bVer != null && !managed.containsKey(bKey)) {
                                managed.put(bKey, bVer);
                            }
                        }
                        try { System.out.println("[deploy-manifest] imported BOM " + dmDep.getGroupId()+":"+dmDep.getArtifactId()+":"+bomVer+" entries="+bom.getDependencyManagement().getDependencies().size()); } catch (Throwable ignore) {}
                    }
                } else {
                    String key = dmDep.getGroupId() + ":" + dmDep.getArtifactId();
                    String ver = resolveProperty(dmDep.getVersion(), props);
                    if (ver != null) managed.putIfAbsent(key, ver);
                }
            }
        }
        Model parent = readParentModel(m, modulePath);
        if (parent != null) {
            String pk = (parent.getGroupId() == null ? "" : parent.getGroupId()) + ":" +
                        (parent.getArtifactId() == null ? "" : parent.getArtifactId()) + ":" +
                        (parent.getVersion() == null ? "" : parent.getVersion());
            if (visitedModels.add(pk)) {
                collectPropertiesRecursive(parent, modulePath, props, new HashSet<>());
                gatherManagedVersions(parent, modulePath, managed, props, visitedModels);
            }
        }
    }

    private void collectPropertiesRecursive(Model m, Path modulePath, Properties props, Set<String> visitedGavs) {
        if (m == null) return;
        // Seed standard Maven properties so placeholders like ${project.version} resolve
        String effGroupId = m.getGroupId() != null ? m.getGroupId() : (m.getParent() != null ? m.getParent().getGroupId() : null);
        String effVersion = m.getVersion() != null ? m.getVersion() : (m.getParent() != null ? m.getParent().getVersion() : null);
        if (effGroupId != null && !props.containsKey("project.groupId")) props.setProperty("project.groupId", effGroupId);
        if (m.getArtifactId() != null && !props.containsKey("project.artifactId")) props.setProperty("project.artifactId", m.getArtifactId());
        if (effVersion != null && !props.containsKey("project.version")) props.setProperty("project.version", effVersion);
        if (!props.containsKey("pom.groupId") && effGroupId != null) props.setProperty("pom.groupId", effGroupId);
        if (!props.containsKey("pom.artifactId") && m.getArtifactId() != null) props.setProperty("pom.artifactId", m.getArtifactId());
        if (!props.containsKey("pom.version") && effVersion != null) props.setProperty("pom.version", effVersion);

        if (m.getProperties() != null) {
            for (String name : m.getProperties().stringPropertyNames()) {
                if (!props.containsKey(name)) {
                    props.setProperty(name, m.getProperties().getProperty(name));
                }
            }
        }
        Model parent = readParentModel(m, modulePath);
        if (parent != null) {
            String pk = (parent.getGroupId() == null ? "" : parent.getGroupId()) + ":" +
                        (parent.getArtifactId() == null ? "" : parent.getArtifactId()) + ":" +
                        (parent.getVersion() == null ? "" : parent.getVersion());
            if (visitedGavs.add(pk)) {
                collectPropertiesRecursive(parent, modulePath, props, visitedGavs);
            }
        }
    }

    private Model readParentModel(Model m, Path modulePath) {
        try {
            if (m == null || m.getParent() == null) return null;
            String pg = m.getParent().getGroupId();
            String pa = m.getParent().getArtifactId();
            String pv = m.getParent().getVersion();
            try { System.out.println("[deploy-manifest] readParentModel for " + m.getArtifactId() + " -> " + pg + ":" + pa + ":" + pv + " (modulePath=" + modulePath + ")"); } catch (Throwable ignore) {}
            String relPath = m.getParent().getRelativePath();
            // Prefer explicit relativePath when provided
            if (relPath != null && !relPath.isBlank() && modulePath != null) {
                Path parentPom = modulePath.resolve(relPath).normalize();
                if (Files.exists(parentPom)) {
                    MavenXpp3Reader reader = new MavenXpp3Reader();
                    try (FileReader fr = new FileReader(parentPom.toFile())) {
                        return reader.read(fr);
                    }
                }
            }
            // If no explicit relativePath, try Maven's default ../pom.xml within the reactor
            if ((relPath == null || relPath.isBlank()) && modulePath != null) {
                Path parentPom = modulePath.resolve("..").resolve("pom.xml").normalize();
                if (Files.exists(parentPom)) {
                    MavenXpp3Reader reader = new MavenXpp3Reader();
                    try (FileReader fr = new FileReader(parentPom.toFile())) {
                        Model candidate = reader.read(fr);
                        // Only use this file if it actually matches the declared parent GAV
                        String cg = (candidate.getGroupId() != null ? candidate.getGroupId() : (candidate.getParent() != null ? candidate.getParent().getGroupId() : null));
                        String ca = candidate.getArtifactId();
                        String cv = (candidate.getVersion() != null ? candidate.getVersion() : (candidate.getParent() != null ? candidate.getParent().getVersion() : null));
                        if (Objects.equals(pg, cg) && Objects.equals(pa, ca) && Objects.equals(pv, cv)) {
                            return candidate;
                        }
                    }
                }
            }
            // Fallback to local repository for external parents (e.g., Spring Boot starter parent)
            return readPomFromLocalRepo(pg, pa, pv);
        } catch (Exception e) {
            try { System.out.println("[deploy-manifest] readParentModel failed: " + e.getMessage()); } catch (Throwable ignore) {}
            return null;
        }
    }

    /**
     * Collect licenses from given model, following parent POMs until a non-empty <licenses> is found.
     */
    private java.util.List<org.apache.maven.model.License> collectLicensesWithFallback(Model m) {
        java.util.Set<String> visited = new java.util.HashSet<>();
        Model cur = m;
        while (cur != null) {
            if (cur.getLicenses() != null && !cur.getLicenses().isEmpty()) {
                return cur.getLicenses();
            }
            String key = (cur.getGroupId() == null ? "" : cur.getGroupId()) + ":" +
                         (cur.getArtifactId() == null ? "" : cur.getArtifactId()) + ":" +
                         (cur.getVersion() == null ? "" : cur.getVersion());
            if (!visited.add(key)) break;
            cur = readParentModel(cur, null);
        }
        return java.util.Collections.emptyList();
    }


    private String resolveProperty(String value, Properties props) {
        if (value == null) return null;
        String result = value;
        Pattern p = Pattern.compile("\\$\\{([^}]+)}");
        for (int i = 0; i < 10; i++) {
            Matcher m = p.matcher(result);
            boolean replaced = false;
            StringBuffer sb = new StringBuffer();
            while (m.find()) {
                String name = m.group(1);
                String repl = props.getProperty(name);
                if (repl == null) repl = m.group(0);
                else replaced = true;
                m.appendReplacement(sb, Matcher.quoteReplacement(repl));
            }
            m.appendTail(sb);
            String next = sb.toString();
            if (!replaced || next.equals(result)) break;
            result = next;
        }
        return result;
    }

    private String nullToEmpty(String v) { return v == null ? "" : v; }
}

