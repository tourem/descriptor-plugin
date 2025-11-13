package io.github.tourem.maven.descriptor.service;

import io.github.tourem.maven.descriptor.model.ContainerInfo;
import lombok.extern.slf4j.Slf4j;
import org.apache.maven.model.Model;
import org.apache.maven.model.Plugin;
import org.apache.maven.model.PluginExecution;
import org.codehaus.plexus.util.xml.Xpp3Dom;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Detects Docker/OCI container image configuration from common, maintained Maven plugins.
 * Supported tools: Jib, Spring Boot build-image, Fabric8 Docker, Quarkus, Micronaut.
 * @author tourem

 */
@Slf4j
public class DockerImageDetector {

    private static final String JIB_G = "com.google.cloud.tools";
    private static final String JIB_A = "jib-maven-plugin";

    private static final String BOOT_G = "org.springframework.boot";
    private static final String BOOT_A = "spring-boot-maven-plugin";

    private static final String FABRIC8_G = "io.fabric8";
    private static final String FABRIC8_A = "docker-maven-plugin";

    private static final String QUARKUS_G = "io.quarkus";
    private static final String QUARKUS_A = "quarkus-maven-plugin";

    private static final String MICRONAUT_G = "io.micronaut.maven";
    private static final String MICRONAUT_A = "micronaut-maven-plugin";

    private static final String JKUBE_G = "org.eclipse.jkube";
    private static final String JKUBE_K8S_A = "kubernetes-maven-plugin";
    private static final String JKUBE_OS_A = "openshift-maven-plugin";

    public ContainerInfo detect(Model model, Path modulePath) {
        List<Plugin> plugins = getPlugins(model);

        // Prefer explicit tools if configured
        Plugin jib = findPlugin(plugins, JIB_G, JIB_A);
        if (jib != null) {
            return detectJib(jib);
        }

        Plugin boot = findPlugin(plugins, BOOT_G, BOOT_A);
        if (boot != null && hasGoal(boot, "build-image")) {
            ContainerInfo info = detectSpringBoot(boot);
            if (info != null) return info;
        }

        Plugin quarkus = findPlugin(plugins, QUARKUS_G, QUARKUS_A);
        if (quarkus != null) {
            ContainerInfo info = detectQuarkus(quarkus, model, modulePath);
            if (info != null) return info;
        }

        Plugin fabric8 = findPlugin(plugins, FABRIC8_G, FABRIC8_A);
        if (fabric8 != null) {
            ContainerInfo info = detectFabric8(fabric8);
            if (info != null) return info;
        }

        Plugin micronaut = findPlugin(plugins, MICRONAUT_G, MICRONAUT_A);
        if (micronaut != null) {
            ContainerInfo info = detectMicronaut(micronaut);
            if (info != null) return info;
        }

        // Eclipse JKube (Kubernetes/Openshift)
        Plugin jkubeK8s = findPlugin(plugins, JKUBE_G, JKUBE_K8S_A);
        if (jkubeK8s != null) {
            ContainerInfo info = detectJKube(jkubeK8s);
            if (info != null) return info;
        }
        Plugin jkubeOs = findPlugin(plugins, JKUBE_G, JKUBE_OS_A);
        if (jkubeOs != null) {
            ContainerInfo info = detectJKube(jkubeOs);
            if (info != null) return info;
        }

        return null;
    }

    // ---------------- Jib ----------------
    private ContainerInfo detectJib(Plugin plugin) {
        try {
            Xpp3Dom cfg = (Xpp3Dom) plugin.getConfiguration();
            String toImage = getNestedValue(cfg, "to", "image");
            List<String> tags = getNestedValues(cfg, List.of("to", "tags", "tag"));
            String baseImage = getNestedValue(cfg, "from", "image");

            if (toImage == null && baseImage == null) {
                // Try executions config
                for (PluginExecution ex : plugin.getExecutions()) {
                    Xpp3Dom exCfg = (Xpp3Dom) ex.getConfiguration();
                    if (exCfg != null) {
                        if (toImage == null) toImage = getNestedValue(exCfg, "to", "image");
                        if (baseImage == null) baseImage = getNestedValue(exCfg, "from", "image");
                        if (tags == null || tags.isEmpty()) tags = getNestedValues(exCfg, List.of("to", "tags", "tag"));
                    }
                }
            }

            if (toImage == null && (tags == null || tags.isEmpty())) {
                return null;
            }

            String registry = null, group = null, imageRepo = null;
            if (toImage != null) {
                String[] parts = toImage.split("/");
                if (parts.length >= 3 && parts[0].contains(".")) {
                    registry = parts[0];
                    group = parts[1];
                    imageRepo = String.join("/", Arrays.copyOfRange(parts, 2, parts.length));
                } else if (parts.length >= 2) {
                    group = parts[0];
                    imageRepo = parts[1];
                } else {
                    imageRepo = parts[0];
                }
            }

            return ContainerInfo.builder()
                    .tool("jib")
                    .image(toImage)
                    .registry(registry)
                    .group(group)
                    .tag(tags != null && !tags.isEmpty() ? tags.get(0) : null)
                    .additionalTags(tags != null && tags.size() > 1 ? tags.subList(1, tags.size()) : null)
                    .baseImage(baseImage)
                    .publish(null)
                    .build();
        } catch (Exception e) {
            log.debug("Error detecting Jib config", e);
            return null;
        }
    }

    // ---------------- Spring Boot build-image ----------------
    private ContainerInfo detectSpringBoot(Plugin plugin) {
        try {
            Xpp3Dom cfg = (Xpp3Dom) plugin.getConfiguration();
            String name = getNestedValue(cfg, "image", "name");
            List<String> tags = getNestedValues(cfg, List.of("image", "tags", "tag"));
            String builder = getNestedValue(cfg, "image", "builder");
            String runImage = getNestedValue(cfg, "image", "runImage");
            Boolean publish = getNestedBoolean(cfg, "image", "publish");

            String registry = null, group = null;
            if (name != null) {
                String[] parts = name.split("/");
                if (parts.length >= 3 && parts[0].contains(".")) {
                    registry = parts[0];
                    group = parts[1];
                } else if (parts.length >= 2) {
                    group = parts[0];
                }
            }

            return ContainerInfo.builder()
                    .tool("spring-boot")
                    .image(name)
                    .registry(registry)
                    .group(group)
                    .tag(tags != null && !tags.isEmpty() ? tags.get(0) : null)
                    .additionalTags(tags != null && tags.size() > 1 ? tags.subList(1, tags.size()) : null)
                    .builderImage(builder)
                    .runImage(runImage)
                    .publish(publish)
                    .build();
        } catch (Exception e) {
            log.debug("Error detecting Spring Boot build-image config", e);
            return null;
        }
    }

    // ---------------- Fabric8 Docker ----------------
    private ContainerInfo detectFabric8(Plugin plugin) {
        try {
            Xpp3Dom cfg = (Xpp3Dom) plugin.getConfiguration();
            if (cfg == null) return null;

            // <images><image><name>...</name><build><tags><tag>...</tag></tags></build></image></images>
            Xpp3Dom images = cfg.getChild("images");
            if (images == null) return null;
            Xpp3Dom firstImage = Arrays.stream(images.getChildren("image")).findFirst().orElse(null);
            if (firstImage == null) return null;
            String name = optChildValue(firstImage, "name");
            List<String> tags = new ArrayList<>();
            Xpp3Dom build = firstImage.getChild("build");
            if (build != null) {
                Xpp3Dom tagsNode = build.getChild("tags");
                if (tagsNode != null) {
                    for (Xpp3Dom tag : tagsNode.getChildren("tag")) {
                        if (tag.getValue() != null) tags.add(tag.getValue());
                    }
                }
                String baseImage = optChildValue(build, "from");
                String registry = null, group = null;
                if (name != null) {
                    String[] parts = name.split("/");
                    if (parts.length >= 3 && parts[0].contains(".")) {
                        registry = parts[0];
                        group = parts[1];
                    } else if (parts.length >= 2) {
                        group = parts[0];
                    }
                }
                return ContainerInfo.builder()
                        .tool("fabric8")
                        .image(name)
                        .registry(registry)
                        .group(group)
                        .tag(!tags.isEmpty() ? tags.get(0) : null)
                        .additionalTags(tags.size() > 1 ? tags.subList(1, tags.size()) : null)
                        .baseImage(baseImage)
                        .build();
            }
            return null;
        } catch (Exception e) {
            log.debug("Error detecting Fabric8 config", e);
            return null;
        }
    }

    // ---------------- Eclipse JKube ----------------
    private ContainerInfo detectJKube(Plugin plugin) {
        try {
            Xpp3Dom cfg = (Xpp3Dom) plugin.getConfiguration();
            if (cfg == null) return null;
            Xpp3Dom images = cfg.getChild("images");
            if (images == null) return null;
            Xpp3Dom firstImage = Arrays.stream(images.getChildren("image")).findFirst().orElse(null);
            if (firstImage == null) return null;
            String name = optChildValue(firstImage, "name");
            List<String> tags = new ArrayList<>();
            Xpp3Dom build = firstImage.getChild("build");
            if (build != null) {
                Xpp3Dom tagsNode = build.getChild("tags");
                if (tagsNode != null) {
                    for (Xpp3Dom tag : tagsNode.getChildren("tag")) {
                        if (tag.getValue() != null) tags.add(tag.getValue());
                    }
                }
                String baseImage = optChildValue(build, "from");
                String registry = null, group = null;
                if (name != null) {
                    String[] parts = name.split("/");
                    if (parts.length >= 3 && parts[0].contains(".")) {
                        registry = parts[0];
                        group = parts[1];
                    } else if (parts.length >= 2) {
                        group = parts[0];
                    }
                }
                return ContainerInfo.builder()
                        .tool("jkube")
                        .image(name)
                        .registry(registry)
                        .group(group)
                        .tag(!tags.isEmpty() ? tags.get(0) : null)
                        .additionalTags(tags.size() > 1 ? tags.subList(1, tags.size()) : null)
                        .baseImage(baseImage)
                        .build();
            }
            return null;
        } catch (Exception e) {
            log.debug("Error detecting Eclipse JKube config", e);
            return null;
        }
    }

    // ---------------- Quarkus ----------------
    private ContainerInfo detectQuarkus(Plugin plugin, Model model, Path modulePath) {
        try {
            Xpp3Dom cfg = (Xpp3Dom) plugin.getConfiguration();
            String registry = getNestedValue(cfg, "containerImage", "registry");
            String group = getNestedValue(cfg, "containerImage", "group");
            String name = getNestedValue(cfg, "containerImage", "name");
            String tag = getNestedValue(cfg, "containerImage", "tag");
            List<String> addTags = getNestedValues(cfg, List.of("containerImage", "additionalTags", "tag"));

            if (name == null) {
                // Try properties on POM
                Properties props = model.getProperties();
                if (props != null) {
                    if (registry == null) registry = props.getProperty("quarkus.container-image.registry");
                    if (group == null) group = props.getProperty("quarkus.container-image.group");
                    if (name == null) name = props.getProperty("quarkus.container-image.name");
                    if (tag == null) tag = props.getProperty("quarkus.container-image.tag");
                    String at = props.getProperty("quarkus.container-image.additional-tags");
                    if (addTags == null && at != null && !at.isBlank()) {
                        addTags = Arrays.stream(at.split(","))
                                .map(String::trim)
                                .filter(s -> !s.isEmpty())
                                .collect(Collectors.toList());
                    }
                }
            }

            if (name == null) {
                // Try application.properties
                try {
                    Path propsPath = modulePath.resolve("src/main/resources/application.properties");
                    if (Files.exists(propsPath)) {
                        List<String> lines = Files.readAllLines(propsPath);
                        for (String line : lines) {
                            String trimmed = line.trim();
                            if (trimmed.startsWith("quarkus.container-image.registry=") && registry == null) {
                                registry = trimmed.substring(trimmed.indexOf('=') + 1).trim();
                            } else if (trimmed.startsWith("quarkus.container-image.group=") && group == null) {
                                group = trimmed.substring(trimmed.indexOf('=') + 1).trim();
                            } else if (trimmed.startsWith("quarkus.container-image.name=") && name == null) {
                                name = trimmed.substring(trimmed.indexOf('=') + 1).trim();
                            } else if (trimmed.startsWith("quarkus.container-image.tag=") && tag == null) {
                                tag = trimmed.substring(trimmed.indexOf('=') + 1).trim();
                            } else if (trimmed.startsWith("quarkus.container-image.additional-tags=") && addTags == null) {
                                String at = trimmed.substring(trimmed.indexOf('=') + 1).trim();
                                addTags = Arrays.stream(at.split(",")).map(String::trim).filter(s -> !s.isEmpty()).collect(Collectors.toList());
                            }
                        }
                    }
                } catch (IOException ignored) { }
            }

            if (name == null && (registry == null && group == null)) {
                return null;
            }

            String image = (registry != null ? registry + "/" : "") + (group != null ? group + "/" : "") + name;
            return ContainerInfo.builder()
                    .tool("quarkus")
                    .image(image)
                    .registry(registry)
                    .group(group)
                    .tag(tag)
                    .additionalTags(addTags)
                    .build();
        } catch (Exception e) {
            log.debug("Error detecting Quarkus container-image config", e);
            return null;
        }
    }

    // ---------------- Micronaut ----------------
    private ContainerInfo detectMicronaut(Plugin plugin) {
        try {
            Xpp3Dom cfg = (Xpp3Dom) plugin.getConfiguration();
            String registry = getNestedValue(cfg, "dockerRegistry");
            String group = getNestedValue(cfg, "dockerGroup");
            String name = getNestedValue(cfg, "dockerName");
            String tag = getNestedValue(cfg, "dockerTag");
            List<String> addTags = getNestedValues(cfg, List.of("dockerExtraTags", "tag"));

            if (name == null && (registry == null && group == null)) {
                return null;
            }
            String image = (registry != null ? registry + "/" : "") + (group != null ? group + "/" : "") + name;
            return ContainerInfo.builder()
                    .tool("micronaut")
                    .image(image)
                    .registry(registry)
                    .group(group)
                    .tag(tag)
                    .additionalTags(addTags)
                    .build();
        } catch (Exception e) {
            log.debug("Error detecting Micronaut container config", e);
            return null;
        }
    }

    // ---------------- helpers ----------------
    private List<Plugin> getPlugins(Model model) {
        List<Plugin> list = new ArrayList<>();
        if (model.getBuild() != null) {
            if (model.getBuild().getPlugins() != null) list.addAll(model.getBuild().getPlugins());
            if (model.getBuild().getPluginManagement() != null && model.getBuild().getPluginManagement().getPlugins() != null) {
                list.addAll(model.getBuild().getPluginManagement().getPlugins());
            }
        }
        return list;
    }

    private Plugin findPlugin(List<Plugin> plugins, String groupId, String artifactId) {
        return plugins.stream()
                .filter(p -> {
                    String g = p.getGroupId() != null ? p.getGroupId() : "org.apache.maven.plugins";
                    return groupId.equals(g) && artifactId.equals(p.getArtifactId());
                })
                .findFirst()
                .orElse(null);
    }

    private boolean hasGoal(Plugin plugin, String goal) {
        if (plugin.getExecutions() == null || plugin.getExecutions().isEmpty()) {
            return true; // assume default goals
        }
        for (PluginExecution ex : plugin.getExecutions()) {
            if (ex.getGoals() != null && ex.getGoals().stream().anyMatch(goal::equals)) return true;
        }
        return false;
    }

    private String getNestedValue(Xpp3Dom cfg, String... path) {
        if (cfg == null) return null;
        Xpp3Dom node = cfg;
        for (String p : path) {
            node = node != null ? node.getChild(p) : null;
        }
        return node != null ? node.getValue() : null;
    }

    private List<String> getNestedValues(Xpp3Dom cfg, List<String> path) {
        if (cfg == null) return null;
        // last element is the repeating node name, previous are containers
        if (path.isEmpty()) return null;
        Xpp3Dom node = cfg;
        for (int i = 0; i < path.size() - 1; i++) {
            node = node != null ? node.getChild(path.get(i)) : null;
        }
        if (node == null) return null;
        String item = path.get(path.size() - 1);
        Xpp3Dom[] children = node.getChildren(item);
        if (children == null || children.length == 0) return List.of();
        List<String> values = new ArrayList<>();
        for (Xpp3Dom c : children) {
            if (c.getValue() != null) values.add(c.getValue());
        }
        return values;
    }

    private String optChildValue(Xpp3Dom node, String child) {
        if (node == null) return null;
        Xpp3Dom c = node.getChild(child);
        return c != null ? c.getValue() : null;
    }

    private Boolean getNestedBoolean(Xpp3Dom cfg, String... path) {
        String v = getNestedValue(cfg, path);
        if (v == null) return null;
        return Boolean.parseBoolean(v.trim());
    }
}

