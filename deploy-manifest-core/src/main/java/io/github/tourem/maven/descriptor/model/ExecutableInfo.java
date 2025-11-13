package io.github.tourem.maven.descriptor.model;

import lombok.Builder;
import lombok.Data;

import java.util.List;

/**
 * Comprehensive information about an executable Maven artifact.
 * This class captures all metadata about how a module can be executed,
 * including the detection method, structure, and runtime requirements.
 * @author tourem

 */
@Data
@Builder
public class ExecutableInfo {

    /**
     * Type of executable artifact (JAR, WAR, EAR)
     */
    private ExecutableType type;

    /**
     * Detection method - name of the plugin or combination that makes this executable
     * Examples: "spring-boot-maven-plugin", "maven-shade-plugin", "maven-jar-plugin + maven-dependency-plugin"
     */
    private String method;

    /**
     * Is this artifact executable standalone (can run with java -jar)?
     */
    private boolean executable;

    /**
     * Structure of the JAR/WAR
     * Examples: "jar-in-jar", "flat-jar", "war-with-embedded-server"
     */
    private String structure;

    /**
     * Main class to execute
     */
    private String mainClass;

    /**
     * Launcher class (for Spring Boot)
     * Examples: "org.springframework.boot.loader.JarLauncher", "org.springframework.boot.loader.WarLauncher"
     */
    private String launcherClass;

    /**
     * Embedded server (for WAR files)
     * Examples: "Tomcat", "Jetty", "Undertow", "Tomcat/Jetty/Undertow"
     */
    private String embeddedServer;

    /**
     * Command to run this artifact
     * Examples: "java -jar target/app.jar", "mvn jetty:run", "mvn tomcat7:run"
     */
    private String runCommand;

    /**
     * Does this artifact require an external server?
     */
    private boolean requiresExternalServer;

    /**
     * Is this for deployment only (not executable standalone)?
     */
    private boolean deploymentOnly;

    /**
     * Modules contained in EAR
     */
    private List<String> modules;

    /**
     * Java EE version (for EAR)
     */
    private String javaEEVersion;

    /**
     * Transformers used (for maven-shade-plugin)
     * Examples: "ManifestResourceTransformer", "AppendingTransformer"
     */
    private List<String> transformers;

    /**
     * Assembly descriptors (for maven-assembly-plugin)
     * Examples: "jar-with-dependencies", "custom-assembly"
     */
    private List<String> descriptors;

    /**
     * Does the main class extend SpringBootServletInitializer? (for Spring Boot WAR)
     */
    private boolean servletInitializer;

    /**
     * Is this plugin/method obsolete?
     */
    private boolean obsolete;

    /**
     * Warning message (e.g., for obsolete plugins)
     */
    private String warning;

    /**
     * Is this a Spring Boot application (detected by dependencies)?
     */
    private boolean springBootApplication;

    /**
     * Spring Boot profiles detected
     */
    private List<String> springBootProfiles;

    /**
     * Create a non-executable info
     */
    public static ExecutableInfo notExecutable() {
        return ExecutableInfo.builder()
                .executable(false)
                .build();
    }

    /**
     * Create info for a deployable but not standalone executable artifact
     */
    public static ExecutableInfo deployableOnly(ExecutableType type, String method) {
        return ExecutableInfo.builder()
                .type(type)
                .method(method)
                .executable(false)
                .deploymentOnly(true)
                .requiresExternalServer(true)
                .build();
    }
}

