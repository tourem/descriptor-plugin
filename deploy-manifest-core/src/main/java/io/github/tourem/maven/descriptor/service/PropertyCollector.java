package io.github.tourem.maven.descriptor.service;

import io.github.tourem.maven.descriptor.model.BuildProperties;
import io.github.tourem.maven.descriptor.model.ProfilesInfo;
import io.github.tourem.maven.descriptor.model.PropertyOptions;
import lombok.extern.slf4j.Slf4j;
import org.apache.maven.model.Model;
import org.apache.maven.model.Profile;

import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Collects Maven properties, system properties, environment variables and profile info.
 */
@Slf4j
public class PropertyCollector {

    public record Result(BuildProperties properties, ProfilesInfo profiles) {}

    public Result collect(Model rootModel, Path projectRoot, PropertyOptions options) {
        if (options == null || !options.isInclude()) {
            return new Result(null, collectProfiles(rootModel));
        }
        Set<String> sensitive = buildSensitiveSet(options);
        AtomicInteger masked = new AtomicInteger();

        Map<String, String> project = new LinkedHashMap<>();
        project.put("project.groupId", resolveGroupId(rootModel));
        project.put("project.artifactId", rootModel.getArtifactId());
        project.put("project.version", resolveVersion(rootModel));
        project.put("project.packaging", rootModel.getPackaging() == null ? "jar" : rootModel.getPackaging());
        if (rootModel.getName() != null) project.put("project.name", rootModel.getName());

        Properties pomProps = rootModel.getProperties() == null ? new Properties() : rootModel.getProperties();
        Map<String, String> pomMap = toStringMap(pomProps);

        Map<String, String> maven = new TreeMap<>();
        Map<String, String> custom = new TreeMap<>();

        // Categorize pom properties
        for (Map.Entry<String, String> e : pomMap.entrySet()) {
            String k = e.getKey();
            String v = e.getValue();
            if (k.startsWith("project.")) {
                // already covered in project group
                continue;
            }
            if (k.startsWith("maven.") || isKnownBuildFlag(k)) {
                putMaybeMasked(maven, k, v, sensitive, options, masked);
            } else {
                putMaybeMasked(custom, k, v, sensitive, options, masked);
            }
        }

        Map<String, String> system = null;
        if (options.isIncludeSystemProperties()) {
            system = new TreeMap<>();
            Properties sys = System.getProperties();
            for (String name : sys.stringPropertyNames()) {
                putMaybeMasked(system, name, sys.getProperty(name), sensitive, options, masked);
            }
        }

        Map<String, String> environment = null;
        if (options.isIncludeEnvironmentVariables()) {
            environment = new TreeMap<>();
            for (Map.Entry<String, String> e : System.getenv().entrySet()) {
                putMaybeMasked(environment, e.getKey(), e.getValue(), sensitive, options, masked);
            }
        }

        BuildProperties properties = BuildProperties.builder()
                .project(emptyToNull(project))
                .maven(emptyToNull(maven))
                .custom(emptyToNull(custom))
                .system(emptyToNull(system))
                .environment(emptyToNull(environment))
                .maskedCount(masked.get())
                .build();

        return new Result(properties, collectProfiles(rootModel));
    }

    public ProfilesInfo collectProfiles(Model model) {
        List<String> available = new ArrayList<>();
        String defaultProfile = null;
        if (model.getProfiles() != null) {
            for (Profile p : model.getProfiles()) {
                available.add(p.getId());
                if (defaultProfile == null && p.getActivation() != null && Boolean.TRUE.equals(p.getActivation().isActiveByDefault())) {
                    defaultProfile = p.getId();
                }
            }
        }
        return ProfilesInfo.builder()
                .available(available.isEmpty() ? null : available)
                .defaultProfile(defaultProfile)
                .build();
    }

    private static Map<String, String> toStringMap(Properties props) {
        Map<String, String> out = new LinkedHashMap<>();
        for (String name : props.stringPropertyNames()) {
            out.put(name, props.getProperty(name));
        }
        return out;
    }

    private static boolean isKnownBuildFlag(String key) {
        String k = key.toLowerCase(Locale.ROOT);
        return k.equals("skiptests") ||
               k.equals("maven.test.skip") ||
               k.equals("maven.javadoc.skip") ||
               k.equals("maven.deploy.skip") ||
               k.equals("enforcer.skip") ||
               k.startsWith("maven.compiler.");
    }

    private static void putMaybeMasked(Map<String, String> target, String key, String value,
                                       Set<String> sensitive, PropertyOptions options, AtomicInteger masked) {
        if (key == null) return;
        String k = key.trim();
        if (value == null) value = "";
        String v = value;
        if (options.isFilterSensitiveProperties() && isSensitiveKey(k, sensitive)) {
            if (options.isMaskSensitiveValues()) {
                v = "***MASKED***";
                masked.incrementAndGet();
                target.put(k, v);
            } else {
                // drop
            }
        } else {
            target.put(k, v);
        }
    }

    private static boolean isSensitiveKey(String key, Set<String> sensitive) {
        String lower = key.toLowerCase(Locale.ROOT);
        for (String token : sensitive) {
            if (lower.contains(token)) return true;
        }
        return false;
    }

    private static Set<String> buildSensitiveSet(PropertyOptions options) {
        Set<String> s = new HashSet<>(PropertyOptions.defaultSensitiveKeys());
        if (options.getPropertyExclusions() != null) {
            for (String extra : options.getPropertyExclusions()) {
                if (extra != null && !extra.isBlank()) {
                    s.add(extra.toLowerCase(Locale.ROOT).trim());
                }
            }
        }
        return s;
    }

    private static Map<String, String> emptyToNull(Map<String, String> in) {
        return in == null || in.isEmpty() ? null : in;
    }

    private static String resolveGroupId(Model model) {
        if (model.getGroupId() != null) return model.getGroupId();
        if (model.getParent() != null && model.getParent().getGroupId() != null) return model.getParent().getGroupId();
        return null;
    }

    private static String resolveVersion(Model model) {
        if (model.getVersion() != null) return model.getVersion();
        if (model.getParent() != null && model.getParent().getVersion() != null) return model.getParent().getVersion();
        return null;
    }
}

