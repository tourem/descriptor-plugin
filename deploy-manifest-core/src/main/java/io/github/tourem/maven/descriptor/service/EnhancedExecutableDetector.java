package io.github.tourem.maven.descriptor.service;

import io.github.tourem.maven.descriptor.model.ExecutableInfo;
import io.github.tourem.maven.descriptor.model.ExecutableType;
import lombok.extern.slf4j.Slf4j;
import org.apache.maven.model.Dependency;
import org.apache.maven.model.Model;
import org.apache.maven.model.Plugin;
import org.apache.maven.model.PluginExecution;
import org.codehaus.plexus.util.xml.Xpp3Dom;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Enhanced detector for Maven executable artifacts.
 * Implements comprehensive detection for all Maven executable patterns including:
 * - Spring Boot applications (with or without plugin)
 * - maven-shade-plugin
 * - maven-assembly-plugin
 * - maven-jar-plugin + maven-dependency-plugin
 * - WAR executables (Spring Boot, Jetty, Tomcat)
 * - EAR applications
 * @author tourem

 */
@Slf4j
public class EnhancedExecutableDetector {

    private static final String SPRING_BOOT_GROUP = "org.springframework.boot";
    private static final String SPRING_BOOT_PLUGIN = "spring-boot-maven-plugin";

    /**
     * Detect if a module generates an executable artifact.
     *
     * @param model Maven model to analyze
     * @param modulePath Path to the module directory
     * @return ExecutableInfo with detection results
     */
    public ExecutableInfo detectExecutable(Model model, Path modulePath) {
        String packaging = model.getPackaging() != null ? model.getPackaging() : "jar";

        log.debug("Detecting executable for module: {} (packaging: {})", model.getArtifactId(), packaging);

        // Detect based on packaging type
        switch (packaging.toLowerCase()) {
            case "jar":
                return detectExecutableJar(model, modulePath);
            case "war":
                return detectExecutableWar(model, modulePath);
            case "ear":
                return detectEar(model);
            default:
                log.debug("Packaging type '{}' is not executable", packaging);
                return ExecutableInfo.notExecutable();
        }
    }

    /**
     * Detect executable JAR artifacts.
     */
    private ExecutableInfo detectExecutableJar(Model model, Path modulePath) {
        List<Plugin> plugins = getPlugins(model);

        // 1. Spring Boot Maven Plugin
        Plugin springBootPlugin = findPlugin(plugins, SPRING_BOOT_GROUP, SPRING_BOOT_PLUGIN);
        if (springBootPlugin != null && hasRepackageGoal(springBootPlugin)) {
            return buildSpringBootJarInfo(model, springBootPlugin, modulePath);
        }

        // 2. Maven Shade Plugin
        Plugin shadePlugin = findPlugin(plugins, "org.apache.maven.plugins", "maven-shade-plugin");
        if (shadePlugin != null && hasGoal(shadePlugin, "shade")) {
            return buildShadePluginInfo(model, shadePlugin);
        }

        // 3. Maven Assembly Plugin
        Plugin assemblyPlugin = findPlugin(plugins, "org.apache.maven.plugins", "maven-assembly-plugin");
        if (assemblyPlugin != null && hasGoal(assemblyPlugin, "single")) {
            return buildAssemblyPluginInfo(model, assemblyPlugin);
        }

        // 4. Maven Jar + Maven Dependency Plugin
        Plugin dependencyPlugin = findPlugin(plugins, "org.apache.maven.plugins", "maven-dependency-plugin");
        Plugin jarPlugin = findPlugin(plugins, "org.apache.maven.plugins", "maven-jar-plugin");

        if (dependencyPlugin != null && jarPlugin != null) {
            boolean hasUnpackDeps = hasGoal(dependencyPlugin, "unpack-dependencies") ||
                                   hasGoal(dependencyPlugin, "copy-dependencies");
            boolean hasCustomClassesDir = hasCustomClassesDirectory(jarPlugin);

            if (hasUnpackDeps || hasCustomClassesDir) {
                return buildJarDependencyPluginInfo(model, jarPlugin, dependencyPlugin);
            }
        }

        // 5. OneJar Plugin (obsolete)
        Plugin oneJarPlugin = findPlugin(plugins, "com.jolira", "onejar-maven-plugin");
        if (oneJarPlugin != null && hasGoal(oneJarPlugin, "one-jar")) {
            return buildOneJarPluginInfo(model);
        }

        // 6. Spring Boot application WITHOUT plugin (NEW DETECTION)
        // Check if module has Spring Boot dependencies and could be executable
        if (hasSpringBootDependencies(model)) {
            return buildSpringBootWithoutPluginInfo(model, modulePath);
        }

        // Not executable
        return ExecutableInfo.notExecutable();
    }

    /**
     * Detect executable WAR artifacts.
     */
    private ExecutableInfo detectExecutableWar(Model model, Path modulePath) {
        List<Plugin> plugins = getPlugins(model);

        // 1. Spring Boot WAR
        Plugin springBootPlugin = findPlugin(plugins, SPRING_BOOT_GROUP, SPRING_BOOT_PLUGIN);
        if (springBootPlugin != null) {
            return buildSpringBootWarInfo(model, springBootPlugin, modulePath);
        }

        // 2. Jetty Embedded
        Plugin jettyPlugin = findPlugin(plugins, "org.eclipse.jetty", "jetty-maven-plugin");
        if (jettyPlugin != null) {
            return buildJettyWarInfo(model);
        }

        // 3. Tomcat Embedded
        Plugin tomcatPlugin = findPlugin(plugins, "org.apache.tomcat.maven", "tomcat7-maven-plugin");
        if (tomcatPlugin == null) {
            tomcatPlugin = findPlugin(plugins, "org.apache.tomcat.maven", "tomcat8-maven-plugin");
        }
        if (tomcatPlugin != null) {
            return buildTomcatWarInfo(model);
        }

        // 4. Spring Boot application WITHOUT plugin but with WAR packaging
        if (hasSpringBootDependencies(model)) {
            return buildSpringBootWarWithoutPluginInfo(model, modulePath);
        }

        // Traditional WAR (not executable standalone)
        return ExecutableInfo.deployableOnly(ExecutableType.WAR, "maven-war-plugin");
    }

    /**
     * Detect EAR artifacts.
     */
    private ExecutableInfo detectEar(Model model) {
        List<Plugin> plugins = getPlugins(model);
        Plugin earPlugin = findPlugin(plugins, "org.apache.maven.plugins", "maven-ear-plugin");

        if (earPlugin != null) {
            return ExecutableInfo.builder()
                    .type(ExecutableType.EAR)
                    .method("maven-ear-plugin")
                    .executable(false)
                    .deploymentOnly(true)
                    .requiresExternalServer(true)
                    .modules(extractEarModules(earPlugin))
                    .javaEEVersion(extractJavaEEVersion(earPlugin))
                    .build();
        }

        return ExecutableInfo.deployableOnly(ExecutableType.EAR, "maven-ear-plugin");
    }

    /**
     * Build ExecutableInfo for Spring Boot JAR with plugin.
     */
    private ExecutableInfo buildSpringBootJarInfo(Model model, Plugin plugin, Path modulePath) {
        String mainClass = extractMainClass(plugin, model);
        List<String> profiles = detectSpringBootProfiles(modulePath);

        return ExecutableInfo.builder()
                .type(ExecutableType.JAR)
                .method("spring-boot-maven-plugin")
                .executable(true)
                .structure("jar-in-jar")
                .mainClass(mainClass)
                .launcherClass("org.springframework.boot.loader.JarLauncher")
                .runCommand("java -jar target/" + model.getArtifactId() + "-" + model.getVersion() + ".jar")
                .springBootApplication(true)
                .springBootProfiles(profiles.isEmpty() ? null : profiles)
                .build();
    }

    /**
     * Build ExecutableInfo for maven-shade-plugin.
     */
    private ExecutableInfo buildShadePluginInfo(Model model, Plugin plugin) {
        String mainClass = extractMainClassFromShade(plugin);
        List<String> transformers = extractTransformers(plugin);
        boolean isSpringBoot = hasSpringBootDependencies(model);

        return ExecutableInfo.builder()
                .type(ExecutableType.JAR)
                .method("maven-shade-plugin")
                .executable(true)
                .structure("flat-jar")
                .mainClass(mainClass)
                .transformers(transformers.isEmpty() ? null : transformers)
                .runCommand("java -jar target/" + model.getArtifactId() + "-" + model.getVersion() + ".jar")
                .springBootApplication(isSpringBoot)
                .build();
    }

    /**
     * Build ExecutableInfo for maven-assembly-plugin.
     */
    private ExecutableInfo buildAssemblyPluginInfo(Model model, Plugin plugin) {
        String mainClass = extractMainClassFromAssembly(plugin);
        List<String> descriptors = extractAssemblyDescriptors(plugin);
        boolean isSpringBoot = hasSpringBootDependencies(model);

        return ExecutableInfo.builder()
                .type(ExecutableType.JAR)
                .method("maven-assembly-plugin")
                .executable(true)
                .structure("flat-jar")
                .mainClass(mainClass)
                .descriptors(descriptors.isEmpty() ? null : descriptors)
                .runCommand("java -jar target/" + model.getArtifactId() + "-" + model.getVersion() + "-jar-with-dependencies.jar")
                .springBootApplication(isSpringBoot)
                .build();
    }

    /**
     * Build ExecutableInfo for maven-jar-plugin + maven-dependency-plugin.
     */
    private ExecutableInfo buildJarDependencyPluginInfo(Model model, Plugin jarPlugin, Plugin dependencyPlugin) {
        String mainClass = extractMainClassFromJar(jarPlugin);
        boolean isSpringBoot = hasSpringBootDependencies(model);

        return ExecutableInfo.builder()
                .type(ExecutableType.JAR)
                .method("maven-jar-plugin + maven-dependency-plugin")
                .executable(true)
                .structure("flat-jar")
                .mainClass(mainClass)
                .runCommand("java -jar target/" + model.getArtifactId() + "-" + model.getVersion() + ".jar")
                .springBootApplication(isSpringBoot)
                .build();
    }

    /**
     * Build ExecutableInfo for onejar-maven-plugin (obsolete).
     */
    private ExecutableInfo buildOneJarPluginInfo(Model model) {
        return ExecutableInfo.builder()
                .type(ExecutableType.JAR)
                .method("onejar-maven-plugin")
                .executable(true)
                .structure("jar-in-jar")
                .obsolete(true)
                .warning("onejar-maven-plugin is obsolete and doesn't work with Java 9+. Consider migrating to spring-boot-maven-plugin or maven-shade-plugin")
                .runCommand("java -jar target/" + model.getArtifactId() + "-" + model.getVersion() + ".one-jar.jar")
                .build();
    }




    /**
     * Build ExecutableInfo for Spring Boot application WITHOUT plugin (JAR).
     * This handles the case where a module has Spring Boot dependencies but no spring-boot-maven-plugin.
     */
    private ExecutableInfo buildSpringBootWithoutPluginInfo(Model model, Path modulePath) {
        List<String> profiles = detectSpringBootProfiles(modulePath);

        return ExecutableInfo.builder()
                .type(ExecutableType.JAR)
                .method("spring-boot-dependencies (no plugin)")
                .executable(true)
                .structure("requires-plugin")
                .springBootApplication(true)
                .springBootProfiles(profiles.isEmpty() ? null : profiles)
                .warning("Spring Boot dependencies detected but no spring-boot-maven-plugin configured. " +
                        "Add spring-boot-maven-plugin to create an executable JAR, or use maven-shade-plugin/maven-assembly-plugin.")
                .runCommand("Requires plugin configuration to be executable")
                .build();
    }

    /**
     * Build ExecutableInfo for Spring Boot WAR with plugin.
     */
    private ExecutableInfo buildSpringBootWarInfo(Model model, Plugin plugin, Path modulePath) {
        String mainClass = extractMainClass(plugin, model);
        List<String> profiles = detectSpringBootProfiles(modulePath);

        return ExecutableInfo.builder()
                .type(ExecutableType.WAR)
                .method("spring-boot-maven-plugin")
                .executable(true)
                .mainClass(mainClass)
                .launcherClass("org.springframework.boot.loader.WarLauncher")
                .embeddedServer("Tomcat/Jetty/Undertow")
                .servletInitializer(true)
                .runCommand("java -jar target/" + model.getArtifactId() + "-" + model.getVersion() + ".war")
                .springBootApplication(true)
                .springBootProfiles(profiles.isEmpty() ? null : profiles)
                .build();
    }

    /**
     * Build ExecutableInfo for Spring Boot WAR WITHOUT plugin.
     */
    private ExecutableInfo buildSpringBootWarWithoutPluginInfo(Model model, Path modulePath) {
        List<String> profiles = detectSpringBootProfiles(modulePath);

        return ExecutableInfo.builder()
                .type(ExecutableType.WAR)
                .method("spring-boot-dependencies (no plugin)")
                .executable(false)
                .deploymentOnly(true)
                .requiresExternalServer(true)
                .springBootApplication(true)
                .springBootProfiles(profiles.isEmpty() ? null : profiles)
                .warning("Spring Boot dependencies detected but no spring-boot-maven-plugin configured. " +
                        "Add spring-boot-maven-plugin to create an executable WAR with embedded server.")
                .build();
    }

    /**
     * Build ExecutableInfo for Jetty WAR.
     */
    private ExecutableInfo buildJettyWarInfo(Model model) {
        return ExecutableInfo.builder()
                .type(ExecutableType.WAR)
                .method("jetty-maven-plugin")
                .executable(false)
                .embeddedServer("Jetty")
                .runCommand("mvn jetty:run")
                .build();
    }

    /**
     * Build ExecutableInfo for Tomcat WAR.
     */
    private ExecutableInfo buildTomcatWarInfo(Model model) {
        return ExecutableInfo.builder()
                .type(ExecutableType.WAR)
                .method("tomcat7-maven-plugin")
                .executable(false)
                .embeddedServer("Tomcat")
                .runCommand("mvn tomcat7:run")
                .build();
    }

    // ==================== Helper Methods ====================

    /**
     * Get all plugins from model (build plugins + plugin management).
     */
    private List<Plugin> getPlugins(Model model) {
        List<Plugin> allPlugins = new ArrayList<>();

        if (model.getBuild() != null) {
            if (model.getBuild().getPlugins() != null) {
                allPlugins.addAll(model.getBuild().getPlugins());
            }

            if (model.getBuild().getPluginManagement() != null &&
                model.getBuild().getPluginManagement().getPlugins() != null) {
                allPlugins.addAll(model.getBuild().getPluginManagement().getPlugins());
            }
        }

        return allPlugins;
    }

    /**
     * Find a plugin by groupId and artifactId.
     */
    private Plugin findPlugin(List<Plugin> plugins, String groupId, String artifactId) {
        return plugins.stream()
                .filter(p -> {
                    String pGroupId = p.getGroupId() != null ? p.getGroupId() : "org.apache.maven.plugins";
                    return groupId.equals(pGroupId) && artifactId.equals(p.getArtifactId());
                })
                .findFirst()
                .orElse(null);
    }

    /**
     * Check if a plugin has a specific goal.
     */
    private boolean hasGoal(Plugin plugin, String goal) {
        if (plugin.getExecutions() == null || plugin.getExecutions().isEmpty()) {
            // If no executions defined, assume default goals are used
            return true;
        }

        return plugin.getExecutions().stream()
                .flatMap(e -> e.getGoals().stream())
                .anyMatch(g -> goal.equals(g));
    }

    /**
     * Check if Spring Boot plugin has repackage goal.
     */
    private boolean hasRepackageGoal(Plugin plugin) {
        if (plugin.getExecutions() == null || plugin.getExecutions().isEmpty()) {
            // Default execution includes repackage
            return true;
        }

        return hasGoal(plugin, "repackage");
    }

    /**
     * Check if maven-jar-plugin has custom classesDirectory.
     */
    private boolean hasCustomClassesDirectory(Plugin plugin) {
        if (plugin.getConfiguration() == null) {
            return false;
        }

        try {
            Xpp3Dom config = (Xpp3Dom) plugin.getConfiguration();
            Xpp3Dom classesDir = config.getChild("classesDirectory");
            return classesDir != null && classesDir.getValue() != null;
        } catch (Exception e) {
            log.debug("Error checking custom classesDirectory", e);
            return false;
        }
    }


    /**
     * Check if model has Spring Boot dependencies.
     * This is the KEY method for detecting Spring Boot applications without the plugin.
     */
    private boolean hasSpringBootDependencies(Model model) {
        if (model.getDependencies() == null) {
            return false;
        }

        return model.getDependencies().stream()
                .anyMatch(dep -> SPRING_BOOT_GROUP.equals(dep.getGroupId()) &&
                               (dep.getArtifactId().startsWith("spring-boot-starter-") ||
                                "spring-boot".equals(dep.getArtifactId())));
    }

    /**
     * Detect Spring Boot profiles from application properties/yaml files.
     */
    private List<String> detectSpringBootProfiles(Path modulePath) {
        List<String> profiles = new ArrayList<>();

        try {
            Path resourcesPath = modulePath.resolve("src/main/resources");
            if (!Files.exists(resourcesPath)) {
                return profiles;
            }

            // Look for application-{profile}.properties or application-{profile}.yml/yaml
            Files.list(resourcesPath)
                    .filter(Files::isRegularFile)
                    .forEach(file -> {
                        String fileName = file.getFileName().toString();

                        // Match application-{profile}.properties
                        if (fileName.startsWith("application-") && fileName.endsWith(".properties")) {
                            String profile = fileName.substring("application-".length(), fileName.length() - ".properties".length());
                            if (!profile.isEmpty() && !profiles.contains(profile)) {
                                profiles.add(profile);
                            }
                        }
                        // Match application-{profile}.yml or application-{profile}.yaml
                        else if (fileName.startsWith("application-") &&
                                (fileName.endsWith(".yml") || fileName.endsWith(".yaml"))) {
                            String extension = fileName.endsWith(".yml") ? ".yml" : ".yaml";
                            String profile = fileName.substring("application-".length(), fileName.length() - extension.length());
                            if (!profile.isEmpty() && !profiles.contains(profile)) {
                                profiles.add(profile);
                            }
                        }
                    });
        } catch (Exception e) {
            log.debug("Error detecting Spring Boot profiles", e);
        }

        return profiles;
    }

    /**
     * Extract main class from plugin configuration.
     */
    private String extractMainClass(Plugin plugin, Model model) {
        // Try to extract from plugin configuration
        String mainClass = extractConfigValue(plugin, "mainClass");
        if (mainClass != null) {
            return mainClass;
        }

        // Try to extract from start-class (Spring Boot specific)
        mainClass = extractConfigValue(plugin, "start-class");
        if (mainClass != null) {
            return mainClass;
        }

        // Fallback: try to find @SpringBootApplication class
        // This would require scanning source files, which is complex
        // For now, return null
        return null;
    }

    /**
     * Extract main class from maven-shade-plugin configuration.
     */
    private String extractMainClassFromShade(Plugin plugin) {
        if (plugin.getConfiguration() == null) {
            return null;
        }

        try {
            Xpp3Dom config = (Xpp3Dom) plugin.getConfiguration();
            Xpp3Dom transformers = config.getChild("transformers");

            if (transformers != null) {
                for (Xpp3Dom transformer : transformers.getChildren("transformer")) {
                    String impl = transformer.getAttribute("implementation");
                    if (impl != null && impl.contains("ManifestResourceTransformer")) {
                        Xpp3Dom mainClass = transformer.getChild("mainClass");
                        if (mainClass != null) {
                            return mainClass.getValue();
                        }
                    }
                }
            }
        } catch (Exception e) {
            log.debug("Error extracting main class from shade plugin", e);
        }

        return null;
    }

    /**
     * Extract main class from maven-assembly-plugin configuration.
     */
    private String extractMainClassFromAssembly(Plugin plugin) {
        if (plugin.getConfiguration() == null) {
            return null;
        }

        try {
            Xpp3Dom config = (Xpp3Dom) plugin.getConfiguration();
            Xpp3Dom archive = config.getChild("archive");

            if (archive != null) {
                Xpp3Dom manifest = archive.getChild("manifest");
                if (manifest != null) {
                    Xpp3Dom mainClass = manifest.getChild("mainClass");
                    if (mainClass != null) {
                        return mainClass.getValue();
                    }
                }
            }
        } catch (Exception e) {
            log.debug("Error extracting main class from assembly plugin", e);
        }

        return null;
    }

    /**
     * Extract main class from maven-jar-plugin configuration.
     */
    private String extractMainClassFromJar(Plugin plugin) {
        if (plugin.getConfiguration() == null) {
            return null;
        }

        try {
            Xpp3Dom config = (Xpp3Dom) plugin.getConfiguration();
            Xpp3Dom archive = config.getChild("archive");

            if (archive != null) {
                Xpp3Dom manifest = archive.getChild("manifest");
                if (manifest != null) {
                    Xpp3Dom mainClass = manifest.getChild("mainClass");
                    if (mainClass != null) {
                        return mainClass.getValue();
                    }
                }
            }
        } catch (Exception e) {
            log.debug("Error extracting main class from jar plugin", e);
        }

        return null;
    }



    /**
     * Extract transformers from maven-shade-plugin configuration.
     */
    private List<String> extractTransformers(Plugin plugin) {
        List<String> transformers = new ArrayList<>();

        if (plugin.getConfiguration() == null) {
            return transformers;
        }

        try {
            Xpp3Dom config = (Xpp3Dom) plugin.getConfiguration();
            Xpp3Dom transformersNode = config.getChild("transformers");

            if (transformersNode != null) {
                for (Xpp3Dom transformer : transformersNode.getChildren("transformer")) {
                    String impl = transformer.getAttribute("implementation");
                    if (impl != null) {
                        // Extract simple class name
                        String simpleName = impl.substring(impl.lastIndexOf('.') + 1);
                        transformers.add(simpleName);
                    }
                }
            }
        } catch (Exception e) {
            log.debug("Error extracting transformers from shade plugin", e);
        }

        return transformers;
    }

    /**
     * Extract assembly descriptors from maven-assembly-plugin configuration.
     */
    private List<String> extractAssemblyDescriptors(Plugin plugin) {
        List<String> descriptors = new ArrayList<>();

        if (plugin.getConfiguration() == null) {
            return descriptors;
        }

        try {
            Xpp3Dom config = (Xpp3Dom) plugin.getConfiguration();

            // Check for descriptorRefs (predefined descriptors)
            Xpp3Dom descriptorRefs = config.getChild("descriptorRefs");
            if (descriptorRefs != null) {
                for (Xpp3Dom ref : descriptorRefs.getChildren("descriptorRef")) {
                    String value = ref.getValue();
                    if (value != null) {
                        descriptors.add(value);
                    }
                }
            }

            // Check for descriptors (custom descriptor files)
            Xpp3Dom descriptorsNode = config.getChild("descriptors");
            if (descriptorsNode != null) {
                for (Xpp3Dom desc : descriptorsNode.getChildren("descriptor")) {
                    String value = desc.getValue();
                    if (value != null) {
                        descriptors.add(value);
                    }
                }
            }
        } catch (Exception e) {
            log.debug("Error extracting assembly descriptors", e);
        }

        return descriptors;
    }

    /**
     * Extract EAR modules from maven-ear-plugin configuration.
     */
    private List<String> extractEarModules(Plugin plugin) {
        List<String> modules = new ArrayList<>();

        if (plugin.getConfiguration() == null) {
            return modules;
        }

        try {
            Xpp3Dom config = (Xpp3Dom) plugin.getConfiguration();
            Xpp3Dom modulesNode = config.getChild("modules");

            if (modulesNode != null) {
                // Extract web modules
                for (Xpp3Dom webModule : modulesNode.getChildren("webModule")) {
                    Xpp3Dom artifactId = webModule.getChild("artifactId");
                    if (artifactId != null && artifactId.getValue() != null) {
                        modules.add(artifactId.getValue() + ".war");
                    }
                }

                // Extract EJB modules
                for (Xpp3Dom ejbModule : modulesNode.getChildren("ejbModule")) {
                    Xpp3Dom artifactId = ejbModule.getChild("artifactId");
                    if (artifactId != null && artifactId.getValue() != null) {
                        modules.add(artifactId.getValue() + ".jar");
                    }
                }

                // Extract JAR modules
                for (Xpp3Dom jarModule : modulesNode.getChildren("jarModule")) {
                    Xpp3Dom artifactId = jarModule.getChild("artifactId");
                    if (artifactId != null && artifactId.getValue() != null) {
                        modules.add(artifactId.getValue() + ".jar");
                    }
                }
            }
        } catch (Exception e) {
            log.debug("Error extracting EAR modules", e);
        }

        return modules;
    }

    /**
     * Extract Java EE version from maven-ear-plugin configuration.
     */
    private String extractJavaEEVersion(Plugin plugin) {
        if (plugin.getConfiguration() == null) {
            return null;
        }

        try {
            Xpp3Dom config = (Xpp3Dom) plugin.getConfiguration();
            Xpp3Dom version = config.getChild("version");
            if (version != null) {
                return version.getValue();
            }
        } catch (Exception e) {
            log.debug("Error extracting Java EE version", e);
        }

        return null;
    }

    /**
     * Extract a configuration value from plugin.
     */
    private String extractConfigValue(Plugin plugin, String key) {
        if (plugin.getConfiguration() == null) {
            return null;
        }

        try {
            Xpp3Dom config = (Xpp3Dom) plugin.getConfiguration();
            Xpp3Dom node = config.getChild(key);
            if (node != null) {
                return node.getValue();
            }
        } catch (Exception e) {
            log.debug("Error extracting config value: {}", key, e);
        }

        return null;
    }
}

