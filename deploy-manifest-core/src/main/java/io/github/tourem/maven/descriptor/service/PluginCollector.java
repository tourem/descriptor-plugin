package io.github.tourem.maven.descriptor.service;

import io.github.tourem.maven.descriptor.model.*;
import lombok.extern.slf4j.Slf4j;
import org.apache.maven.model.Build;
import org.apache.maven.model.Model;
import org.apache.maven.model.Plugin;
import org.apache.maven.model.PluginExecution;
import org.codehaus.plexus.util.xml.Xpp3Dom;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Collects information about Maven build plugins from a Maven model.
 * author: tourem
 */
@Slf4j
public class PluginCollector {

    public PluginInfo collect(Model model, Path modulePath, PluginOptions options) {
        if (model == null) return null;
        Build build = model.getBuild();
        if (build == null) {
            return PluginInfo.builder()
                    .summary(PluginSummary.builder().total(0).withConfiguration(0).fromManagement(0).outdated(0).build())
                    .list(Collections.emptyList())
                    .management(Collections.emptyList())
                    .outdated(Collections.emptyList())
                    .build();
        }

        List<Plugin> effectivePlugins = Optional.ofNullable(build.getPlugins()).orElse(Collections.emptyList());
        List<Plugin> mgmtPlugins = Optional.ofNullable(build.getPluginManagement() != null ? build.getPluginManagement().getPlugins() : null)
                .orElse(Collections.emptyList());

        Map<String, String> mgmtVersionIndex = new HashMap<>();
        for (Plugin p : mgmtPlugins) {
            String key = (p.getGroupId() == null ? "" : p.getGroupId()) + ":" + p.getArtifactId();
            if (p.getVersion() != null) mgmtVersionIndex.put(key, p.getVersion());
        }

        Set<String> usedKeys = new HashSet<>();
        List<PluginDetail> details = new ArrayList<>();
        int withConfig = 0;
        int fromMgmt = 0;
        int outdatedCount = 0;
        List<PluginDetail> outdatedList = new ArrayList<>();

        for (Plugin p : effectivePlugins) {
            String g = p.getGroupId();
            String a = p.getArtifactId();
            String v = p.getVersion();
            if (v == null) {
                String key = (g == null ? "" : g) + ":" + a;
                v = mgmtVersionIndex.get(key);
            }
            String source = "effective";
            boolean inherited = p.isInherited();

            // Primary phase from executions (first non-null)
            String phase = null;
            List<String> goals = new ArrayList<>();
            List<PluginExecutionInfo> execInfos = new ArrayList<>();
            if (p.getExecutions() != null) {
                for (PluginExecution ex : p.getExecutions()) {
                    if (phase == null && ex.getPhase() != null) phase = ex.getPhase();
                    if (ex.getGoals() != null) {
                        for (Object gobj : ex.getGoals()) {
                            if (gobj != null) goals.add(String.valueOf(gobj));
                        }
                    }
                    java.util.List<String> goalsCopy = new java.util.ArrayList<>();
                    if (ex.getGoals() != null) {
                        for (Object gobj : ex.getGoals()) {
                            if (gobj != null) goalsCopy.add(String.valueOf(gobj));
                        }
                    }
                    execInfos.add(PluginExecutionInfo.builder()
                            .id(ex.getId())
                            .phase(ex.getPhase())
                            .goals(goalsCopy)
                            .build());
                }
            }
            // plugin-level goals if any (rare)
            try {
                java.lang.reflect.Method m = p.getClass().getMethod("getGoals");
                Object gos = m.invoke(p);
                if (gos instanceof java.util.Collection<?> coll) {
                    for (Object it : coll) if (it != null) goals.add(String.valueOf(it));
                }
            } catch (Exception ignore) { /* older maven-model may not have getGoals() */ }
            // unique goals
            goals = goals.stream().filter(Objects::nonNull).distinct().collect(Collectors.toList());

            Object configuration = null;
            if (options != null && options.isIncludePluginConfiguration()) {
                configuration = sanitizeConfiguration(p.getConfiguration(), options);
                if (configuration != null) withConfig++;
            }

            PluginDetail.PluginDetailBuilder builder = PluginDetail.builder()
                    .groupId(g)
                    .artifactId(a)
                    .version(v)
                    .source(source)
                    .inherited(inherited)
                    .phase(phase)
                    .goals(goals.isEmpty() ? null : goals)
                    .executions(execInfos.isEmpty() ? null : execInfos)
                    .configuration(configuration);

            // Optional: check updates
            if (options != null && options.isCheckPluginUpdates() && g != null && a != null && v != null) {
                try {
                    String latest = fetchLatestReleaseVersion(g, a, options.getUpdateCheckTimeoutMillis());
                    if (latest != null && !latest.equals(v)) {
                        int behind = nullSafeCompareOrdinal(v, latest) ? 1 : 1; // best-effort
                        builder.outdated(PluginOutdatedInfo.builder().current(v).latest(latest).behind(behind).build());
                        outdatedCount++;
                    }
                } catch (Exception e) {
                    log.debug("Plugin update check failed for {}:{} - {}", g, a, e.toString());
                }
            }

            PluginDetail d = builder.build();
            details.add(d);
            usedKeys.add((g == null ? "" : g) + ":" + a);
        }

        List<PluginManagementEntry> managementEntries = new ArrayList<>();
        if (options == null || options.isIncludePluginManagement()) {
            for (Plugin p : mgmtPlugins) {
                String key = (p.getGroupId() == null ? "" : p.getGroupId()) + ":" + p.getArtifactId();
                boolean used = usedKeys.contains(key);
                managementEntries.add(PluginManagementEntry.builder()
                        .groupId(p.getGroupId())
                        .artifactId(p.getArtifactId())
                        .version(p.getVersion())
                        .usedInBuild(used)
                        .build());
                if ("management".equalsIgnoreCase("management")) fromMgmt++; // count mgmt entries for summary
            }
        }

        PluginSummary summary = PluginSummary.builder()
                .total(details.size())
                .withConfiguration(withConfig)
                .fromManagement(managementEntries.size())
                .outdated(outdatedCount)
                .build();

        return PluginInfo.builder()
                .summary(summary)
                .list(details)
                .management(managementEntries)
                .outdated(outdatedList.isEmpty() ? null : outdatedList)
                .build();
    }

    private Object sanitizeConfiguration(Object cfg, PluginOptions options) {
        if (cfg == null) return null;
        if (!(cfg instanceof Xpp3Dom dom)) return cfg;
        Map<String, Object> asMap = (Map<String, Object>) domToObject(dom);
        if (options != null && options.isFilterSensitivePluginConfig()) {
            maskSensitive(asMap);
        }
        return asMap;
    }

    private Object domToObject(Xpp3Dom node) {
        if (node == null) return null;
        Xpp3Dom[] children = node.getChildren();
        if (children == null || children.length == 0) {
            String value = node.getValue();
            return value == null ? "" : value;
        }
        // group by name to decide map or list
        Map<String, List<Xpp3Dom>> byName = new LinkedHashMap<>();
        for (Xpp3Dom c : children) {
            byName.computeIfAbsent(c.getName(), k -> new ArrayList<>()).add(c);
        }
        Map<String, Object> map = new LinkedHashMap<>();
        for (Map.Entry<String, List<Xpp3Dom>> e : byName.entrySet()) {
            if (e.getValue().size() == 1) {
                map.put(e.getKey(), domToObject(e.getValue().get(0)));
            } else {
                List<Object> list = new ArrayList<>();
                for (Xpp3Dom c : e.getValue()) list.add(domToObject(c));
                map.put(e.getKey(), list);
            }
        }
        return map;
    }

    private void maskSensitive(Object obj) {
        if (obj == null) return;
        Set<String> sensitive = Set.of("password", "pwd", "secret", "token", "apikey", "api-key", "api_key", "key", "credentials", "auth", "username");
        if (obj instanceof Map<?, ?> m) {
            for (Object k : new ArrayList<>(m.keySet())) {
                Object v = m.get(k);
                if (k != null && k instanceof String s) {
                    String sl = s.toLowerCase(Locale.ROOT);
                    if (sensitive.stream().anyMatch(sl::contains)) {
                        ((Map<Object, Object>) m).put(k, "***MASKED***");
                        continue;
                    }
                }
                maskSensitive(v);
            }
        } else if (obj instanceof List<?> list) {
            for (Object it : list) maskSensitive(it);
        }
    }

    private String fetchLatestReleaseVersion(String groupId, String artifactId, int timeoutMillis) throws Exception {
        String path = groupId.replace('.', '/') + "/" + artifactId + "/maven-metadata.xml";
        String url = "https://repo1.maven.org/maven2/" + path;
        HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofMillis(timeoutMillis)).build();
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofMillis(timeoutMillis))
                .header("User-Agent", "deploy-manifest-plugin/1.0")
                .GET()
                .build();
        HttpResponse<byte[]> resp = client.send(request, HttpResponse.BodyHandlers.ofByteArray());
        if (resp.statusCode() >= 200 && resp.statusCode() < 300) {
            String xml = new String(resp.body(), StandardCharsets.UTF_8);
            // very small parser: try <release> first, fallback to <latest>
            String release = extractTag(xml, "release");
            if (release == null || release.isBlank()) release = extractTag(xml, "latest");
            return (release == null || release.isBlank()) ? null : release.trim();
        }
        return null;
    }

    private String extractTag(String xml, String tag) {
        String start = "<" + tag + ">";
        String end = "</" + tag + ">";
        int i = xml.indexOf(start);
        int j = xml.indexOf(end);
        if (i >= 0 && j > i) return xml.substring(i + start.length(), j);
        return null;
    }

    // very naive comparator: return true if likely behind
    private boolean nullSafeCompareOrdinal(String current, String latest) {
        try {
            if (Objects.equals(current, latest)) return false;
            List<String> c = Arrays.asList(current.split("\\."));
            List<String> l = Arrays.asList(latest.split("\\."));
            int n = Math.max(c.size(), l.size());
            for (int i = 0; i < n; i++) {
                int ci = i < c.size() ? parseIntSafe(c.get(i)) : 0;
                int li = i < l.size() ? parseIntSafe(l.get(i)) : 0;
                if (li > ci) return true;
                if (li < ci) return false;
            }
        } catch (Exception ignore) { }
        return true;
    }

    private int parseIntSafe(String s) {
        try { return Integer.parseInt(s.replaceAll("[^0-9]", "").trim()); } catch (Exception e) { return 0; }
    }
}

