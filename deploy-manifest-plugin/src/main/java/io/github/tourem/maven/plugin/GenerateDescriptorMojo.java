package io.github.tourem.maven.plugin;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.fasterxml.jackson.dataformat.yaml.YAMLGenerator;
import io.github.tourem.maven.descriptor.model.ProjectDescriptor;
import io.github.tourem.maven.descriptor.service.MavenProjectAnalyzer;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Component;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;
import org.apache.maven.project.MavenProjectHelper;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.project.ProjectBuilder;
import org.apache.maven.project.ProjectBuildingRequest;
import org.apache.maven.project.DefaultProjectBuildingRequest;
import org.apache.maven.shared.dependency.graph.DependencyGraphBuilder;
import org.apache.maven.shared.dependency.graph.DependencyGraphBuilderException;
import org.apache.maven.artifact.Artifact;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;
import java.util.zip.GZIPOutputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream;
import org.apache.commons.compress.compressors.gzip.GzipCompressorOutputStream;
import org.apache.commons.compress.compressors.bzip2.BZip2CompressorOutputStream;
import org.apache.hc.client5.http.classic.methods.HttpPost;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.CloseableHttpResponse;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.io.entity.StringEntity;

/**
 * Maven plugin goal that generates a deployment descriptor for the project.
 *
 * This plugin analyzes the Maven project structure and generates a comprehensive
 * JSON descriptor containing deployment information including:
 * - Deployable modules (JAR, WAR, EAR)
 * - Spring Boot executables
 * - Environment-specific configurations
 * - Actuator endpoints
 * - Assembly artifacts
 * @author tourem

 */
@Mojo(name = "generate", defaultPhase = LifecyclePhase.PACKAGE, threadSafe = true)
public class GenerateDescriptorMojo extends AbstractMojo {

    /**
     * The Maven project being analyzed.
     */
    @Parameter(defaultValue = "${project}", readonly = true, required = true)
    private MavenProject project;

    /** Maven runtime session */
    @Parameter(defaultValue = "${session}", readonly = true)
    private MavenSession session;

    /** Project builder (fallback when module not in session) */
    @Component
    private ProjectBuilder projectBuilder;

    /** Dependency graph builder for resolving transitive dependencies */
    @Component
    private DependencyGraphBuilder dependencyGraphBuilder;

    /**
     * Maven project helper for attaching artifacts.
     */
    @Component
    private MavenProjectHelper projectHelper;

    /**
     * Output file name for the generated descriptor.
     * Default: descriptor.json
     */
    @Parameter(property = "descriptor.outputFile", defaultValue = "descriptor.json")
    private String outputFile;

    /**
     * Output directory for the generated descriptor.
     * If not specified, defaults to ${project.build.directory} (target/).
     * Can be an absolute path or relative to the project root.
     */
    @Parameter(property = "descriptor.outputDirectory", defaultValue = "${project.build.directory}")
    private String outputDirectory;

    /**
     * Skip the plugin execution.
     * Default: false
     */
    @Parameter(property = "descriptor.skip", defaultValue = "false")
    private boolean skip;

    /**
     * Pretty print the JSON output.
     * Default: true
     */
    @Parameter(property = "descriptor.prettyPrint", defaultValue = "true")
    private boolean prettyPrint;

    /**
     * Archive format for the descriptor file.
     * Supported formats: zip, tar.gz, tar.bz2, jar
     * If not specified, only the JSON file is generated without archiving.
     *
     * Examples:
     * - "zip" : Creates a ZIP archive containing the JSON file
     * - "tar.gz" : Creates a gzipped TAR archive
     * - "tar.bz2" : Creates a bzip2 compressed TAR archive
     * - "jar" : Creates a JAR archive (same as ZIP)
     */
    @Parameter(property = "descriptor.format")
    private String format;

    /**
     * Classifier to use for the attached artifact.
     * Default: "descriptor"
     *
     * The classifier is appended to the artifact name:
     * - artifactId-version-classifier.format
     *
     * Example: myapp-1.0.0-descriptor.zip
     */
    @Parameter(property = "descriptor.classifier", defaultValue = "descriptor")
    private String classifier;

    /**
     * Whether to attach the generated descriptor (or archive) to the project.
     * When true, the artifact will be installed and deployed along with the main artifact.
     * Default: false
     *
     * Set to true to deploy the descriptor to Maven repository (Nexus, JFrog, etc.)
     */
    @Parameter(property = "descriptor.attach", defaultValue = "false")
    private boolean attach;

    /**
     * Export format for the descriptor.
     * Supported formats: json, yaml, both
     * Default: json
     *
     * - "json" : Export only JSON format
     * - "yaml" : Export only YAML format
     * - "both" : Export both JSON and YAML formats
     */
    @Parameter(property = "descriptor.exportFormat", defaultValue = "json")
    private String exportFormat;

    /**
     * Enable JSON Schema validation of the generated descriptor.
     * Default: false
     *
     * When enabled, validates the descriptor against a JSON Schema before writing.
     */
    @Parameter(property = "descriptor.validate", defaultValue = "false")
    private boolean validate;

    /**
     * Generate digital signature (SHA-256 hash) for the descriptor.
     * Default: false
     *
     * When enabled, creates a .sha256 file containing the hash of the descriptor.
     */
    @Parameter(property = "descriptor.sign", defaultValue = "false")
    private boolean sign;

    /**
     * Compress the JSON output using GZIP.
     * Default: false
     *
     * When enabled, creates a .json.gz file in addition to the regular JSON file.
     * Note: This is different from the 'format' parameter which creates archives.
     */
    @Parameter(property = "descriptor.compress", defaultValue = "false")
    private boolean compress;

    /**
     * Webhook URL to notify after successful descriptor generation.
     * Optional parameter.
     *
     * When specified, sends an HTTP POST request to this URL with the descriptor content.
     * Example: http://localhost:8080/api/descriptors/notify
     */
    @Parameter(property = "descriptor.webhookUrl")
    private String webhookUrl;

    /**
     * Webhook authentication token (optional).
     * Sent as "Authorization: Bearer {token}" header.
     */
    @Parameter(property = "descriptor.webhookToken")
    private String webhookToken;

    /**
     * Webhook timeout in seconds.
     * Default: 10 seconds
     */
    @Parameter(property = "descriptor.webhookTimeout", defaultValue = "10")
    private int webhookTimeout;

    /**
     * Dry-run mode: print summary to console without generating files.
     * Default: false
     *
     * When enabled, analyzes the project and displays a dashboard in the console
     * but does not write any files to disk.
     */
    @Parameter(property = "descriptor.summary", defaultValue = "false")
    private boolean summary;

    /**
     * Generate HTML documentation from the descriptor.
     * Default: false
     *
     * When enabled, creates an HTML page with a human-readable view of the descriptor.
     * Useful for non-technical teams to review deployment information.
     */
    @Parameter(property = "descriptor.generateHtml", defaultValue = "false")
    private boolean generateHtml;

    /**
     * Local post-generation hook: script or command to execute after generation.
     * Optional parameter.
     *
     * This is different from webhookUrl (which sends HTTP requests).
     * This executes a local script/command on the build machine.
     *
     * Examples:
     * - "/path/to/script.sh"
     * - "python /path/to/process.py"
     * - "echo 'Descriptor generated'"
     */
    @Parameter(property = "descriptor.postGenerationHook")
    private String postGenerationHook;

    // ================================
    // Dependency Tree Feature Options
    // ================================

    /** Enable dependency tree collection (disabled by default). */
    @Parameter(property = "descriptor.includeDependencyTree", defaultValue = "false")
    private boolean includeDependencyTree;

    /** Depth: -1=unlimited, 0=direct only. */
    @Parameter(property = "descriptor.dependencyTreeDepth", defaultValue = "-1")
    private int dependencyTreeDepth;

    /** Scopes to include, comma-separated. Default: compile,runtime */
    @Parameter(property = "descriptor.dependencyScopes", defaultValue = "compile,runtime")
    private String dependencyScopes;

    /** Output format: flat, tree, both. */
    @Parameter(property = "descriptor.dependencyTreeFormat", defaultValue = "flat")
    private String dependencyTreeFormat;

    /** Exclude transitive dependencies entirely. */
    @Parameter(property = "descriptor.excludeTransitive", defaultValue = "false")
    private boolean excludeTransitive;

    /** Include optional dependencies (default: false). */
    @Parameter(property = "descriptor.includeOptional", defaultValue = "false")
    private boolean includeOptional;

    // =============================
    // Licenses Feature Options
    // =============================

    /** Enable licenses collection (disabled by default). */
    @Parameter(property = "descriptor.includeLicenses", defaultValue = "false")
    private boolean includeLicenses;

    /** Generate warnings for incompatible/unknown licenses. */
    @Parameter(property = "descriptor.licenseWarnings", defaultValue = "false")
    private boolean licenseWarnings;

    /** Comma-separated list of incompatible licenses (e.g., GPL-3.0,AGPL-3.0,SSPL). */
    @Parameter(property = "descriptor.incompatibleLicenses", defaultValue = "GPL-3.0,AGPL-3.0,SSPL")
    private String incompatibleLicenses;

    /** Include transitive licenses (future use). */
    @Parameter(property = "descriptor.includeTransitiveLicenses", defaultValue = "true")
    private boolean includeTransitiveLicenses;

    // =============================
    // Properties Feature Options
    // =============================

    /** Enable properties collection (disabled by default). */
    @Parameter(property = "descriptor.includeProperties", defaultValue = "false")
    private boolean includeProperties;

    /** Include Java/system properties. */
    @Parameter(property = "descriptor.includeSystemProperties", defaultValue = "true")
    private boolean includeSystemProperties;

    /** Include environment variables (use with care). */
    @Parameter(property = "descriptor.includeEnvironmentVariables", defaultValue = "false")
    private boolean includeEnvironmentVariables;

    /** Filter sensitive properties by key name (password, secret, token, ...). */
    @Parameter(property = "descriptor.filterSensitiveProperties", defaultValue = "true")
    private boolean filterSensitiveProperties;

    /** Mask sensitive values instead of dropping them. */
    @Parameter(property = "descriptor.maskSensitiveValues", defaultValue = "true")
    private boolean maskSensitiveValues;

    /** Comma-separated list of extra sensitive key patterns (case-insensitive). */
    @Parameter(property = "descriptor.propertyExclusions", defaultValue = "password,secret,token,apikey,api-key,api_key,credentials,auth,key")
    private String propertyExclusions;

    // =============================
    // Plugins Feature Options
    // =============================

    /** Enable plugins collection (disabled by default). */
    @Parameter(property = "descriptor.includePlugins", defaultValue = "false")
    private boolean includePlugins;

    /** Include plugin configuration blocks (sanitized). */
    @Parameter(property = "descriptor.includePluginConfiguration", defaultValue = "true")
    private boolean includePluginConfiguration;

    /** Include pluginManagement definitions. */
    @Parameter(property = "descriptor.includePluginManagement", defaultValue = "true")
    private boolean includePluginManagement;

    /** Check newer plugin versions (network). */
    @Parameter(property = "descriptor.checkPluginUpdates", defaultValue = "false")
    private boolean checkPluginUpdates;

    /** Mask sensitive config values for plugins. */
    @Parameter(property = "descriptor.filterSensitivePluginConfig", defaultValue = "true")
    private boolean filterSensitivePluginConfig;

    /** Timeout in ms for update checks. */
    @Parameter(property = "descriptor.pluginUpdateTimeoutMillis", defaultValue = "2000")
    private int pluginUpdateTimeoutMillis;



    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {
        if (skip) {
            getLog().info("Descriptor plugin execution skipped");
            return;
        }

        try {
            getLog().info("Analyzing Maven project: " + project.getName());

            // Get the project base directory (fallback to current dir for safety in test environments)
            File projectDir = (project != null && project.getBasedir() != null)
                    ? project.getBasedir()
                    : new File(".").getAbsoluteFile();
            getLog().debug("Project directory: " + projectDir.getAbsolutePath());

            // Analyze the project
            var dtOptionsBuilder = io.github.tourem.maven.descriptor.model.DependencyTreeOptions.builder()
                .include(includeDependencyTree)
                .depth(dependencyTreeDepth)
                .format(io.github.tourem.maven.descriptor.model.DependencyTreeFormat.fromString(dependencyTreeFormat))
                .excludeTransitive(excludeTransitive)
                .includeOptional(includeOptional);

            // Parse scopes
            java.util.Set<String> scopeSet = new java.util.HashSet<>();
            if (dependencyScopes != null && !dependencyScopes.trim().isEmpty()) {
                for (String s : dependencyScopes.split(",")) {
                    if (s != null && !s.trim().isEmpty()) scopeSet.add(s.trim().toLowerCase());
                }
            }
            if (scopeSet.isEmpty()) {
                scopeSet.add("compile");
                scopeSet.add("runtime");
            }
            dtOptionsBuilder.scopes(scopeSet);

            // Licenses options
            java.util.Set<String> licIncompatSet = new java.util.HashSet<>();
            if (incompatibleLicenses != null && !incompatibleLicenses.isBlank()) {
                for (String s : incompatibleLicenses.split(",")) {
                    if (s != null && !s.isBlank()) licIncompatSet.add(s.trim());
                }
            }
            var licOpts = io.github.tourem.maven.descriptor.model.LicenseOptions.builder()
                .include(includeLicenses)
                .licenseWarnings(licenseWarnings)
                .includeTransitiveLicenses(includeTransitiveLicenses)
                .incompatibleLicenses(licIncompatSet)
                .build();

            // Build property options for core analyzer
            java.util.Set<String> exclusions = new java.util.HashSet<>();
            if (propertyExclusions != null && !propertyExclusions.isBlank()) {
                for (String t : propertyExclusions.split(",")) {
                    if (t != null && !t.isBlank()) exclusions.add(t.trim());
                }
            }
            io.github.tourem.maven.descriptor.model.PropertyOptions propOpts = io.github.tourem.maven.descriptor.model.PropertyOptions.builder()
                    .include(includeProperties)
                    .includeSystemProperties(includeSystemProperties)
                    .includeEnvironmentVariables(includeEnvironmentVariables)
                    .filterSensitiveProperties(filterSensitiveProperties)
                    .maskSensitiveValues(maskSensitiveValues)
                    .propertyExclusions(exclusions)
                    .build();

            // Plugins options
            io.github.tourem.maven.descriptor.model.PluginOptions pluginOpts = io.github.tourem.maven.descriptor.model.PluginOptions.builder()
                    .include(includePlugins)
                    .includePluginConfiguration(includePluginConfiguration)
                    .includePluginManagement(includePluginManagement)
                    .checkPluginUpdates(checkPluginUpdates)
                    .filterSensitivePluginConfig(filterSensitivePluginConfig)
                    .updateCheckTimeoutMillis(pluginUpdateTimeoutMillis)
                    .build();
            // Ensure core uses the exact local repository Maven is using
            try {
                if (session != null && session.getLocalRepository() != null
                        && session.getLocalRepository().getBasedir() != null
                        && !session.getLocalRepository().getBasedir().isBlank()) {
                    System.setProperty("maven.repo.local", session.getLocalRepository().getBasedir());
                }
            } catch (Throwable t) {
                getLog().debug("Unable to set maven.repo.local from session: " + t.getMessage());
            }


            // Pre-resolve dependencies to ensure POMs are present for license collection
            if (includeLicenses) {
                try {
                    preResolveDependenciesForLicensesInSession();
                } catch (Exception e) {
                    getLog().debug("Pre-resolving dependencies for license collection failed: " + e.getMessage(), e);
                }
            }


            MavenProjectAnalyzer analyzer = new MavenProjectAnalyzer(dtOptionsBuilder.build(), licOpts, propOpts, pluginOpts);
            ProjectDescriptor descriptor = analyzer.analyzeProject(projectDir.toPath());

            // Optionally enrich BuildInfo with properties, profiles, goals and Maven runtime
            if (includeProperties) {
                descriptor = enrichBuildInfoWithProperties(descriptor);
            }

            // Validate descriptor if requested
            if (validate) {
                validateDescriptor(descriptor);
            }

            // If summary mode, print dashboard and exit
            if (summary) {
                printSummaryDashboard(descriptor);
                return;
            }

            // Determine output path
            Path outputPath = resolveOutputPath();
            getLog().info("Generating descriptor: " + outputPath.toAbsolutePath());

            // Create output directory if needed
            Files.createDirectories(outputPath.getParent());

            // Configure ObjectMapper
            ObjectMapper jsonMapper = new ObjectMapper();
            // Enrich dependencies with resolved transitive tree for HTML if enabled
            var dtOptions = dtOptionsBuilder.build();
            if (dtOptions.isInclude() && !excludeTransitive) {
                try {
                    enrichDependencyTrees(descriptor, dtOptions);
                } catch (Exception e) {
                    getLog().warn("Failed to resolve transitive dependencies for tree view: " + e.getMessage());
                    getLog().debug("Tree resolution error details", e);
                }
            }

            jsonMapper.findAndRegisterModules();
            jsonMapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
            if (prettyPrint) {
                jsonMapper.enable(SerializationFeature.INDENT_OUTPUT);
            }

            // Export based on format
            String normalizedExportFormat = exportFormat.trim().toLowerCase();
            Path jsonOutputPath = null;
            Path yamlOutputPath = null;

            switch (normalizedExportFormat) {
                case "json":
                    jsonOutputPath = outputPath;
                    jsonMapper.writeValue(jsonOutputPath.toFile(), descriptor);
                    getLog().info("✓ Descriptor JSON generated successfully");
                    break;

                case "yaml":
                    yamlOutputPath = changeExtension(outputPath, ".yaml");
                    writeYaml(descriptor, yamlOutputPath);
                    getLog().info("✓ Descriptor YAML generated successfully");
                    break;

                case "both":
                    jsonOutputPath = outputPath;
                    yamlOutputPath = changeExtension(outputPath, ".yaml");
                    jsonMapper.writeValue(jsonOutputPath.toFile(), descriptor);
                    writeYaml(descriptor, yamlOutputPath);
                    getLog().info("✓ Descriptor JSON and YAML generated successfully");
                    break;

                default:
                    throw new MojoExecutionException("Unsupported export format: " + exportFormat +
                        ". Supported formats: json, yaml, both");
            }

            getLog().info("  - Total modules: " + descriptor.totalModules());
            getLog().info("  - Deployable modules: " + descriptor.deployableModulesCount());

            // Use JSON path as primary output for subsequent operations
            Path primaryOutput = jsonOutputPath != null ? jsonOutputPath : yamlOutputPath;
            getLog().info("  - Output: " + primaryOutput.toAbsolutePath());

            // Generate digital signature if requested (for primary output)
            if (sign && primaryOutput != null) {
                generateSignature(primaryOutput);
            }

            // Build list of files to archive (all generated artifacts)
            java.util.List<java.nio.file.Path> filesToArchive = new java.util.ArrayList<>();
            if (jsonOutputPath != null) {
                filesToArchive.add(jsonOutputPath);
            }
            if (yamlOutputPath != null) {
                filesToArchive.add(yamlOutputPath);
            }

            // Compress JSON if requested and include .gz in archive
            if (compress && jsonOutputPath != null) {
                compressFile(jsonOutputPath);
                filesToArchive.add(java.nio.file.Paths.get(jsonOutputPath.toString() + ".gz"));
            }

            // Generate HTML documentation before archiving so it can be included
            if (generateHtml) {
                generateHtmlDocumentation(descriptor, outputPath);
                java.nio.file.Path htmlPath = outputPath.getParent().resolve(
                    outputPath.getFileName().toString().replace(".json", ".html")
                );
                filesToArchive.add(htmlPath);
            }

            // Handle archiving and attachment if format is specified
            File finalArtifact = primaryOutput.toFile();

            if (format != null && !format.trim().isEmpty()) {
                finalArtifact = createArchive(filesToArchive);
                getLog().info("✓ Archive created: " + finalArtifact.getAbsolutePath());
            }

            // Attach artifact to project if requested
            if (attach) {
                attachArtifact(finalArtifact);
            }

            // Send webhook notification if configured
            if (webhookUrl != null && !webhookUrl.trim().isEmpty()) {
                sendWebhookNotification(descriptor, primaryOutput);
            }

            // Execute post-generation hook if configured
            if (postGenerationHook != null && !postGenerationHook.trim().isEmpty()) {
                executePostGenerationHook(primaryOutput);
            }

        } catch (IOException e) {
            throw new MojoExecutionException("Failed to generate descriptor", e);
        } catch (Exception e) {
            throw new MojoExecutionException("Error analyzing project", e);
        }
    }

    /**
     * Resolves the output path based on configuration.
     *
     * @return the resolved output path
     */
    private Path resolveOutputPath() {
        Path basePath;

        if (outputDirectory != null && !outputDirectory.trim().isEmpty()) {
            // Use configured output directory
            Path configuredPath = Paths.get(outputDirectory);

            if (configuredPath.isAbsolute()) {
                basePath = configuredPath;
            } else {
                // Relative to project root
                basePath = project.getBasedir().toPath().resolve(configuredPath);
            }
        } else {
            // Default: target directory
            basePath = Paths.get(project.getBuild().getDirectory());
        }

        return basePath.resolve(outputFile);
    }

    /**
     * Creates an archive containing all generated descriptor files (JSON, YAML, HTML, etc.).
     *
     * @param files list of files to include in the archive
     * @return the created archive file
     * @throws IOException if archive creation fails
     */
    private File createArchive(java.util.List<java.nio.file.Path> files) throws IOException {
        String normalizedFormat = format.trim().toLowerCase();

        if (files == null || files.isEmpty()) {
            throw new IOException("No files to archive");
        }

        // Determine archive file name
        String archiveBaseName = project.getArtifactId() + "-" + project.getVersion();
        if (classifier != null && !classifier.trim().isEmpty()) {
            archiveBaseName += "-" + classifier;
        }

        File archiveFile;
        java.nio.file.Path parent = files.get(0).getParent();

        switch (normalizedFormat) {
            case "zip":
            case "jar":
                archiveFile = new File(parent.toFile(), archiveBaseName + ".zip");
                createZipArchive(files, archiveFile);
                break;

            case "tar.gz":
            case "tgz":
                archiveFile = new File(parent.toFile(), archiveBaseName + ".tar.gz");
                createTarGzArchive(files, archiveFile);
                break;

            case "tar.bz2":
            case "tbz2":
                archiveFile = new File(parent.toFile(), archiveBaseName + ".tar.bz2");
                createTarBz2Archive(files, archiveFile);
                break;

            default:
                throw new IOException("Unsupported archive format: " + format +
                    ". Supported formats: zip, jar, tar.gz, tgz, tar.bz2, tbz2");
        }

        getLog().info("  - Archive format: " + normalizedFormat);
        getLog().info("  - Archive size: " + formatFileSize(archiveFile.length()));

        return archiveFile;
    }

    /**
     * Creates a ZIP archive containing the provided files.
     */
    private void createZipArchive(java.util.List<java.nio.file.Path> files, File archiveFile) throws IOException {
        try (FileOutputStream fos = new FileOutputStream(archiveFile);
             ZipOutputStream zos = new ZipOutputStream(fos)) {

            for (java.nio.file.Path file : files) {
                if (file == null) continue;
                ZipEntry entry = new ZipEntry(file.getFileName().toString());
                zos.putNextEntry(entry);
                Files.copy(file, zos);
                zos.closeEntry();
            }
        }
    }

    /**
     * Creates a TAR.GZ archive containing the provided files.
     */
    private void createTarGzArchive(java.util.List<java.nio.file.Path> files, File archiveFile) throws IOException {
        try (FileOutputStream fos = new FileOutputStream(archiveFile);
             GzipCompressorOutputStream gzos = new GzipCompressorOutputStream(fos);
             TarArchiveOutputStream taos = new TarArchiveOutputStream(gzos)) {

            for (java.nio.file.Path file : files) {
                if (file == null) continue;
                TarArchiveEntry entry = new TarArchiveEntry(file.toFile(), file.getFileName().toString());
                taos.putArchiveEntry(entry);
                Files.copy(file, taos);
                taos.closeArchiveEntry();
            }
            taos.finish();
        }
    }

    /**
     * Creates a TAR.BZ2 archive containing the provided files.
     */
    private void createTarBz2Archive(java.util.List<java.nio.file.Path> files, File archiveFile) throws IOException {
        try (FileOutputStream fos = new FileOutputStream(archiveFile);
             BZip2CompressorOutputStream bz2os = new BZip2CompressorOutputStream(fos);
             TarArchiveOutputStream taos = new TarArchiveOutputStream(bz2os)) {

            for (java.nio.file.Path file : files) {
                if (file == null) continue;
                TarArchiveEntry entry = new TarArchiveEntry(file.toFile(), file.getFileName().toString());
                taos.putArchiveEntry(entry);
                Files.copy(file, taos);
                taos.closeArchiveEntry();
            }
            taos.finish();
        }
    }

    /**
     * Attaches the artifact to the Maven project for installation and deployment.
     */
    private void attachArtifact(File artifact) {
        String type = determineArtifactType(artifact);

        getLog().info("✓ Attaching artifact to project");
        getLog().info("  - Type: " + type);
        getLog().info("  - Classifier: " + (classifier != null ? classifier : "none"));
        getLog().info("  - File: " + artifact.getName());

        projectHelper.attachArtifact(project, type, classifier, artifact);

        getLog().info("  → Artifact will be deployed to Maven repository during 'mvn deploy'");
    }

    /**
     * Determines the artifact type based on file extension.
     */
    private String determineArtifactType(File file) {
        String name = file.getName().toLowerCase();

        if (name.endsWith(".zip")) {
            return "zip";
        } else if (name.endsWith(".tar.gz") || name.endsWith(".tgz")) {
            return "tar.gz";
        } else if (name.endsWith(".tar.bz2") || name.endsWith(".tbz2")) {
            return "tar.bz2";
        } else if (name.endsWith(".json")) {
            return "json";
        } else {
            return "file";
        }
    }

    /**
     * Formats file size in human-readable format.
     */
    private String formatFileSize(long bytes) {
        if (bytes < 1024) {
            return bytes + " B";
        } else if (bytes < 1024 * 1024) {
            return String.format("%.2f KB", bytes / 1024.0);
        } else {
            return String.format("%.2f MB", bytes / (1024.0 * 1024.0));
        }
    }

    /**
     * Validates the descriptor against a JSON Schema.
     */
    private void validateDescriptor(ProjectDescriptor descriptor) throws MojoExecutionException {
        getLog().info("✓ Validating descriptor structure");
        // Basic validation - check required fields
        if (descriptor.projectName() == null || descriptor.projectName().isEmpty()) {
            throw new MojoExecutionException("Descriptor validation failed: projectName is required");
        }
        if (descriptor.projectVersion() == null || descriptor.projectVersion().isEmpty()) {
            throw new MojoExecutionException("Descriptor validation failed: projectVersion is required");
        }
        getLog().info("  - Validation passed");
    }

    /**
     * Writes descriptor in YAML format.
     */
    private void writeYaml(ProjectDescriptor descriptor, Path yamlPath) throws IOException {
        YAMLFactory yamlFactory = YAMLFactory.builder()
            .disable(YAMLGenerator.Feature.WRITE_DOC_START_MARKER)
            .build();

        ObjectMapper yamlMapper = new ObjectMapper(yamlFactory);
        yamlMapper.findAndRegisterModules();
        yamlMapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        if (prettyPrint) {
            yamlMapper.enable(SerializationFeature.INDENT_OUTPUT);
        }

        yamlMapper.writeValue(yamlPath.toFile(), descriptor);
    }

    /**
     * Changes file extension.
     */
    private Path changeExtension(Path path, String newExtension) {
        String fileName = path.getFileName().toString();
        int dotIndex = fileName.lastIndexOf('.');
        String baseName = dotIndex > 0 ? fileName.substring(0, dotIndex) : fileName;
        return path.getParent().resolve(baseName + newExtension);
    }

    /**
     * Generates SHA-256 digital signature for the file.
     */
    private void generateSignature(Path filePath) throws IOException, NoSuchAlgorithmException {
        getLog().info("✓ Generating digital signature (SHA-256)");

        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        byte[] fileBytes = Files.readAllBytes(filePath);
        byte[] hashBytes = digest.digest(fileBytes);

        // Convert to hex string
        StringBuilder hexString = new StringBuilder();
        for (byte b : hashBytes) {
            String hex = Integer.toHexString(0xff & b);
            if (hex.length() == 1) {
                hexString.append('0');
            }
            hexString.append(hex);
        }

        String hash = hexString.toString();
        Path signaturePath = Paths.get(filePath.toString() + ".sha256");
        Files.writeString(signaturePath, hash + "  " + filePath.getFileName().toString() + "\n");

        getLog().info("  - Signature: " + hash);
        getLog().info("  - Signature file: " + signaturePath.getFileName());
    }

    /**
     * Compresses the file using GZIP.
     */
    private void compressFile(Path filePath) throws IOException {
        getLog().info("✓ Compressing descriptor with GZIP");

        Path compressedPath = Paths.get(filePath.toString() + ".gz");

        try (FileInputStream fis = new FileInputStream(filePath.toFile());
             FileOutputStream fos = new FileOutputStream(compressedPath.toFile());
             GZIPOutputStream gzos = new GZIPOutputStream(fos)) {

            byte[] buffer = new byte[8192];
            int len;
            while ((len = fis.read(buffer)) > 0) {
                gzos.write(buffer, 0, len);
            }
        }

        long originalSize = Files.size(filePath);
        long compressedSize = Files.size(compressedPath);
        double ratio = 100.0 * (1.0 - ((double) compressedSize / originalSize));

        getLog().info("  - Original size: " + formatFileSize(originalSize));
        getLog().info("  - Compressed size: " + formatFileSize(compressedSize));
        getLog().info("  - Compression ratio: " + String.format("%.1f%%", ratio));
        getLog().info("  - Compressed file: " + compressedPath.getFileName());
    }

    /**
     * Sends webhook notification with descriptor content.
     */
    private void sendWebhookNotification(ProjectDescriptor descriptor, Path filePath) {
        getLog().info("✓ Sending webhook notification");
        getLog().info("  - URL: " + webhookUrl);

        try (CloseableHttpClient httpClient = HttpClients.createDefault()) {
            HttpPost httpPost = new HttpPost(webhookUrl);

            // Set headers
            httpPost.setHeader("Content-Type", "application/json");
            httpPost.setHeader("User-Agent", "Descriptor-Maven-Plugin/1.0");

            if (webhookToken != null && !webhookToken.trim().isEmpty()) {
                httpPost.setHeader("Authorization", "Bearer " + webhookToken);
            }

            // Create payload
            ObjectMapper mapper = new ObjectMapper();
            mapper.findAndRegisterModules();
            mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

            String jsonPayload = mapper.writeValueAsString(descriptor);
            httpPost.setEntity(new StringEntity(jsonPayload, ContentType.APPLICATION_JSON));

            // Execute request
            try (CloseableHttpResponse response = httpClient.execute(httpPost)) {
                int statusCode = response.getCode();

                if (statusCode >= 200 && statusCode < 300) {
                    getLog().info("  - Response: " + statusCode + " (Success)");
                } else {
                    getLog().warn("  - Response: " + statusCode + " (Warning: non-2xx status)");
                }
            }

        } catch (Exception e) {
            getLog().warn("Failed to send webhook notification: " + e.getMessage());
            getLog().debug("Webhook error details", e);
        }
    }

    /**
     * Print a summary dashboard to the console.
     */
    private void printSummaryDashboard(ProjectDescriptor descriptor) {
        getLog().info("");
        getLog().info("╔═══════════════════════════════════════════════════════════════════════╗");
        getLog().info("║                    DESCRIPTOR SUMMARY (DRY-RUN)                       ║");
        getLog().info("╚═══════════════════════════════════════════════════════════════════════╝");
        getLog().info("");
        getLog().info("  Project: " + descriptor.projectName());
        getLog().info("  Group ID: " + descriptor.projectGroupId());
        getLog().info("  Artifact ID: " + descriptor.projectArtifactId());
        getLog().info("  Version: " + descriptor.projectVersion());
        getLog().info("  Generated At: " + descriptor.generatedAt());
        getLog().info("");
        getLog().info("┌───────────────────────────────────────────────────────────────────────┐");
        getLog().info("│ MODULES SUMMARY                                                       │");
        getLog().info("├───────────────────────────────────────────────────────────────────────┤");
        getLog().info("│ Total Modules:      " + String.format("%-50d", descriptor.totalModules()) + "│");
        getLog().info("│ Deployable Modules: " + String.format("%-50d", descriptor.deployableModulesCount()) + "│");
        getLog().info("└───────────────────────────────────────────────────────────────────────┘");
        getLog().info("");

        if (descriptor.deployableModules() != null && !descriptor.deployableModules().isEmpty()) {
            getLog().info("┌───────────────────────────────────────────────────────────────────────┐");
            getLog().info("│ DEPLOYABLE MODULES                                                    │");
            getLog().info("├───────────────────────────────────────────────────────────────────────┤");

            descriptor.deployableModules().forEach(module -> {
                getLog().info("│                                                                       │");
                getLog().info("│ • " + module.getArtifactId() + " (" + module.getPackaging() + ")");
                getLog().info("│   Path: " + module.getRepositoryPath());
                if (module.isSpringBootExecutable()) {
                    getLog().info("│   Type: Spring Boot Executable");
                    if (module.getMainClass() != null) {
                        getLog().info("│   Main Class: " + module.getMainClass());
                    }
                }
                if (module.getEnvironments() != null && !module.getEnvironments().isEmpty()) {
                    getLog().info("│   Environments: " + module.getEnvironments().size());
                }
            });

            getLog().info("│                                                                       │");
            getLog().info("└───────────────────────────────────────────────────────────────────────┘");
        }

        if (descriptor.buildInfo() != null) {
            getLog().info("");
            getLog().info("┌───────────────────────────────────────────────────────────────────────┐");
            getLog().info("│ BUILD INFO                                                            │");
            getLog().info("├───────────────────────────────────────────────────────────────────────┤");
            if (descriptor.buildInfo().gitCommitSha() != null) {
                getLog().info("│ Git Commit: " + descriptor.buildInfo().gitCommitShortSha());
            }
            if (descriptor.buildInfo().gitBranch() != null) {
                getLog().info("│ Git Branch: " + descriptor.buildInfo().gitBranch());
            }
            if (descriptor.buildInfo().ciProvider() != null) {
                getLog().info("│ CI Provider: " + descriptor.buildInfo().ciProvider());
            }
            getLog().info("└───────────────────────────────────────────────────────────────────────┘");
        }

        getLog().info("");
        getLog().info("✓ Dry-run complete. No files were generated.");
        getLog().info("  To generate files, run without -Ddescriptor.summary=true");
        getLog().info("");
    }

    /**
     * Generate HTML documentation from the descriptor.
     */
    private void generateHtmlDocumentation(ProjectDescriptor descriptor, Path jsonOutputPath) throws IOException {
        Path htmlPath = jsonOutputPath.getParent().resolve(
            jsonOutputPath.getFileName().toString().replace(".json", ".html")
        );

        StringBuilder html = new StringBuilder();
        html.append("<!DOCTYPE html>\n");
        html.append("<html lang=\"en\">\n");
        html.append("<head>\n");
        html.append("  <meta charset=\"UTF-8\">\n");
        html.append("  <meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\">\n");
        html.append("  <title>").append(escapeHtml(descriptor.projectName())).append(" - Deployment Descriptor</title>\n");
        html.append("  <style>\n");

        // Modern CSS with gradients, animations, and tabs
        html.append("    * { margin: 0; padding: 0; box-sizing: border-box; }\n");
        html.append("    body { font-family: 'Segoe UI', Tahoma, Geneva, Verdana, sans-serif; background: linear-gradient(135deg, #667eea 0%, #764ba2 100%); min-height: 100vh; padding: 20px; }\n");
        html.append("    .container { max-width: 1400px; margin: 0 auto; background: white; border-radius: 20px; box-shadow: 0 20px 60px rgba(0,0,0,0.3); overflow: hidden; }\n");

        // Header
        html.append("    .header { background: linear-gradient(135deg, #667eea 0%, #764ba2 100%); color: white; padding: 40px; display: flex; justify-content: space-between; align-items: center; position: relative; overflow: hidden; }\n");
        html.append("    .header::before { content: ''; position: absolute; top: -50%; right: -50%; width: 200%; height: 200%; background: radial-gradient(circle, rgba(255,255,255,0.1) 0%, transparent 70%); animation: pulse 15s ease-in-out infinite; }\n");
        html.append("    @keyframes pulse { 0%, 100% { transform: scale(1); } 50% { transform: scale(1.1); } }\n");
        html.append("    .header h1 { font-size: 2.5em; margin-bottom: 10px; position: relative; z-index: 1; text-shadow: 2px 2px 4px rgba(0,0,0,0.2); }\n");
        html.append("    .header .subtitle { font-size: 1.1em; opacity: 0.9; position: relative; z-index: 1; }\n");
        html.append("    .header .timestamp { margin-top: 15px; font-size: 0.9em; opacity: 0.8; position: relative; z-index: 1; }\n");
        html.append("    .theme-toggle { background: rgba(255,255,255,0.2); border: 2px solid rgba(255,255,255,0.3); color: white; padding: 12px 16px; border-radius: 50%; cursor: pointer; font-size: 1.5em; transition: all 0.3s; position: relative; z-index: 1; }\n");
        html.append("    .theme-toggle:hover { background: rgba(255,255,255,0.3); transform: rotate(20deg) scale(1.1); }\n");

        // Stats Cards
        html.append("    .stats { display: grid; grid-template-columns: repeat(auto-fit, minmax(200px, 1fr)); gap: 20px; padding: 30px; background: #f8f9fa; }\n");
        html.append("    .stat-card { background: white; padding: 25px; border-radius: 15px; text-align: center; box-shadow: 0 4px 15px rgba(0,0,0,0.1); transition: transform 0.3s, box-shadow 0.3s; }\n");
        html.append("    .stat-card:hover { transform: translateY(-5px); box-shadow: 0 8px 25px rgba(0,0,0,0.15); }\n");
        html.append("    .stat-card .number { font-size: 2.5em; font-weight: bold; background: linear-gradient(135deg, #667eea 0%, #764ba2 100%); -webkit-background-clip: text; -webkit-text-fill-color: transparent; background-clip: text; }\n");
        html.append("    .stat-card .label { color: #666; margin-top: 10px; font-size: 0.9em; text-transform: uppercase; letter-spacing: 1px; }\n");

        // Tabs Navigation
        html.append("    .tabs { display: flex; background: #f8f9fa; border-bottom: 2px solid #e0e0e0; padding: 0 30px; overflow-x: auto; }\n");
        html.append("    .tab { padding: 20px 30px; cursor: pointer; border: none; background: none; font-size: 1em; font-weight: 600; color: #666; position: relative; transition: color 0.3s; white-space: nowrap; }\n");
        html.append("    .tab:hover { color: #667eea; }\n");
        html.append("    .tab.active { color: #667eea; }\n");
        html.append("    .tab.active::after { content: ''; position: absolute; bottom: -2px; left: 0; right: 0; height: 3px; background: linear-gradient(135deg, #667eea 0%, #764ba2 100%); }\n");

        // Tab Content
        html.append("    .tab-content { display: none; padding: 40px; animation: fadeIn 0.5s; }\n");
        html.append("    .tab-content.active { display: block; }\n");
        html.append("    @keyframes fadeIn { from { opacity: 0; transform: translateY(10px); } to { opacity: 1; transform: translateY(0); } }\n");

        // Info Grid
        html.append("    .info-grid { display: grid; grid-template-columns: repeat(auto-fit, minmax(300px, 1fr)); gap: 20px; margin: 20px 0; }\n");
        html.append("    .info-item { background: #f8f9fa; padding: 20px; border-radius: 10px; border-left: 4px solid #667eea; }\n");
        html.append("    .info-label { font-weight: 600; color: #667eea; margin-bottom: 8px; font-size: 0.85em; text-transform: uppercase; letter-spacing: 0.5px; }\n");
        html.append("    .info-value { color: #333; word-break: break-word; font-size: 1em; }\n");

        // Module Cards
        html.append("    .module-card { background: linear-gradient(135deg, #f8f9fa 0%, #ffffff 100%); padding: 30px; margin: 20px 0; border-radius: 15px; box-shadow: 0 4px 15px rgba(0,0,0,0.1); border: 1px solid #e0e0e0; transition: transform 0.3s, box-shadow 0.3s; }\n");
        html.append("    .module-card:hover { transform: translateY(-3px); box-shadow: 0 8px 25px rgba(0,0,0,0.15); }\n");
        html.append("    .module-header { display: flex; align-items: center; justify-content: space-between; margin-bottom: 20px; flex-wrap: wrap; gap: 10px; }\n");
        html.append("    .module-title { font-size: 1.8em; font-weight: bold; color: #333; }\n");
        html.append("    .module-badges { display: flex; gap: 8px; flex-wrap: wrap; }\n");

        // Badges
        html.append("    .badge { display: inline-block; padding: 6px 14px; border-radius: 20px; font-size: 0.75em; font-weight: bold; text-transform: uppercase; letter-spacing: 0.5px; }\n");
        html.append("    .badge-spring { background: linear-gradient(135deg, #6DB33F 0%, #5a9e32 100%); color: white; box-shadow: 0 2px 8px rgba(109,179,63,0.3); }\n");
        html.append("    .badge-jar { background: linear-gradient(135deg, #2196F3 0%, #1976D2 100%); color: white; box-shadow: 0 2px 8px rgba(33,150,243,0.3); }\n");
        html.append("    .badge-war { background: linear-gradient(135deg, #FF9800 0%, #F57C00 100%); color: white; box-shadow: 0 2px 8px rgba(255,152,0,0.3); }\n");
        html.append("    .badge-git { background: linear-gradient(135deg, #F05032 0%, #d63e1f 100%); color: white; box-shadow: 0 2px 8px rgba(240,80,50,0.3); }\n");
        html.append("    .badge-ci { background: linear-gradient(135deg, #24292e 0%, #1a1d21 100%); color: white; box-shadow: 0 2px 8px rgba(36,41,46,0.3); }\n");
        html.append("    .badge-deployable { background: linear-gradient(135deg, #10b981 0%, #059669 100%); color: white; box-shadow: 0 2px 8px rgba(16,185,129,0.3); }\n");

        // Tables
        html.append("    .table-container { overflow-x: auto; margin: 20px 0; border-radius: 10px; box-shadow: 0 2px 10px rgba(0,0,0,0.05); }\n");
        html.append("    table { width: 100%; border-collapse: collapse; background: white; }\n");
        html.append("    th { background: linear-gradient(135deg, #667eea 0%, #764ba2 100%); color: white; padding: 15px; text-align: left; font-weight: 600; text-transform: uppercase; font-size: 0.85em; letter-spacing: 0.5px; }\n");
        html.append("    td { padding: 15px; border-bottom: 1px solid #e0e0e0; color: #333; }\n");
        html.append("    tr:last-child td { border-bottom: none; }\n");
        html.append("    tr:hover { background: #f8f9fa; }\n");

        // Code
        html.append("    code { background: #2d2d2d; color: #f8f8f2; padding: 4px 8px; border-radius: 5px; font-family: 'Courier New', monospace; font-size: 0.9em; }\n");
        html.append("    a { color: #667eea; text-decoration: none; transition: color 0.3s; }\n");
        html.append("    a:hover { color: #764ba2; text-decoration: underline; }\n");
        html.append("    .repo-link { display: inline-flex; align-items: center; gap: 5px; padding: 6px 12px; background: linear-gradient(135deg, #667eea 0%, #764ba2 100%); color: white; border-radius: 6px; font-weight: 600; font-size: 0.9em; transition: transform 0.2s, box-shadow 0.2s; }\n");
        html.append("    .repo-link:hover { transform: translateY(-2px); box-shadow: 0 4px 12px rgba(102,126,234,0.4); text-decoration: none; color: white; }\n");

        // Section Headers
        html.append("    .section-header { font-size: 1.4em; font-weight: bold; color: #333; margin: 30px 0 20px 0; padding-bottom: 10px; border-bottom: 2px solid #e0e0e0; }\n");

        // Dependency Tree (collapsible)
        html.append("    .dep-tree ul { list-style: none; margin: 6px 0 6px 14px; padding-left: 14px; border-left: 1px dashed #e0e0f0; }\n");
        html.append("    .dep-tree .dep-node { margin: 4px 0; }\n");
        html.append("    .dep-tree .dep-node.collapsed > ul { display: none; }\n");
        html.append("    .dep-tree .tree-toggle { display: inline-block; width: 18px; color: #667eea; cursor: pointer; user-select: none; margin-right: 6px; }\n");
        html.append("    .dep-tree .dep-label { color: #333; }\n");

        html.append("    /* Scope badges, highlights, quick filters */\n");
        html.append("    .scope-badge { display:inline-block; padding:2px 6px; border-radius:10px; font-size:0.85em; color:#fff; margin-left:6px; vertical-align:middle; }\n");
        html.append("    .scope-compile { background:#4caf50; }\n");
        html.append("    .scope-runtime { background:#ff9800; }\n");
        html.append("    .scope-test { background:#9c27b0; }\n");
        html.append("    .scope-provided { background:#607d8b; }\n");
        html.append("    .scope-system { background:#795548; }\n");
        html.append("    .scope-import { background:#3f51b5; }\n");
        html.append("    mark.hl { background:#ffe08a; padding:0 2px; border-radius:2px; }\n");
        html.append("    .current-match { box-shadow: 0 0 0 2px #f39c12 inset; border-radius:4px; }\n");
        html.append("    .quick-filters { margin-top:10px; display:flex; gap:8px; flex-wrap:wrap; align-items:center; }\n");
        html.append("    .filter-chip { padding:6px 10px; border-radius:16px; border:1px solid #e0e0e0; background:#f8f9fa; cursor:pointer; font-size:0.9em; }\n");
        html.append("    .filter-chip.active { background:#667eea; color:#fff; border-color:#667eea; }\n");
        html.append("    body.dark-mode mark.hl { background:#665200; }\n");
        html.append("    body.dark-mode .filter-chip { background:#0f3460; border-color:#2a2a3e; color:#e0e0e0; }\n");
        html.append("    body.dark-mode .filter-chip.active { background:#4953c8; border-color:#4953c8; color:#fff; }\n");
        // Empty State
        html.append("    .empty-state { text-align: center; padding: 60px 20px; color: #999; }\n");
        html.append("    .empty-state-icon { font-size: 4em; margin-bottom: 20px; opacity: 0.3; }\n");

        // Dark Mode Styles
        html.append("    body.dark-mode { background: linear-gradient(135deg, #1a1a2e 0%, #16213e 100%); }\n");
        html.append("    body.dark-mode .container { background: #0f3460; }\n");
        html.append("    body.dark-mode .header { background: linear-gradient(135deg, #16213e 0%, #0f3460 100%); }\n");
        html.append("    body.dark-mode .stats { background: #16213e; }\n");
        html.append("    body.dark-mode .stat-card { background: #1a1a2e; color: #e0e0e0; }\n");
        html.append("    body.dark-mode .stat-card .label { color: #a0a0a0; }\n");
        html.append("    body.dark-mode .tabs { background: #16213e; border-bottom-color: #2a2a3e; }\n");
        html.append("    body.dark-mode .tab { color: #a0a0a0; }\n");
        html.append("    body.dark-mode .tab:hover { color: #667eea; }\n");
        html.append("    body.dark-mode .tab.active { color: #667eea; }\n");
        html.append("    body.dark-mode .tab-content { background: #0f3460; color: #e0e0e0; }\n");
        html.append("    body.dark-mode .module-card { background: #1a1a2e; border-color: #2a2a3e; }\n");
        html.append("    body.dark-mode .module-title { color: #e0e0e0; }\n");
        html.append("    body.dark-mode .info-label { color: #a0a0a0; }\n");
        html.append("    body.dark-mode .dep-tree ul { border-left-color: #2a2a3e; }\n");
        html.append("    body.dark-mode .dep-tree .dep-label { color: #e0e0e0; }\n");
        html.append("    body.dark-mode .dep-tree .tree-toggle { color: #a0a0ff; }\n");

        html.append("    body.dark-mode .info-value { color: #e0e0e0; }\n");
        html.append("    body.dark-mode .section-header { color: #e0e0e0; border-bottom-color: #2a2a3e; }\n");
        html.append("    body.dark-mode table { background: #1a1a2e; }\n");
        html.append("    body.dark-mode td { color: #e0e0e0; border-bottom-color: #2a2a3e; }\n");
        html.append("    body.dark-mode tr:hover { background: #16213e; }\n");
        html.append("    body.dark-mode code { background: #16213e; color: #a0e9ff; }\n");
        html.append("    body.dark-mode .empty-state { color: #666; }\n");

        html.append("  </style>\n");
        html.append("</head>\n");
        html.append("<body>\n");
        html.append("  <div class=\"container\">\n");

        // Header
        html.append("    <div class=\"header\">\n");
        html.append("      <div>\n");
        html.append("        <h1>").append(escapeHtml(descriptor.projectName())).append("</h1>\n");
        html.append("        <div class=\"subtitle\">Deployment Descriptor</div>\n");
        html.append("        <div class=\"timestamp\">📅 Generated: ").append(descriptor.generatedAt()).append("</div>\n");
        html.append("      </div>\n");

        html.append("      <button class=\"theme-toggle\" onclick=\"toggleTheme()\" title=\"Toggle Dark/Light Mode\">\n");
        html.append("        <span class=\"theme-icon\">🌙</span>\n");
        html.append("      </button>\n");
        html.append("    </div>\n");

        // Stats Cards
        html.append("    <div class=\"stats\">\n");
        html.append("      <div class=\"stat-card\">\n");
        html.append("        <div class=\"number\">").append(descriptor.totalModules()).append("</div>\n");
        html.append("        <div class=\"label\">Total Modules</div>\n");
        html.append("      </div>\n");
        html.append("      <div class=\"stat-card\">\n");
        html.append("        <div class=\"number\">").append(descriptor.deployableModulesCount()).append("</div>\n");
        html.append("        <div class=\"label\">Deployable</div>\n");
        html.append("      </div>\n");
        html.append("      <div class=\"stat-card\">\n");
        html.append("        <div class=\"number\">").append(escapeHtml(descriptor.projectVersion())).append("</div>\n");
        html.append("        <div class=\"label\">Version</div>\n");
        html.append("      </div>\n");
        if (descriptor.buildInfo() != null && descriptor.buildInfo().gitBranch() != null) {
            html.append("      <div class=\"stat-card\">\n");
            html.append("        <div class=\"number\" style=\"font-size: 1.5em;\">🌿</div>\n");
            html.append("        <div class=\"label\">").append(escapeHtml(descriptor.buildInfo().gitBranch())).append("</div>\n");
            html.append("      </div>\n");
        }
        html.append("    </div>\n");

        // Tabs Navigation
        html.append("    <div class=\"tabs\">\n");
        html.append("      <button class=\"tab active\" onclick=\"showTab(this, 'overview')\">📊 Overview</button>\n");
        html.append("      <button class=\"tab\" onclick=\"showTab(this, 'build')\">🔨 Build Info</button>\n");
        html.append("      <button class=\"tab\" onclick=\"showTab(this, 'modules')\">📦 Modules</button>\n");
        html.append("      <button class=\"tab\" onclick=\"showTab(this, 'dependencies')\">🧩 Dependencies</button>\n");

        html.append("      <button class=\"tab\" onclick=\"showTab(this, 'environments')\">🌍 Environments</button>\n");
        html.append("      <button class=\"tab\" onclick=\"showTab(this, 'compliance')\">⚖️ Compliance</button>\n");

        html.append("      <button class=\"tab\" onclick=\"showTab(this, 'assemblies')\">📚 Assemblies</button>\n");
        html.append("    </div>\n");

        // Tab 1: Overview
        html.append("    <div id=\"overview\" class=\"tab-content active\">\n");
        html.append("      <div class=\"section-header\">📋 Project Information</div>\n");
        html.append("      <div class=\"info-grid\">\n");
        html.append("        <div class=\"info-item\">\n");
        html.append("          <div class=\"info-label\">Group ID</div>\n");
        html.append("          <div class=\"info-value\">").append(escapeHtml(descriptor.projectGroupId())).append("</div>\n");
        html.append("        </div>\n");
        html.append("        <div class=\"info-item\">\n");
        html.append("          <div class=\"info-label\">Artifact ID</div>\n");
        html.append("          <div class=\"info-value\">").append(escapeHtml(descriptor.projectArtifactId())).append("</div>\n");
        html.append("        </div>\n");
        html.append("        <div class=\"info-item\">\n");
        html.append("          <div class=\"info-label\">Version</div>\n");
        html.append("          <div class=\"info-value\">").append(escapeHtml(descriptor.projectVersion())).append("</div>\n");
        html.append("        </div>\n");
        if (descriptor.projectDescription() != null) {
            html.append("        <div class=\"info-item\" style=\"grid-column: 1 / -1;\">\n");
            html.append("          <div class=\"info-label\">Description</div>\n");
            html.append("          <div class=\"info-value\">").append(escapeHtml(descriptor.projectDescription())).append("</div>\n");
            html.append("        </div>\n");
        }
        html.append("      </div>\n");

        // Quick Summary
        if (descriptor.deployableModules() != null && !descriptor.deployableModules().isEmpty()) {
            html.append("      <div class=\"section-header\">🚀 Quick Summary</div>\n");
            html.append("      <div class=\"table-container\">\n");
            html.append("        <table>\n");
            html.append("          <tr><th>Module</th><th>Type</th><th>Framework</th><th>Environments</th></tr>\n");
            descriptor.deployableModules().forEach(module -> {
                html.append("          <tr>\n");
                html.append("            <td><strong>").append(escapeHtml(module.getArtifactId())).append("</strong></td>\n");
                html.append("            <td><span class=\"badge badge-").append(module.getPackaging()).append("\">").append(module.getPackaging().toUpperCase()).append("</span></td>\n");
                html.append("            <td>");
                if (module.isSpringBootExecutable()) {
                    html.append("<span class=\"badge badge-spring\">Spring Boot</span>");
                } else {
                    html.append("-");
                }
                html.append("</td>\n");
                html.append("            <td>").append(module.getEnvironments() != null ? module.getEnvironments().size() : 0).append("</td>\n");
                html.append("          </tr>\n");
            });
            html.append("        </table>\n");
            html.append("      </div>\n");
        }
        html.append("    </div>\n");

        // Tab 2: Build Info
        html.append("    <div id=\"build\" class=\"tab-content\">\n");
        if (descriptor.buildInfo() != null) {
            var buildInfo = descriptor.buildInfo();

            // Git Information
            html.append("      <div class=\"section-header\">🌿 Git Information</div>\n");
            html.append("      <div class=\"info-grid\">\n");
            if (buildInfo.gitCommitSha() != null) {
                html.append("        <div class=\"info-item\">\n");
                html.append("          <div class=\"info-label\">Commit SHA (Short)</div>\n");
                html.append("          <div class=\"info-value\"><code>").append(escapeHtml(buildInfo.gitCommitShortSha())).append("</code></div>\n");
                html.append("        </div>\n");
                html.append("        <div class=\"info-item\">\n");
                html.append("          <div class=\"info-label\">Commit SHA (Full)</div>\n");
                html.append("          <div class=\"info-value\"><code>").append(escapeHtml(buildInfo.gitCommitSha())).append("</code></div>\n");
                html.append("        </div>\n");
            }
            if (buildInfo.gitBranch() != null) {
                html.append("        <div class=\"info-item\">\n");
                html.append("          <div class=\"info-label\">Branch</div>\n");
                html.append("          <div class=\"info-value\"><span class=\"badge badge-git\">").append(escapeHtml(buildInfo.gitBranch())).append("</span></div>\n");
                html.append("        </div>\n");
            }
            if (buildInfo.gitTag() != null) {
                html.append("        <div class=\"info-item\">\n");
                html.append("          <div class=\"info-label\">Tag</div>\n");
                html.append("          <div class=\"info-value\"><span class=\"badge badge-git\">").append(escapeHtml(buildInfo.gitTag())).append("</span></div>\n");
                html.append("        </div>\n");
            }
            if (buildInfo.gitDirty() != null) {
                html.append("        <div class=\"info-item\">\n");
                html.append("          <div class=\"info-label\">Working Directory Status</div>\n");
                html.append("          <div class=\"info-value\">").append(buildInfo.gitDirty() ? "⚠️ Uncommitted changes" : "✅ Clean").append("</div>\n");
                html.append("        </div>\n");
            }
            if (buildInfo.gitRemoteUrl() != null) {
                html.append("        <div class=\"info-item\" style=\"grid-column: 1 / -1;\">\n");
                html.append("          <div class=\"info-label\">Remote URL</div>\n");
                html.append("          <div class=\"info-value\"><a href=\"").append(escapeHtml(buildInfo.gitRemoteUrl())).append("\" target=\"_blank\">").append(escapeHtml(buildInfo.gitRemoteUrl())).append("</a></div>\n");
                html.append("        </div>\n");
            }
            if (buildInfo.gitCommitMessage() != null) {
                html.append("        <div class=\"info-item\" style=\"grid-column: 1 / -1;\">\n");
                html.append("          <div class=\"info-label\">Commit Message</div>\n");
                html.append("          <div class=\"info-value\">").append(escapeHtml(buildInfo.gitCommitMessage())).append("</div>\n");
                html.append("        </div>\n");
            }
            if (buildInfo.gitCommitAuthor() != null) {
                html.append("        <div class=\"info-item\">\n");
                html.append("          <div class=\"info-label\">Author</div>\n");
                html.append("          <div class=\"info-value\">👤 ").append(escapeHtml(buildInfo.gitCommitAuthor())).append("</div>\n");
                html.append("        </div>\n");
            }
            if (buildInfo.gitCommitTime() != null) {
                html.append("        <div class=\"info-item\">\n");
                html.append("          <div class=\"info-label\">Commit Time</div>\n");
                html.append("          <div class=\"info-value\">🕐 ").append(buildInfo.gitCommitTime()).append("</div>\n");
                html.append("        </div>\n");
            }
            html.append("      </div>\n");

            // Build Information
            html.append("      <div class=\"section-header\">🔨 Build Information</div>\n");
            html.append("      <div class=\"info-grid\">\n");
            if (buildInfo.buildTimestamp() != null) {
                html.append("        <div class=\"info-item\">\n");
                html.append("          <div class=\"info-label\">Build Timestamp</div>\n");
                html.append("          <div class=\"info-value\">🕐 ").append(buildInfo.buildTimestamp()).append("</div>\n");
                html.append("        </div>\n");
            }
            if (buildInfo.buildHost() != null) {
                html.append("        <div class=\"info-item\">\n");
                html.append("          <div class=\"info-label\">Build Host</div>\n");
                html.append("          <div class=\"info-value\">💻 ").append(escapeHtml(buildInfo.buildHost())).append("</div>\n");
                html.append("        </div>\n");
            }
            if (buildInfo.buildUser() != null) {
                html.append("        <div class=\"info-item\">\n");
                html.append("          <div class=\"info-label\">Build User</div>\n");
                html.append("          <div class=\"info-value\">👤 ").append(escapeHtml(buildInfo.buildUser())).append("</div>\n");
                html.append("        </div>\n");
            }
            html.append("      </div>\n");

            // CI/CD Information
            if (buildInfo.ciProvider() != null) {
                html.append("      <div class=\"section-header\">🔄 CI/CD Information</div>\n");
                html.append("      <div class=\"info-grid\">\n");
                html.append("        <div class=\"info-item\">\n");
                html.append("          <div class=\"info-label\">CI Provider</div>\n");
                html.append("          <div class=\"info-value\"><span class=\"badge badge-ci\">").append(escapeHtml(buildInfo.ciProvider())).append("</span></div>\n");
                html.append("        </div>\n");
                if (buildInfo.ciBuildId() != null) {
                    html.append("        <div class=\"info-item\">\n");
                    html.append("          <div class=\"info-label\">Build ID</div>\n");
                    html.append("          <div class=\"info-value\">").append(escapeHtml(buildInfo.ciBuildId())).append("</div>\n");
                    html.append("        </div>\n");
                }
                if (buildInfo.ciBuildUrl() != null) {
                    html.append("        <div class=\"info-item\" style=\"grid-column: 1 / -1;\">\n");
                    html.append("          <div class=\"info-label\">Build URL</div>\n");
                    html.append("          <div class=\"info-value\"><a href=\"").append(escapeHtml(buildInfo.ciBuildUrl())).append("\" target=\"_blank\">🔗 ").append(escapeHtml(buildInfo.ciBuildUrl())).append("</a></div>\n");
                    html.append("        </div>\n");
                }
                html.append("      </div>\n");
            // Maven Runtime
            if (buildInfo.maven() != null) {
                var mvn = buildInfo.maven();
                html.append("      <div class=\"section-header\">🧰 Maven Runtime</div>\n");
                html.append("      <div class=\"info-grid\">\n");
                if (mvn.getVersion() != null) {
                    html.append("        <div class=\"info-item\">\n");
                    html.append("          <div class=\"info-label\">Maven Version</div>\n");
                    html.append("          <div class=\"info-value\"><code>").append(escapeHtml(mvn.getVersion())).append("</code></div>\n");
                    html.append("        </div>\n");
                }
                if (mvn.getHome() != null) {
                    html.append("        <div class=\"info-item\">\n");
                    html.append("          <div class=\"info-label\">Maven Home</div>\n");
                    html.append("          <div class=\"info-value\">🏠 ").append(escapeHtml(mvn.getHome())).append("</div>\n");
                    html.append("        </div>\n");
                }
                html.append("      </div>\n");
            }

            // Executed Goals
            if (buildInfo.goals() != null && (buildInfo.goals().getDefaultGoal() != null || (buildInfo.goals().getExecuted() != null && !buildInfo.goals().getExecuted().isEmpty()))) {
                var goals = buildInfo.goals();
                html.append("      <div class=\"section-header\">🎯 Maven Goals</div>\n");
                html.append("      <div class=\"info-grid\">\n");
                if (goals.getDefaultGoal() != null) {
                    html.append("        <div class=\"info-item\">\n");
                    html.append("          <div class=\"info-label\">Default Goal</div>\n");
                    html.append("          <div class=\"info-value\"><code>").append(escapeHtml(goals.getDefaultGoal())).append("</code></div>\n");
                    html.append("        </div>\n");
                }
                if (goals.getExecuted() != null && !goals.getExecuted().isEmpty()) {
                    html.append("        <div class=\"info-item\" style=\"grid-column: 1 / -1;\">\n");
                    html.append("          <div class=\"info-label\">Executed</div>\n");
                    html.append("          <div class=\"info-value\">");
                    for (int i = 0; i < goals.getExecuted().size(); i++) {
                        if (i > 0) html.append(", ");
                        html.append("<span class=\\\"badge\\\">")
                            .append(escapeHtml(String.valueOf(goals.getExecuted().get(i))))
                            .append("</span>");
                    }
                    html.append("</div>\n");
                    html.append("        </div>\n");
                }
                html.append("      </div>\n");
            }

            // Build Properties
            if (buildInfo.properties() != null) {
                var props = buildInfo.properties();
                html.append("      <div class=\"section-header\">⚙️ Build Properties</div>\n");
                html.append("      <div style=\"margin:6px 0 12px 0;\">\n");
                html.append("        <input id=\"prop-search\" type=\"text\" placeholder=\"Search property key or value...\" style=\"padding:8px; width:320px; margin-right:8px;\">\n");
                if (props.getMaskedCount() != null && props.getMaskedCount() > 0) {
                    html.append("        <span style=\"color:#666;\">(").append(String.valueOf(props.getMaskedCount())).append(" value(s) masked)</span>\n");
                }
                html.append("      </div>\n");

                // helper to render a table for a map
                java.util.function.BiConsumer<String, java.util.Map<String,String>> renderTable = (title, map) -> {
                    if (map == null || map.isEmpty()) return;
                    html.append("      <details open style=\"margin-bottom:10px;\"><summary style=\"cursor:pointer;\"><strong>").append(escapeHtml(title)).append("</strong> (").append(String.valueOf(map.size())).append(")</summary>\n");
                    html.append("      <div style=\"overflow:auto;\">\n");
                    html.append("        <table class=\"data-table\" style=\"min-width:520px;\">\n");
                    html.append("          <thead><tr><th style=\"width:35%\">Key</th><th>Value</th></tr></thead>\n");
                    html.append("          <tbody>\n");
                    java.util.List<java.util.Map.Entry<String,String>> entries = new java.util.ArrayList<>(map.entrySet());
                    entries.sort(java.util.Map.Entry.comparingByKey());
                    for (var e : entries) {
                        String k = e.getKey(); String v = e.getValue();
                        html.append("            <tr class=\"prop-row\" data-key=\"").append(escapeHtml(k)).append("\" data-val=\"").append(escapeHtml(String.valueOf(v))).append("\">\n");
                        html.append("              <td><code>").append(escapeHtml(k)).append("</code></td>\n");
                        html.append("              <td><code>").append(escapeHtml(String.valueOf(v))).append("</code></td>\n");
                        html.append("            </tr>\n");
                    }
                    html.append("          </tbody>\n");
                    html.append("        </table>\n");
                    html.append("      </div>\n");
                    html.append("      </details>\n");
                };

                renderTable.accept("Project Properties", props.getProject());

                renderTable.accept("Maven Properties", props.getMaven());
                renderTable.accept("Custom Properties", props.getCustom());
                renderTable.accept("System Properties", props.getSystem());
                renderTable.accept("Environment Variables", props.getEnvironment());

                html.append("      <script>(function(){ var s=document.getElementById('prop-search'); if(!s) return; function apply(){ var q=s.value.toLowerCase(); document.querySelectorAll('tr.prop-row').forEach(function(tr){ var k=tr.getAttribute('data-key')||''; var v=tr.getAttribute('data-val')||''; tr.style.display = (!q || k.toLowerCase().includes(q) || v.toLowerCase().includes(q)) ? '' : 'none'; }); } s.addEventListener('input', apply); })();</script>\n");
            }

            // Profiles
            if (buildInfo.profiles() != null) {
                var pf = buildInfo.profiles();
                html.append("      <div class=\"section-header\">🧩 Maven Profiles</div>\n");
                html.append("      <div class=\"info-grid\">\n");
                if (pf.getDefaultProfile() != null) {
                    html.append("        <div class=\"info-item\">\n");
                    html.append("          <div class=\"info-label\">Default Profile</div>\n");
                    html.append("          <div class=\"info-value\"><span class=\"badge\">").append(escapeHtml(pf.getDefaultProfile())).append("</span></div>\n");
                    html.append("        </div>\n");
                }
                if (pf.getActive() != null && !pf.getActive().isEmpty()) {
                    html.append("        <div class=\"info-item\" style=\"grid-column: 1 / -1;\">\n");
                    html.append("          <div class=\"info-label\">Active Profiles</div>\n");
                    html.append("          <div class=\"info-value\">");
                    for (int i = 0; i < pf.getActive().size(); i++) {
                        if (i > 0) html.append(" ");
                        html.append("<span class=\\\"badge\\\">")
                            .append(escapeHtml(String.valueOf(pf.getActive().get(i))))
                            .append("</span>");
                    }
                    html.append("</div>\n");
                    html.append("        </div>\n");
                }
                if (pf.getAvailable() != null && !pf.getAvailable().isEmpty()) {
                    html.append("        <div class=\"info-item\" style=\"grid-column: 1 / -1;\">\n");
                    html.append("          <div class=\"info-label\">Available Profiles</div>\n");
                    html.append("          <div class=\"info-value\"><code>").append(escapeHtml(String.join(", ", pf.getAvailable()))).append("</code></div>\n");
                    html.append("        </div>\n");
                }
                html.append("      </div>\n");
            }

            // Build Plugins (aggregated)
            if (buildInfo.plugins() != null) {
                var pinfo = buildInfo.plugins();
                html.append("      <div class=\"section-header\">🔧 Build Plugins</div>\n");
                if (pinfo.getSummary() != null) {
                    html.append("      <div style=\"display:flex; gap:8px; margin:6px 0 12px 0; flex-wrap: wrap;\">\n");
                    var s = pinfo.getSummary();
                    html.append("        <span class=\"badge\">total: ").append(String.valueOf(s.getTotal())).append("</span>\n");
                    if (s.getWithConfiguration() != null) html.append("        <span class=\"badge badge-jar\">config: ").append(String.valueOf(s.getWithConfiguration())).append("</span>\n");
                    if (s.getFromManagement() != null) html.append("        <span class=\"badge badge-scope-compile\">mgmt: ").append(String.valueOf(s.getFromManagement())).append("</span>\n");
                    if (s.getOutdated() != null && s.getOutdated() > 0) html.append("        <span class=\"badge badge-scope-runtime\">outdated: ").append(String.valueOf(s.getOutdated())).append("</span>\n");
                    html.append("      </div>\n");
                }
                if (pinfo.getList() != null && !pinfo.getList().isEmpty()) {
                    html.append("      <div class=\"table-container\">\n");
                    html.append("        <table>\n");
                    html.append("          <thead><tr><th>Plugin</th><th>Version</th><th>Phase</th><th>Goals</th><th>Source</th></tr></thead>\n");
                    html.append("          <tbody>\n");
                    for (var pd : pinfo.getList()) {
                        html.append("            <tr>\n");
                        String coord = (pd.getGroupId() != null ? pd.getGroupId() : "") + ":" + (pd.getArtifactId() != null ? pd.getArtifactId() : "");
                        html.append("              <td><code>").append(escapeHtml(coord)).append("</code>");
                        if (pd.getConfiguration() != null) {
                            String cfg = String.valueOf(pd.getConfiguration());
                            html.append("<details style=\"margin-top:4px;\"><summary style=\"cursor:pointer;\">config</summary><pre class=\"code-block\">")
                                .append(escapeHtml(cfg))
                                .append("</pre></details>");
                        }
                        html.append("</td>\n");
                        html.append("              <td>");
                        if (pd.getOutdated() != null && pd.getOutdated().getLatest() != null && pd.getVersion() != null && !pd.getVersion().equals(pd.getOutdated().getLatest())) {
                            html.append("<span class=\"badge badge-fail\">")
                                .append(escapeHtml(pd.getVersion()))
                                .append(" → ")
                                .append(escapeHtml(pd.getOutdated().getLatest()))
                                .append("</span>");
                        } else {
                            html.append(pd.getVersion() == null ? "" : "<code>" + escapeHtml(pd.getVersion()) + "</code>");
                        }
                        html.append("</td>\n");
                        html.append("              <td>").append(pd.getPhase() == null ? "" : "<span class=\\\"badge\\\">" + escapeHtml(pd.getPhase()) + "</span>").append("</td>\n");
                        html.append("              <td>");
                        if (pd.getGoals() != null && !pd.getGoals().isEmpty()) {
                            for (int i = 0; i < pd.getGoals().size(); i++) {
                                if (i > 0) html.append(" ");
                                html.append("<span class=\\\"badge\\\">")
                                    .append(escapeHtml(String.valueOf(pd.getGoals().get(i))))
                                    .append("</span>");
                            }
                        }
                        html.append("</td>\n");
                        String source = pd.getSource();
                        if (Boolean.TRUE.equals(pd.getInherited())) source = (source == null ? "" : source + ", ") + "inherited";
                        html.append("              <td>").append(source == null ? "" : escapeHtml(source)).append("</td>\n");
                        html.append("            </tr>\n");
                    }
                    html.append("          </tbody>\n");
                    html.append("        </table>\n");
                    html.append("      </div>\n");
                } else {
                    html.append("      <div class=\"empty-state\"><p>No build plugins found.</p></div>\n");
                }

                // Plugin Management section
                if (pinfo.getManagement() != null && !pinfo.getManagement().isEmpty()) {
                    html.append("      <details style=\"margin-top:12px;\"><summary style=\"cursor:pointer;\"><strong>Plugin Management</strong> (")
                        .append(String.valueOf(pinfo.getManagement().size()))
                        .append(")</summary>\n");
                    html.append("      <div class=\"table-container\">\n");
                    html.append("        <table>\n");
                    html.append("          <thead><tr><th>Plugin</th><th>Version</th><th>Used in Build</th></tr></thead>\n");
                    html.append("          <tbody>\n");
                    for (var pm : pinfo.getManagement()) {
                        String coord = (pm.getGroupId() != null ? pm.getGroupId() : "") + ":" + (pm.getArtifactId() != null ? pm.getArtifactId() : "");
                        html.append("            <tr>\n");
                        html.append("              <td><code>").append(escapeHtml(coord)).append("</code></td>\n");
                        html.append("              <td>")
                            .append(pm.getVersion() == null ? "" : "<code>" + escapeHtml(pm.getVersion()) + "</code>")
                            .append("</td>\n");
                        html.append("              <td>");
                        if (Boolean.TRUE.equals(pm.getUsedInBuild())) {
                            html.append("<span class=\"badge badge-success\">✓ used</span>");
                        } else {
                            html.append("<span class=\"badge\">—</span>");
                        }
                        html.append("</td>\n");
                        html.append("            </tr>\n");
                    }
                    html.append("          </tbody>\n");
                    html.append("        </table>\n");
                    html.append("      </div>\n");
                    html.append("      </details>\n");
                }
            }

            }
        } else {
            html.append("      <div class=\"empty-state\">\n");
            html.append("        <div class=\"empty-state-icon\">📭</div>\n");
            html.append("        <p>No build information available</p>\n");
            html.append("      </div>\n");
        }
        html.append("    </div>\n");

        // Tab 3: Modules
        html.append("    <div id=\"modules\" class=\"tab-content\">\n");
        if (descriptor.deployableModules() != null && !descriptor.deployableModules().isEmpty()) {
            descriptor.deployableModules().forEach(module -> {
                html.append("      <div class=\"module-card\">\n");
                html.append("        <div class=\"module-header\">\n");
                html.append("          <div class=\"module-title\">📦 ").append(escapeHtml(module.getArtifactId())).append("</div>\n");
                html.append("          <div class=\"module-badges\">\n");
                html.append("            <span class=\"badge badge-deployable\">✓ DEPLOYABLE</span>\n");
                html.append("            <span class=\"badge badge-").append(module.getPackaging()).append("\">").append(module.getPackaging().toUpperCase()).append("</span>\n");
                if (module.isSpringBootExecutable()) {
                    html.append("            <span class=\"badge badge-spring\">Spring Boot</span>\n");
                }
                html.append("          </div>\n");
                html.append("        </div>\n");

                html.append("        <div class=\"info-grid\">\n");
                html.append("          <div class=\"info-item\">\n");
                html.append("            <div class=\"info-label\">Group ID</div>\n");
                html.append("            <div class=\"info-value\">").append(escapeHtml(module.getGroupId())).append("</div>\n");
                html.append("          </div>\n");
                html.append("          <div class=\"info-item\">\n");
                html.append("            <div class=\"info-label\">Version</div>\n");
                html.append("            <div class=\"info-value\">").append(escapeHtml(module.getVersion())).append("</div>\n");
                html.append("          </div>\n");
                if (module.getFinalName() != null) {
                    html.append("          <div class=\"info-item\">\n");
                    html.append("            <div class=\"info-label\">Final Name</div>\n");
                    html.append("            <div class=\"info-value\">").append(escapeHtml(module.getFinalName())).append("</div>\n");
                    html.append("          </div>\n");
                }
                if (module.getJavaVersion() != null) {
                    html.append("          <div class=\"info-item\">\n");
                    html.append("            <div class=\"info-label\">Java Version</div>\n");
                    html.append("            <div class=\"info-value\">☕ ").append(escapeHtml(module.getJavaVersion())).append("</div>\n");
                    html.append("          </div>\n");
                }
                if (module.getMainClass() != null) {
                    html.append("          <div class=\"info-item\" style=\"grid-column: 1 / -1;\">\n");
                    html.append("            <div class=\"info-label\">Main Class</div>\n");
                    html.append("            <div class=\"info-value\"><code>").append(escapeHtml(module.getMainClass())).append("</code></div>\n");
                    html.append("          </div>\n");
                }
                html.append("          <div class=\"info-item\" style=\"grid-column: 1 / -1;\">\n");
                html.append("            <div class=\"info-label\">Repository Path</div>\n");
                html.append("            <div class=\"info-value\"><code>").append(escapeHtml(module.getRepositoryPath())).append("</code></div>\n");
                html.append("          </div>\n");
                if (module.getRepositoryUrl() != null) {
                    html.append("          <div class=\"info-item\" style=\"grid-column: 1 / -1;\">\n");
                    html.append("            <div class=\"info-label\">Maven Repository</div>\n");
                    html.append("            <div class=\"info-value\"><a href=\"").append(escapeHtml(module.getRepositoryUrl())).append("\" target=\"_blank\" class=\"repo-link\">🔗 ").append(escapeHtml(module.getRepositoryUrl())).append("</a></div>\n");
                    html.append("          </div>\n");
                }
                html.append("        </div>\n");

                // Container Image (if available)
                if (module.getContainer() != null) {
                    var c = module.getContainer();
                    html.append("        <div class=\"section-header\" style=\"font-size: 1.1em; margin-top: 20px;\">🐳 Container Image</div>\n");
                    html.append("        <div class=\"info-grid\">\n");

                    if (c.getTool() != null) {
                        html.append("          <div class=\"info-item\">\n");
                        html.append("            <div class=\"info-label\">Tool</div>\n");
                        html.append("            <div class=\"info-value\">").append(escapeHtml(c.getTool())).append("</div>\n");
                        html.append("          </div>\n");
                    }

                    if (c.getImage() != null) {
                        html.append("          <div class=\"info-item\">\n");
                        html.append("            <div class=\"info-label\">Image</div>\n");
                        html.append("            <div class=\"info-value\"><code>").append(escapeHtml(c.getImage())).append("</code></div>\n");
                        html.append("          </div>\n");
                    }

                    if (c.getTag() != null) {
                        html.append("          <div class=\"info-item\">\n");
                        html.append("            <div class=\"info-label\">Tag</div>\n");
                        html.append("            <div class=\"info-value\"><code>").append(escapeHtml(c.getTag())).append("</code></div>\n");
                        html.append("          </div>\n");
                    }

                    if (c.getAdditionalTags() != null && !c.getAdditionalTags().isEmpty()) {
                        html.append("          <div class=\"info-item\">\n");
                        html.append("            <div class=\"info-label\">Additional Tags</div>\n");
                        html.append("            <div class=\"info-value\"><code>")
                            .append(escapeHtml(String.join(", ", c.getAdditionalTags())))
                            .append("</code></div>\n");
                        html.append("          </div>\n");
                    }

                    if (c.getRegistry() != null) {
                        html.append("          <div class=\"info-item\">\n");
                        html.append("            <div class=\"info-label\">Registry</div>\n");
                        html.append("            <div class=\"info-value\">").append(escapeHtml(c.getRegistry())).append("</div>\n");
                        html.append("          </div>\n");
                    }

                    if (c.getGroup() != null) {
                        html.append("          <div class=\"info-item\">\n");
                        html.append("            <div class=\"info-label\">Group</div>\n");
                        html.append("            <div class=\"info-value\">").append(escapeHtml(c.getGroup())).append("</div>\n");
                        html.append("          </div>\n");
                    }

                    if (c.getBaseImage() != null) {
                        html.append("          <div class=\"info-item\">\n");
                        html.append("            <div class=\"info-label\">Base Image</div>\n");
                        html.append("            <div class=\"info-value\"><code>").append(escapeHtml(c.getBaseImage())).append("</code></div>\n");
                        html.append("          </div>\n");
                    }

                    if (c.getBuilderImage() != null) {
                        html.append("          <div class=\"info-item\">\n");
                        html.append("            <div class=\"info-label\">Builder Image</div>\n");
                        html.append("            <div class=\"info-value\"><code>").append(escapeHtml(c.getBuilderImage())).append("</code></div>\n");
                        html.append("          </div>\n");
                    }

                    if (c.getRunImage() != null) {
                        html.append("          <div class=\"info-item\">\n");
                        html.append("            <div class=\"info-label\">Run Image</div>\n");
                        html.append("            <div class=\"info-value\"><code>").append(escapeHtml(c.getRunImage())).append("</code></div>\n");
                        html.append("          </div>\n");
                    }

                    if (c.getPublish() != null) {
                        html.append("          <div class=\"info-item\">\n");
                        html.append("            <div class=\"info-label\">Auto-Publish</div>\n");
                        html.append("            <div class=\"info-value\">")
                            .append(Boolean.TRUE.equals(c.getPublish()) ? "✅ Enabled" : "❌ Disabled")
                            .append("</div>\n");
                        html.append("          </div>\n");
                    }

                    // Example commands
                    if (c.getImage() != null) {
                        String ref = c.getImage();
                        if (c.getTag() != null && !c.getTag().isEmpty()) {
                            ref = ref + ":" + c.getTag();
                        }
                        html.append("          <div class=\"info-item\" style=\"grid-column: 1 / -1;\">\n");
                        html.append("            <div class=\"info-label\">Commands</div>\n");
                        html.append("            <div class=\"info-value\">");
                        html.append("<div>Pull: <code>docker pull ").append(escapeHtml(ref)).append("</code></div>");
                        html.append("<div>Run: <code>docker run --rm ").append(escapeHtml(ref)).append("</code></div>");
                        html.append("            </div>\n");
                        html.append("          </div>\n");
                    }

                    html.append("        </div>\n");
                }


                // Build Plugins
                if (module.getBuildPlugins() != null && !module.getBuildPlugins().isEmpty()) {
                    html.append("        <div class=\"section-header\" style=\"font-size: 1.1em; margin-top: 20px;\">🔧 Build Plugins</div>\n");
                    html.append("        <div style=\"display: flex; gap: 8px; flex-wrap: wrap; margin-top: 10px;\">\n");
                    module.getBuildPlugins().forEach(plugin -> {
                        html.append("          <span class=\"badge badge-jar\">").append(escapeHtml(plugin)).append("</span>\n");
                    });
                    html.append("        </div>\n");
                }


                // Dependencies section (interactive) if available
                if (false && module.getDependencies() != null) {
                    var deps = module.getDependencies();
                    var summary = deps.getSummary();
                    String moduleId = module.getArtifactId().replaceAll("[^A-Za-z0-9_-]", "_");

                    html.append("        <div class=\"section-header\" style=\"font-size: 1.1em; margin-top: 20px;\">🧩 Dependencies</div>\n");

                    // Summary cards
                    html.append("        <div class=\"info-grid\">\n");
                    if (summary != null) {
                        html.append("          <div class=\"info-item\">\n");
                        html.append("            <div class=\"info-label\">Total</div>\n");
                        html.append("            <div class=\"info-value\"><strong>").append(summary.getTotal()).append("</strong></div>\n");
                        html.append("          </div>\n");
                        html.append("          <div class=\"info-item\">\n");
                        html.append("            <div class=\"info-label\">Direct</div>\n");
                        html.append("            <div class=\"info-value\"><strong>").append(summary.getDirect()).append("</strong></div>\n");
                        html.append("          </div>\n");
                        html.append("          <div class=\"info-item\">\n");
                        html.append("            <div class=\"info-label\">Transitive</div>\n");
                        html.append("            <div class=\"info-value\"><strong>").append(summary.getTransitive()).append("</strong></div>\n");
                        html.append("          </div>\n");
                        html.append("          <div class=\"info-item\">\n");
                        html.append("            <div class=\"info-label\">Optional</div>\n");
                        html.append("            <div class=\"info-value\"><strong>").append(summary.getOptional()).append("</strong></div>\n");
                        html.append("          </div>\n");
                    }
                    html.append("        </div>\n");

                    // Controls
                    boolean hasFlat = deps.getFlat() != null && !deps.getFlat().isEmpty();
                    boolean hasTree = deps.getTree() != null && !deps.getTree().isEmpty();
                    String defaultView = hasFlat ? "flat" : (hasTree ? "tree" : "flat");

                    html.append("        <div id=\"dep-section-").append(moduleId).append("\" style=\"margin: 10px 0 5px 0;\">\n");
                    // Search
                    html.append("          <input id=\"dep-search-").append(moduleId).append("\" type=\"text\" placeholder=\"Search group:artifact or version...\" style=\"padding:8px; width:260px; margin-right:8px;\">\n");
                    // Depth
                    html.append("          <label style=\"margin-right:6px;\">Depth</label><input id=\"dep-depth-").append(moduleId).append("\" type=\"number\" min=\"-1\" value=\"-1\" style=\"width:68px; padding:6px; margin-right:12px;\">\n");
                    // View select
                    html.append("          <label style=\"margin-right:6px;\">View</label><select id=\"dep-view-").append(moduleId).append("\" style=\"padding:8px; margin-right:12px;\">");
                    if (hasFlat) html.append("<option value=\"flat\" ").append("flat".equals(defaultView)?"selected":"").append(">Flat</option>");
                    if (hasTree) html.append("<option value=\"tree\" ").append("tree".equals(defaultView)?"selected":"").append(">Tree</option>");
                    html.append("</select>\n");
                    // CSV export
                    /*

                    html.append("          <button type=\"button\" onclick=\"exportCsv('"").append(moduleId).append("')\" style=\"padding:8px 12px; border-radius:6px; border:1px solid #e0e0e0; background:#f8f9fa; cursor:pointer;\">⬇️ CSV</button>\n");
                    */
                    /*

                    html.append("          <button type=\"button\" onclick=\"exportCsv('")
                        .append(moduleId)
                        .append("')\" style=\"padding:8px 12px; border-radius:6px; border:1px solid #e0e0e0; background:#f8f9fa; cursor:pointer;\">
d af f CSV</button>\\n");
                    */
                    /*
                    if (hasTree) {
                        html.append("          <button type=\"button\" onclick=\"expandAll('")
                            .append(moduleId)
                            .append("')\" style=\"padding:8px 12px; border-radius:6px; border:1px solid #e0e0e0; background:#f8f9fa; cursor:pointer;\">Expand all</button>\n");
                        html.append("          <button type=\"button\" onclick=\"collapseAll('")
                            .append(moduleId)
                            .append("')\" style=\"padding:8px 12px; border-radius:6px; border:1px solid #e0e0e0; background:#f8f9fa; cursor:pointer;\">Collapse all</button>\n");
                    }


                    html.append("          <button type=\"button\" onclick=\"exportCsv('")
                        .append(moduleId)
                        .append("')\" style=\"padding:8px 12px; border-radius:6px; border:1px solid #e0e0e0; background:#f8f9fa; cursor:pointer;\">
 f  CSV</button>\\n");
                    */
                    // CSV export (fixed)
                    html.append("          <button type=\"button\" onclick=\"exportCsv('")
                        .append(moduleId)
                        .append("')\" style=\"padding:8px 12px; border-radius:6px; border:1px solid #e0e0e0; background:#f8f9fa; cursor:pointer;\">CSV</button>\n");




                    // Scope filters
                    if (summary != null && summary.getScopes() != null && !summary.getScopes().isEmpty()) {
                        html.append("          <div style=\"margin-top:10px; display:flex; gap:12px; flex-wrap:wrap;\">\n");
                        for (var e : summary.getScopes().entrySet()) {
                            String scope = e.getKey();
                            int count = e.getValue() == null ? 0 : e.getValue();
                            String cbId = "dep-scope-" + moduleId + "-" + scope;
                            html.append("            <label for=\"").append(cbId).append("\" style=\"user-select:none;\">");
                            html.append("<input type=\"checkbox\" id=\"").append(cbId).append("\" data-scope-check=\"").append(moduleId).append("\" value=\"").append(scope).append("\" checked style=\"margin-right:6px;\">");
                            html.append(escapeHtml(scope)).append(" (<strong>").append(count).append("</strong>)");
                            html.append("</label>\n");
                        }
                        html.append("          </div>\n");
                    }
                    html.append("        </div>\n");

                    // Flat table
                    if (hasFlat) {
                        html.append("        <div id=\"dep-flat-").append(moduleId).append("\" class=\"table-container\" style=\"");
                        if (!"flat".equals(defaultView)) html.append("display:none;");
                        html.append("\">\n");
                        html.append("          <table id=\"dep-table-").append(moduleId).append("\">\n");
                        html.append("            <tr><th>Group</th><th>Artifact</th><th>Version</th><th>Scope</th><th>Type</th><th>Optional</th><th>Depth</th></tr>\n");
                        for (var d : deps.getFlat()) {
                            String ga = (d.getGroupId()==null?"":d.getGroupId()) + ":" + (d.getArtifactId()==null?"":d.getArtifactId());
                            String scope = d.getScope()==null?"":d.getScope();
                            String version = d.getVersion()==null?"":d.getVersion();
                            String type = d.getType()==null?"":d.getType();
                            String optional = d.isOptional()?"true":"false";
                            int depth = d.getDepth()==null?1:d.getDepth();
                            html.append("            <tr class=\"dep-row\" data-module=\"").append(moduleId)
                                .append("\" data-ga=\"").append(escapeHtml(ga))
                                .append("\" data-scope=\"").append(escapeHtml(scope))
                                .append("\" data-version=\"").append(escapeHtml(version))
                                .append("\" data-type=\"").append(escapeHtml(type))
                                .append("\" data-optional=\"").append(optional)
                                .append("\" data-depth=\"").append(String.valueOf(depth)).append("\">\n");
                            html.append("              <td>").append(escapeHtml(d.getGroupId())).append("</td>\n");
                            html.append("              <td><strong>").append(escapeHtml(d.getArtifactId())).append("</strong></td>\n");
                            html.append("              <td><code>").append(escapeHtml(version)).append("</code></td>\n");
                            html.append("              <td>").append(escapeHtml(scope)).append("</td>\n");
                            html.append("              <td>").append(escapeHtml(type)).append("</td>\n");
                            html.append("              <td>").append(d.isOptional()?"✅":"-").append("</td>\n");
                            html.append("              <td>").append(String.valueOf(depth)).append("</td>\n");
                            html.append("            </tr>\n");
                        }
                        html.append("          </table>\n");
                        html.append("        </div>\n");

                        // Duplicates area
                        html.append("        <div id=\"dep-dupes-").append(moduleId).append("\" style=\"font-size:0.95em; color:#555; margin-top:6px;\"></div>\n");
                    }

                    // Tree view (top-level only for now)
                    if (hasTree) {
                        html.append("        <div id='dep-tree-").append(moduleId).append("' class='dep-tree' style='");
                        if (!"tree".equals(defaultView)) html.append("display:none;");
                        html.append("\">\n");
                        html.append("          <ul style=\"padding-left:18px;\">\n");
                        for (var n : deps.getTree()) {
                            appendTreeNodeHtml(html, n, moduleId, 1);
                        }
                        html.append("          </ul>\n");
                        html.append("        </div>\n");
                    }

                    // Register for JS init
                    html.append("        <script>window.DEP_SECTIONS = window.DEP_SECTIONS || []; window.DEP_SECTIONS.push('")
                        .append(moduleId).append("');</script>\n");
                }

                html.append("      </div>\n");
            });
        } else {
            html.append("      <div class=\"empty-state\">\n");
            html.append("        <div class=\"empty-state-icon\">📦</div>\n");
            html.append("        <p>No deployable modules found</p>\n");
            html.append("      </div>\n");

        }
        html.append("    </div>\n");
        // Tab 3b: Dependencies (per deployable module)
        html.append("    <div id=\"dependencies\" class=\"tab-content\">\n");
        if (descriptor.deployableModules() != null && !descriptor.deployableModules().isEmpty()) {
            descriptor.deployableModules().forEach(module -> {
                if (module.getDependencies() != null) {
                    var deps = module.getDependencies();
                    var summary = deps.getSummary();
                    String moduleId = module.getArtifactId().replaceAll("[^A-Za-z0-9_-]", "_");

                    html.append("      <div class=\"module-card\">\n");
                    html.append("        <div class=\"module-header\">\n");
                    html.append("          <div class=\"module-title\">");
                    html.append("🧩 Dependencies — ").append(escapeHtml(module.getArtifactId()));
                    html.append("</div>\n");
                    html.append("          <div class=\"module-badges\">\n");
                    html.append("            <span class=\"badge badge-").append(module.getPackaging()).append("\">")
                        .append(module.getPackaging().toUpperCase()).append("</span>\n");
                    html.append("          </div>\n");
                    html.append("        </div>\n");

                    // Summary cards
                    html.append("        <div class=\"info-grid\">\n");
                    if (summary != null) {
                        html.append("          <div class=\"info-item\">\n");
                        html.append("            <div class=\"info-label\">Total</div>\n");
                        html.append("            <div class=\"info-value\"><strong>").append(summary.getTotal()).append("</strong></div>\n");
                        html.append("          </div>\n");
                        html.append("          <div class=\"info-item\">\n");
                        html.append("            <div class=\"info-label\">Direct</div>\n");
                        html.append("            <div class=\"info-value\"><strong>").append(summary.getDirect()).append("</strong></div>\n");
                        html.append("          </div>\n");
                        html.append("          <div class=\"info-item\">\n");
                        html.append("            <div class=\"info-label\">Transitive</div>\n");
                        html.append("            <div class=\"info-value\"><strong>").append(summary.getTransitive()).append("</strong></div>\n");
                        html.append("          </div>\n");
                        html.append("          <div class=\"info-item\">\n");
                        html.append("            <div class=\"info-label\">Optional</div>\n");
                        html.append("            <div class=\"info-value\"><strong>").append(summary.getOptional()).append("</strong></div>\n");
                        html.append("          </div>\n");
                    }
                    html.append("        </div>\n");

                    // Controls
                    boolean hasFlat = deps.getFlat() != null && !deps.getFlat().isEmpty();
                    boolean hasTree = deps.getTree() != null && !deps.getTree().isEmpty();
                    String defaultView = hasFlat ? "flat" : (hasTree ? "tree" : "flat");

                    html.append("        <div id=\"dep-section-").append(moduleId).append("\" style=\"margin: 10px 0 5px 0;\">\n");
                    // Search
                    html.append("          <input id=\"dep-search-").append(moduleId).append("\" type=\"text\" placeholder=\"Search group:artifact or version...\" style=\"padding:8px; width:260px; margin-right:8px;\">");
                    html.append("          <button id=\"dep-prev-").append(moduleId).append("\" type=\"button\" onclick=\"depPrev('").append(moduleId).append("')\" style=\"padding:6px 10px; border-radius:6px; border:1px solid #e0e0e0; background:#f8f9fa; cursor:pointer; margin-right:4px;\">⟨ Prev</button>");
                    html.append("          <button id=\"dep-next-").append(moduleId).append("\" type=\"button\" onclick=\"depNext('").append(moduleId).append("')\" style=\"padding:6px 10px; border-radius:6px; border:1px solid #e0e0e0; background:#f8f9fa; cursor:pointer; margin-right:8px;\">Next ⟩</button>");
                    html.append("          <span id=\"dep-count-").append(moduleId).append("\" style=\"color:#666; margin-right:12px;\"></span>\n");
                    // Depth
                    html.append("          <label style=\"margin-right:6px;\">Depth</label><input id=\"dep-depth-").append(moduleId).append("\" type=\"number\" min=\"-1\" value=\"-1\" style=\"width:68px; padding:6px; margin-right:12px;\">\n");
                    // View select
                    html.append("          <label style=\"margin-right:6px;\">View</label><select id=\"dep-view-").append(moduleId).append("\" style=\"padding:8px; margin-right:12px;\">");
                    if (hasFlat) html.append("<option value=\"flat\" ").append("flat".equals(defaultView)?"selected":"").append(">Flat</option>");
                    if (hasTree) html.append("<option value=\"tree\" ").append("tree".equals(defaultView)?"selected":"").append(">Tree</option>");
                    html.append("</select>\n");
                    // Expand/Collapse for tree
                    if (hasTree) {
                        html.append("          <button type=\"button\" onclick=\"expandAll('")
                            .append(moduleId)
                            .append("')\" style=\"padding:8px 12px; border-radius:6px; border:1px solid #e0e0e0; background:#f8f9fa; cursor:pointer;\">Expand all</button>\n");
                        html.append("          <button type=\"button\" onclick=\"collapseAll('")
                            .append(moduleId)
                            .append("')\" style=\"padding:8px 12px; border-radius:6px; border:1px solid #e0e0e0; background:#f8f9fa; cursor:pointer;\">Collapse all</button>\n");
                    }
                    // Quick filters (families)
                    html.append("          <div class=\"quick-filters\" id=\"dep-quick-").append(moduleId).append("\">\n");
                    html.append("            <span style=\"opacity:0.8;\">Quick filters:</span>\n");
                    html.append("            <button type=\"button\" class=\"filter-chip\" data-prefix=\"org.springframework\" onclick=\"toggleQuickFilterFromBtn(this,'org.springframework')\">Spring</button>\n");
                    html.append("            <button type=\"button\" class=\"filter-chip\" data-prefix=\"com.fasterxml.jackson\" onclick=\"toggleQuickFilterFromBtn(this,'com.fasterxml.jackson')\">Jackson</button>\n");
                    html.append("            <button type=\"button\" class=\"filter-chip\" data-prefix=\"org.hibernate\" onclick=\"toggleQuickFilterFromBtn(this,'org.hibernate')\">Hibernate</button>\n");
                    html.append("            <button type=\"button\" class=\"filter-chip\" data-prefix=\"io.quarkus\" onclick=\"toggleQuickFilterFromBtn(this,'io.quarkus')\">Quarkus</button>\n");
                    html.append("            <button type=\"button\" class=\"filter-chip\" data-prefix=\"io.micronaut\" onclick=\"toggleQuickFilterFromBtn(this,'io.micronaut')\">Micronaut</button>\n");
                    html.append("            <button type=\"button\" class=\"filter-chip\" data-prefix=\"org.apache.commons\" onclick=\"toggleQuickFilterFromBtn(this,'org.apache.commons')\">Apache Commons</button>\n");
                    html.append("            <button type=\"button\" class=\"filter-chip\" data-prefix=\"com.google.guava\" onclick=\"toggleQuickFilterFromBtn(this,'com.google.guava')\">Guava</button>\n");
                    html.append("            <button type=\"button\" class=\"filter-chip\" data-prefix=\"io.netty\" onclick=\"toggleQuickFilterFromBtn(this,'io.netty')\">Netty</button>\n");
                    html.append("            <button type=\"button\" class=\"filter-chip\" data-prefix=\"org.slf4j\" onclick=\"toggleQuickFilterFromBtn(this,'org.slf4j')\">SLF4J</button>\n");
                    html.append("            <button type=\"button\" class=\"filter-chip\" data-prefix=\"ch.qos.logback\" onclick=\"toggleQuickFilterFromBtn(this,'ch.qos.logback')\">Logback</button>\n");
                    html.append("            <button type=\"button\" class=\"filter-chip\" data-prefix=\"junit\" onclick=\"toggleQuickFilterFromBtn(this,'junit')\">JUnit</button>\n");
                    html.append("            <button type=\"button\" class=\"filter-chip clear\" onclick=\"clearQuickFiltersFromBtn(this)\">Clear</button>\n");
                    html.append("          </div>\n");
                    // CSV export
                    html.append("          <button type=\"button\" onclick=\"exportCsv('")
                        .append(moduleId)
                        .append("')\" style=\"padding:8px 12px; border-radius:6px; border:1px solid #e0e0e0; background:#f8f9fa; cursor:pointer;\">CSV</button>\n");

                    // Scope filters
                    if (summary != null && summary.getScopes() != null && !summary.getScopes().isEmpty()) {
                        html.append("          <div style=\"margin-top:10px; display:flex; gap:12px; flex-wrap:wrap;\">\n");
                        for (var e : summary.getScopes().entrySet()) {
                            String scope = e.getKey();
                            int count = e.getValue() == null ? 0 : e.getValue();
                            String cbId = "dep-scope-" + moduleId + "-" + scope;
                            html.append("            <label for=\"").append(cbId).append("\" style=\"user-select:none;\">");
                            html.append("<input type=\"checkbox\" id=\"").append(cbId).append("\" data-scope-check=\"").append(moduleId).append("\" value=\"").append(scope).append("\" checked style=\"margin-right:6px;\">");
                            html.append(escapeHtml(scope)).append(" (<strong>").append(count).append("</strong>)");
                            html.append("</label>\n");
                        }
                        html.append("          </div>\n");
                    }
                    html.append("        </div>\n");

                    // Flat table
                    if (hasFlat) {
                        html.append("        <div id=\"dep-flat-").append(moduleId).append("\" class=\"table-container\" style=\"");
                        if (!"flat".equals(defaultView)) html.append("display:none;");
                        html.append("\">\n");
                        html.append("          <table id=\"dep-table-").append(moduleId).append("\">\n");
                        html.append("            <tr><th>Group</th><th>Artifact</th><th>Version</th><th>Scope</th><th>Type</th><th>Optional</th><th>Depth</th></tr>\n");
                        for (var d : deps.getFlat()) {
                            String ga = (d.getGroupId()==null?"":d.getGroupId()) + ":" + (d.getArtifactId()==null?"":d.getArtifactId());
                            String scope = d.getScope()==null?"":d.getScope();
                            String version = d.getVersion()==null?"":d.getVersion();
                            String type = d.getType()==null?"":d.getType();
                            String optional = d.isOptional()?"true":"false";
                            int depth = d.getDepth()==null?1:d.getDepth();
                            html.append("            <tr class=\"dep-row\" data-module=\"").append(moduleId)
                                .append("\" data-ga=\"").append(escapeHtml(ga))
                                .append("\" data-scope=\"").append(escapeHtml(scope))
                                .append("\" data-version=\"").append(escapeHtml(version))
                                .append("\" data-type=\"").append(escapeHtml(type))
                                .append("\" data-optional=\"").append(optional)
                                .append("\" data-depth=\"").append(String.valueOf(depth)).append("\">\n");
                            html.append("              <td>").append(escapeHtml(d.getGroupId())).append("</td>\n");
                            html.append("              <td><strong>").append(escapeHtml(d.getArtifactId())).append("</strong></td>\n");
                            html.append("              <td><code>").append(escapeHtml(version)).append("</code></td>\n");
                            html.append("              <td>").append(escapeHtml(scope)).append("</td>\n");
                            html.append("              <td>").append(escapeHtml(type)).append("</td>\n");
                            html.append("              <td>").append(d.isOptional()?"✅":"-").append("</td>\n");
                            html.append("              <td>").append(String.valueOf(depth)).append("</td>\n");
                            html.append("            </tr>\n");
                        }
                        html.append("          </table>\n");
                        html.append("        </div>\n");
                        // Duplicates area
                        html.append("        <div id=\"dep-dupes-").append(moduleId).append("\" style=\"font-size:0.95em; color:#555; margin-top:6px;\"></div>\n");
                    }

                    // Tree view
                    if (hasTree) {
                        html.append("        <div id='dep-tree-").append(moduleId).append("' class='dep-tree' style='");
                        if (!"tree".equals(defaultView)) html.append("display:none;");
                        html.append("'>\n");
                        html.append("          <ul style=\"padding-left:18px;\">\n");
                        for (var n : deps.getTree()) {
                            appendTreeNodeHtml(html, n, moduleId, 1);
                        }
                        html.append("          </ul>\n");
                        html.append("        </div>\n");
                    }

                    // Register for JS init
                    html.append("        <script>window.DEP_SECTIONS = window.DEP_SECTIONS || []; window.DEP_SECTIONS.push('")
                        .append(moduleId).append("');</script>\n");

                    html.append("      </div>\n"); // module-card end
                }
            });
        } else {
            html.append("      <div class=\"empty-state\">\n");
            html.append("        <div class=\"empty-state-icon\">📦</div>\n");
            html.append("        <p>No deployable modules found</p>\n");
            html.append("      </div>\n");
        }
        html.append("    </div>\n");


        // Tab 4: Compliance (Licenses)
        html.append("    <div id=\"compliance\" class=\"tab-content\">\n");
        if (descriptor.deployableModules() != null && !descriptor.deployableModules().isEmpty()) {
            boolean hasLic = descriptor.deployableModules().stream()
                .anyMatch(m -> m.getLicenses() != null);
            if (hasLic) {
                descriptor.deployableModules().forEach(module -> {
                    var lic = module.getLicenses();
                    if (lic != null) {
                        String moduleId = module.getArtifactId().replaceAll("[^A-Za-z0-9_-]", "_");
                        html.append("      <div class=\"module-card\" id=\"comp-card-").append(moduleId).append("\">\n");
                        html.append("        <div class=\"module-header\">\n");
                        html.append("          <div class=\"module-title\">");
                        html.append("⚖️ Compliance — ").append(escapeHtml(module.getArtifactId()));
                        html.append("</div>\n");
                        html.append("        </div>\n");
                        // Controls: Expand/Collapse all for this module's compliance section
                        html.append("        <div class=\"compliance-controls\" style=\"margin:8px 0; display:flex; gap:8px; align-items:center; flex-wrap:wrap;\">\n");
                        html.append("          <button type=\"button\" onclick=\"toggleCompliance('").append(moduleId).append("', true)\" style=\"padding:6px 10px; border-radius:6px; border:1px solid #e0e0e0; background:#f8f9fa; cursor:pointer;\">Expand all</button>\n");
                        html.append("          <button type=\"button\" onclick=\"toggleCompliance('").append(moduleId).append("', false)\" style=\"padding:6px 10px; border-radius:6px; border:1px solid #e0e0e0; background:#f8f9fa; cursor:pointer;\">Collapse all</button>\n");
                        html.append("          <input type=\"search\" id=\"comp-filter-").append(moduleId).append("\" oninput=\"filterCompliance('").append(moduleId).append("')\" placeholder=\"Filter by groupId or artifactId\" style=\"padding:6px 10px; border-radius:6px; border:1px solid #e0e0e0; min-width:260px;\" />\n");
                        html.append("        </div>\n");

                        // Summary
                        var sum = lic.getSummary();
                        if (sum != null) {
                            html.append("        <div class=\"info-grid\">\n");
                            html.append("          <div class=\"info-item\"><div class=\"info-label\">Total</div><div class=\"info-value\"><strong>")
                               .append(sum.getTotal()).append("</strong></div></div>\n");
                            html.append("          <div class=\"info-item\"><div class=\"info-label\">Identified</div><div class=\"info-value\"><strong>")
                               .append(sum.getIdentified()).append("</strong></div></div>\n");
                            html.append("          <div class=\"info-item\"><div class=\"info-label\">Unknown</div><div class=\"info-value\"><strong>")
                               .append(sum.getUnknown()).append("</strong></div></div>\n");
                            html.append("        </div>\n");

                                // Distribution + Filters
                                if (sum != null && sum.getByType() != null && !sum.getByType().isEmpty()) {
                                    String modId = String.valueOf(module.getArtifactId());
                                    html.append("        <div style=\"display:flex;gap:24px;align-items:flex-start;margin:6px 0 14px 0;\">\n");
                                    html.append("          <div>\n");
                                    html.append("            <canvas id=\"lic-pie-").append(escapeHtml(modId)).append("\" width=\"220\" height=\"220\" style=\"border-radius:10px\"></canvas>\n");
                                    html.append("          </div>\n");
                                    html.append("          <div>\n");
                                    html.append("            <div style=\"display:flex;gap:8px;align-items:center;margin-bottom:10px;\">\n");
                                    html.append("              <input type=\"text\" id=\"lic-search-").append(escapeHtml(modId)).append("\" placeholder=\"Search artifact or license\" />\n");
                                    html.append("              <select id=\"lic-type-").append(escapeHtml(modId)).append("\">\n");
                                    html.append("                <option value=\"\">All licenses</option>\n");
                                    for (var e : sum.getByType().entrySet()) {
                                        html.append("                <option value=\"").append(escapeHtml(String.valueOf(e.getKey()))).append("\">")
                                            .append(escapeHtml(String.valueOf(e.getKey()))).append(" (").append(String.valueOf(e.getValue())).append(")</option>\n");
                                    }
                                    html.append("              </select>\n");
                                    html.append("            </div>\n");
                                    html.append("            <div class=\"legend\">\n");
                                    for (var e : sum.getByType().entrySet()) {
                                        html.append("              <div style=\"display:flex;align-items:center;gap:6px;margin:2px 0;\"><span class=\"swatch\" data-lic=\"")
                                            .append(escapeHtml(String.valueOf(e.getKey()))).append("\" style=\"display:inline-block;width:12px;height:12px;border-radius:2px;\"></span> ")
                                            .append(escapeHtml(String.valueOf(e.getKey()))).append(" <small>(").append(String.valueOf(e.getValue())).append(")</small></div>\n");
                                    }
                                    html.append("            </div>\n");
                                    html.append("          </div>\n");
                                    html.append("        </div>\n");

                                    // inline JS for pie + filters + sortTable helper
                                    StringBuilder labels = new StringBuilder();
                                    StringBuilder values = new StringBuilder();
                                    int idx = 0; int size = sum.getByType().size();
                                    for (var e : sum.getByType().entrySet()) {
                                        labels.append('\"').append(e.getKey()).append('\"');
                                        values.append(e.getValue());
                                        if (++idx < size) { labels.append(","); values.append(","); }
                                    }
                                    html.append("<script>(function(){\n");
                                    html.append("var labels = [").append(labels).append("]; var values=[").append(values).append("];\n");
                                    html.append("var id='" ).append(escapeHtml(modId)).append("';\n");
                                    html.append("var canvas=document.getElementById('lic-pie-'+id); if(canvas){var ctx=canvas.getContext('2d');var total=values.reduce((a,b)=>a+b,0);var start=0;var cx=110,cy=110,r=100;var colors=labels.map((_,i)=>`hsl(${(i*53)%360},70%,55%)`);values.forEach((v,i)=>{var ang=2*Math.PI*(v/Math.max(total,1));ctx.beginPath();ctx.moveTo(cx,cy);ctx.arc(cx,cy,r,start,start+ang);ctx.closePath();ctx.fillStyle=colors[i];ctx.fill();start+=ang;});document.querySelectorAll('.swatch[data-lic]').forEach((el,i)=>{el.style.backgroundColor=colors[i]||'#888';});}\n");
                                    html.append("function filter(){var q=(document.getElementById('lic-search-'+id)||{}).value||'';q=q.toLowerCase();var t=(document.getElementById('lic-type-'+id)||{}).value||'';var table=document.getElementById('lic-table-'+id);if(!table) return;Array.from(table.querySelectorAll('tbody tr')).forEach(tr=>{var lic=(tr.getAttribute('data-license')||'');var art=(tr.getAttribute('data-artifact')||'');var okT=!t||lic.includes(t);var okQ=lic.toLowerCase().includes(q)||art.toLowerCase().includes(q);tr.style.display=(okT&&okQ)?'':'none';});}\n");
                                    html.append("var s=document.getElementById('lic-search-'+id); if(s){s.addEventListener('input', filter);} var sel=document.getElementById('lic-type-'+id); if(sel){sel.addEventListener('change', filter);}\n");
                                    html.append("window.sortTable=function(tableId,col){var table=document.getElementById(tableId);if(!table)return;var rows=Array.from(table.querySelectorAll('tbody tr'));var asc=table.getAttribute('data-sort')!=='asc';rows.sort((a,b)=>{var ta=a.children[col].innerText.toLowerCase();var tb=b.children[col].innerText.toLowerCase();return asc?ta.localeCompare(tb):tb.localeCompare(ta);});var tbody=table.querySelector('tbody');rows.forEach(r=>tbody.appendChild(r));table.setAttribute('data-sort',asc?'asc':'desc');}\n");
                                    html.append("})();</script>\n");
                                }

                        }

                        // Warnings
                        if (lic.getWarnings() != null && !lic.getWarnings().isEmpty()) {
                            html.append("        <div class=\"table-container\">\n");
                            html.append("          <details class=\"collapsible\">\n");
                            html.append("            <summary>⚠️ Warnings (").append(lic.getWarnings().size()).append(")</summary>\n");
                            html.append("            <div style=\"margin-top:8px\">\n");
                            html.append("              <table id=\"warn-table-").append(moduleId).append("\">\n");
                            html.append("                <tr><th>Severity</th><th>Artifact</th><th>License</th><th>Reason</th><th>Recommendation</th></tr>\n");
                            for (var w : lic.getWarnings()) {
                                html.append("                <tr>\n");
                                html.append("                  <td>").append(escapeHtml(String.valueOf(w.getSeverity()))).append("</td>\n");
                                html.append("                  <td>").append(escapeHtml(String.valueOf(w.getArtifact()))).append("</td>\n");
                                html.append("                  <td>").append(escapeHtml(String.valueOf(w.getLicense()))).append("</td>\n");
                                html.append("                  <td>").append(escapeHtml(String.valueOf(w.getReason()))).append("</td>\n");
                                html.append("                  <td>").append(escapeHtml(String.valueOf(w.getRecommendation()))).append("</td>\n");
                                html.append("                </tr>\n");
                            }
                            html.append("              </table>\n");
                            html.append("            </div>\n");
                            html.append("          </details>\n");
                            html.append("        </div>\n");
                        }

                        // Details
                        if (lic.getDetails() != null && !lic.getDetails().isEmpty()) {
                            html.append("        <div class=\"table-container\">\n");
                            html.append("          <details class=\"collapsible\">\n");
                            html.append("            <summary>📄 License Details (").append(lic.getDetails().size()).append(")</summary>\n");
                            html.append("            <div style=\"margin-top:8px\">\n");
                            html.append("              <table id=\"lic-table-").append(escapeHtml(String.valueOf(module.getArtifactId()))).append("\">\n");
                            html.append("            <thead><tr><th>Group</th><th onclick=\"sortTable('lic-table-").append(escapeHtml(String.valueOf(module.getArtifactId()))).append("',1)\">Artifact</th><th>Version</th><th>Scope</th><th onclick=\"sortTable('lic-table-").append(escapeHtml(String.valueOf(module.getArtifactId()))).append("',4)\">License</th><th>URL</th><th>Depth</th></tr></thead>\n");
                            html.append("            <tbody>\n");
                            for (var d : lic.getDetails()) {
                                html.append("            <tr data-license=\"").append(escapeHtml(String.valueOf(d.getLicense()))).append("\" data-artifact=\"").append(escapeHtml(String.valueOf(d.getArtifactId()))).append("\">\n");
                                html.append("              <td>").append(escapeHtml(String.valueOf(d.getGroupId()))).append("</td>\n");
                                html.append("              <td><strong>").append(escapeHtml(String.valueOf(d.getArtifactId()))).append("</strong></td>\n");
                                html.append("              <td><code>").append(escapeHtml(String.valueOf(d.getVersion()))).append("</code></td>\n");
                                html.append("              <td>").append(escapeHtml(String.valueOf(d.getScope()))).append("</td>\n");
                                html.append("              <td>").append(escapeHtml(String.valueOf(d.getLicense()))).append("</td>\n");
                                String url = d.getLicenseUrl()==null?"":d.getLicenseUrl();
                                if (url.isBlank()) {
                                    html.append("              <td>-</td>\n");
                                } else {
                                    html.append("              <td><a href=\"").append(escapeHtml(url)).append("\" target=\"_blank\">link</a></td>\n");
                                }
                                html.append("              <td>").append(String.valueOf(d.getDepth()==null?1:d.getDepth())).append("</td>\n");
                                html.append("            </tr>\n");
                            }
                            html.append("            </tbody>\n");

                            html.append("              </table>\n");
                            html.append("            </div>\n");
                            html.append("          </details>\n");
                            html.append("        </div>\n");
                        }
                        html.append("      </div>\n");
                    }
                });
            } else {
                html.append("      <div class=\"empty-state\">\n");
                html.append("        <div class=\"empty-state-icon\">⚖️</div>\n");
                html.append("        <p>No license information collected. Enable with -Ddescriptor.includeLicenses=true</p>\n");
                html.append("      </div>\n");
            }
        } else {
            html.append("      <div class=\"empty-state\">\n");
            html.append("        <div class=\"empty-state-icon\">📦</div>\n");
            html.append("        <p>No deployable modules found</p>\n");
            html.append("      </div>\n");
        }
        html.append("    </div>\n");


        // Tab 4: Environments
        html.append("    <div id=\"environments\" class=\"tab-content\">\n");
        if (descriptor.deployableModules() != null && !descriptor.deployableModules().isEmpty()) {
            boolean hasEnvironments = descriptor.deployableModules().stream()
                .anyMatch(m -> m.getEnvironments() != null && !m.getEnvironments().isEmpty());

            if (hasEnvironments) {
                descriptor.deployableModules().forEach(module -> {
                    if (module.getEnvironments() != null && !module.getEnvironments().isEmpty()) {
                        html.append("      <div class=\"module-card\">\n");
                        html.append("        <div class=\"module-header\">\n");
                        html.append("          <div class=\"module-title\">🌍 ").append(escapeHtml(module.getArtifactId())).append("</div>\n");
                        html.append("        </div>\n");
                        html.append("        <div class=\"table-container\">\n");
                        html.append("          <table>\n");
                        html.append("            <tr><th>Profile</th><th>Port</th><th>Context Path</th><th>Actuator</th><th>Health Endpoint</th><th>Info Endpoint</th></tr>\n");
                        module.getEnvironments().forEach(env -> {
                            html.append("            <tr>\n");
                            html.append("              <td><strong>").append(escapeHtml(env.profile())).append("</strong></td>\n");
                            html.append("              <td>").append(env.serverPort() != null ? "🔌 " + env.serverPort() : "-").append("</td>\n");
                            html.append("              <td>").append(env.contextPath() != null ? "<code>" + escapeHtml(env.contextPath()) + "</code>" : "-").append("</td>\n");
                            html.append("              <td>").append(env.actuatorEnabled() != null && env.actuatorEnabled() ? "✅ Enabled" : "❌ Disabled").append("</td>\n");
                            html.append("              <td>").append(env.actuatorHealthPath() != null ? "<code>" + escapeHtml(env.actuatorHealthPath()) + "</code>" : "-").append("</td>\n");
                            html.append("              <td>").append(env.actuatorInfoPath() != null ? "<code>" + escapeHtml(env.actuatorInfoPath()) + "</code>" : "-").append("</td>\n");
                            html.append("            </tr>\n");
                        });
                        html.append("          </table>\n");
                        html.append("        </div>\n");
                        html.append("      </div>\n");
                    }
                });
            } else {
                html.append("      <div class=\"empty-state\">\n");
                html.append("        <div class=\"empty-state-icon\">🌍</div>\n");
                html.append("        <p>No environment configurations found</p>\n");
                html.append("      </div>\n");
            }
        } else {
            html.append("      <div class=\"empty-state\">\n");
            html.append("        <div class=\"empty-state-icon\">🌍</div>\n");
            html.append("        <p>No deployable modules found</p>\n");
            html.append("      </div>\n");
        }
        html.append("    </div>\n");

        // Tab 5: Assemblies
        html.append("    <div id=\"assemblies\" class=\"tab-content\">\n");
        if (descriptor.deployableModules() != null && !descriptor.deployableModules().isEmpty()) {
            boolean hasAssemblies = descriptor.deployableModules().stream()
                .anyMatch(m -> m.getAssemblyArtifacts() != null && !m.getAssemblyArtifacts().isEmpty());

            if (hasAssemblies) {
                descriptor.deployableModules().forEach(module -> {
                    if (module.getAssemblyArtifacts() != null && !module.getAssemblyArtifacts().isEmpty()) {
                        html.append("      <div class=\"module-card\">\n");
                        html.append("        <div class=\"module-header\">\n");
                        html.append("          <div class=\"module-title\">📚 ").append(escapeHtml(module.getArtifactId())).append("</div>\n");
                        html.append("          <div class=\"module-badges\">\n");
                        html.append("            <span class=\"badge badge-jar\">").append(module.getAssemblyArtifacts().size()).append(" assemblies</span>\n");
                        html.append("          </div>\n");
                        html.append("        </div>\n");
                        html.append("        <div class=\"table-container\">\n");
                        html.append("          <table>\n");
                        html.append("            <tr><th>Assembly ID</th><th>Format</th><th>Repository Path</th><th>Maven Repository</th></tr>\n");
                        module.getAssemblyArtifacts().forEach(assembly -> {
                            html.append("            <tr>\n");
                            html.append("              <td><strong>").append(escapeHtml(assembly.assemblyId())).append("</strong></td>\n");
                            html.append("              <td><span class=\"badge badge-war\">").append(escapeHtml(assembly.format().toUpperCase())).append("</span></td>\n");
                            html.append("              <td><code>").append(escapeHtml(assembly.repositoryPath())).append("</code></td>\n");
                            html.append("              <td>");
                            if (assembly.repositoryUrl() != null) {
                                html.append("<a href=\"").append(escapeHtml(assembly.repositoryUrl())).append("\" target=\"_blank\" class=\"repo-link\">🔗 Download</a>");
                            } else {
                                html.append("-");
                            }
                            html.append("</td>\n");
                            html.append("            </tr>\n");
                        });
                        html.append("          </table>\n");
                        html.append("        </div>\n");
                        html.append("      </div>\n");
                    }
                });
            } else {
                html.append("      <div class=\"empty-state\">\n");
                html.append("        <div class=\"empty-state-icon\">📚</div>\n");
                html.append("        <p>No assembly artifacts found</p>\n");
                html.append("      </div>\n");
            }
        } else {
            html.append("      <div class=\"empty-state\">\n");
            html.append("        <div class=\"empty-state-icon\">📚</div>\n");
            html.append("        <p>No deployable modules found</p>\n");
            html.append("      </div>\n");
        }
        html.append("    </div>\n");

        // JavaScript for tab navigation and theme toggle
        html.append("  </div>\n");
        html.append("  <script>\n");
        html.append("    // Tab navigation\n");
        html.append("    function showTab(btn, tabName) {\n");
        html.append("      // Hide all tab contents\n");
        html.append("      const contents = document.querySelectorAll('.tab-content');\n");
        html.append("      contents.forEach(content => content.classList.remove('active'));\n");
        html.append("      \n");
        html.append("      // Remove active class from all tabs\n");
        html.append("      const tabs = document.querySelectorAll('.tab');\n");
        html.append("      tabs.forEach(tab => tab.classList.remove('active'));\n");

        html.append("      \n");
        html.append("      // Show selected tab content\n");
        html.append("      document.getElementById(tabName).classList.add('active');\n");
        html.append("      \n");
        html.append("      // Add active class to clicked tab\n");
        html.append("      btn.classList.add('active');\n");
        html.append("    }\n");
        html.append("    \n");
        html.append("    // Theme toggle\n");
        html.append("    function toggleTheme() {\n");
        html.append("      const body = document.body;\n");
        html.append("      const themeIcon = document.querySelector('.theme-icon');\n");
        html.append("      \n");
        html.append("      body.classList.toggle('dark-mode');\n");
        html.append("      \n");
        html.append("      // Update icon\n");
        html.append("      if (body.classList.contains('dark-mode')) {\n");
        html.append("        themeIcon.textContent = '☀️';\n");
        html.append("        localStorage.setItem('theme', 'dark');\n");
        html.append("      } else {\n");
        html.append("        themeIcon.textContent = '🌙';\n");
        html.append("        localStorage.setItem('theme', 'light');\n");
        html.append("      }\n");
        html.append("    }\n");
        html.append("    \n");
        html.append("    // Load saved theme on page load\n");
    // Dependencies UI helpers
    html.append("    // Dependencies UI\n");
    html.append("    function byId(id){ return document.getElementById(id); }\n");
    html.append("    function setDepView(modId){ const sel=byId('dep-view-'+modId); if(!sel) return; const v=sel.value; const flat=byId('dep-flat-'+modId); const tree=byId('dep-tree-'+modId); if(flat) flat.style.display=(v==='flat')?'':'none'; if(tree) tree.style.display=(v==='tree')?'':'none'; }\n");
    html.append("    function toggleTreeNode(el){ const li=el.closest('.dep-node'); if(!li) return; const c=li.classList.toggle('collapsed'); el.textContent=c?'\u25b8':'\u25be'; }\n");
    html.append("    function expandAll(modId){ document.querySelectorAll('#dep-tree-'+modId+' .dep-node.has-children').forEach(li=>{ li.classList.remove('collapsed'); const t=li.querySelector(':scope > .tree-toggle'); if(t) t.textContent='\u25be'; }); }\n");
    html.append("    function collapseAll(modId){ document.querySelectorAll('#dep-tree-'+modId+' .dep-node.has-children').forEach(li=>{ li.classList.add('collapsed'); const t=li.querySelector(':scope > .tree-toggle'); if(t) t.textContent='\u25b8'; }); }\n");
    html.append("    function initTreeCollapse(modId){ document.querySelectorAll('#dep-tree-'+modId+' .dep-node.has-children').forEach(li=>{ const depth=parseInt(li.dataset.depth||'1',10); const t=li.querySelector(':scope > .tree-toggle'); if(depth>1){ li.classList.add('collapsed'); if(t) t.textContent='\u25b8'; } else { if(t) t.textContent='\u25be'; } }); }\n");

    html.append("    function toggleCompliance(modId, open){ const root=document.getElementById('comp-card-'+modId); const list=(root?root.querySelectorAll('details.collapsible'):document.querySelectorAll('#compliance details.collapsible')); list.forEach(d=>d.open=!!open); }\n");
    html.append("    function filterCompliance(modId){ const root=document.getElementById('comp-card-'+modId); if(!root) return; const input=document.getElementById('comp-filter-'+modId); const term=(input&&input.value?input.value:'').trim().toLowerCase(); const matches=(s)=>!term||(s&&s.toLowerCase().includes(term)); const w=root.querySelector('#warn-table-'+modId); if(w){ w.querySelectorAll('tbody tr').forEach(tr=>{ const tds=tr.querySelectorAll('td'); const art=(tds&&tds[1])?tds[1].textContent:''; tr.style.display=matches(art)?'':'none'; }); } root.querySelectorAll('table[id^=\"lic-table-\"] tbody tr').forEach(tr=>{ const g=tr.querySelector('td:nth-child(1)'); const a=tr.querySelector('td:nth-child(2)'); const gs=g?g.textContent:''; const as=a?a.textContent:''; tr.style.display=(matches(gs)||matches(as))?'':'none'; }); }\n");

    html.append("    window.DEP_QUICK=window.DEP_QUICK||{}; window.DEP_NAV=window.DEP_NAV||{};\n");
    html.append("    function escapeRegExp(s){ return s.replace(/[.*+?^${}()|[\\]\\\\]/g,'\\\\$&'); }\n");
    html.append("    function clearHighlights(root){ if(!root) return; const sels=['.dep-label','td:nth-child(1)','td:nth-child(2)','td:nth-child(3)']; sels.forEach(sel=>{ root.querySelectorAll(sel).forEach(el=>{ if(el.dataset && el.dataset.orig){ el.innerHTML=el.dataset.orig; } }); }); }\n");
    html.append("    function applyHighlights(root, term){ if(!root||!term) return; const re=new RegExp(escapeRegExp(term),'gi'); const sels=['.dep-label','td:nth-child(1)','td:nth-child(2)','td:nth-child(3)']; sels.forEach(sel=>{ root.querySelectorAll(sel).forEach(el=>{ if(!el.dataset) el.dataset={}; if(!el.dataset.orig) el.dataset.orig=el.innerHTML; el.innerHTML=el.dataset.orig.replace(re, m=>'<mark class=\\\'hl\\\'>'+m+'</mark>'); }); }); }\n");
    html.append("    function highlightAll(modId, term){ const flat=byId('dep-table-'+modId); const tree=byId('dep-tree-'+modId); [flat,tree].forEach(root=>{ if(!root) return; if(term){ applyHighlights(root, term);} else { clearHighlights(root);} }); }\n");
    html.append("    function updatePrevNextButtons(modId){ const nav=(window.DEP_NAV||{})[modId]; const prev=byId('dep-prev-'+modId), next=byId('dep-next-'+modId); const has=!!(nav&&nav.list&&nav.list.length>0); if(prev) prev.disabled=!has; if(next) next.disabled=!has; }\n");
    html.append("    function focusCurrentMatch(modId){ const nav=(window.DEP_NAV||{})[modId]; if(!nav||!nav.list||!nav.list.length) return; document.querySelectorAll('#dep-table-'+modId+' tr.current-match').forEach(e=>e.classList.remove('current-match')); document.querySelectorAll('#dep-tree-'+modId+' .dep-label.current-match').forEach(e=>e.classList.remove('current-match')); const el=nav.list[nav.idx]; if(!el) return; if(nav.view==='flat'){ el.classList.add('current-match'); el.scrollIntoView({block:'center'});} else { const lab=el.querySelector(':scope > .dep-label'); if(lab){ lab.classList.add('current-match'); lab.scrollIntoView({block:'center'});} else { el.classList.add('current-match'); el.scrollIntoView({block:'center'});} } }\n");
    html.append("    function collectMatchesAndUpdateNav(modId, term){ const v=(byId('dep-view-'+modId)?.value)||'flat'; let list=[]; if(v==='flat'){ list=Array.from(document.querySelectorAll('#dep-table-'+modId+' tr.dep-row')).filter(r=>r.style.display!=='none' && (!term || r.dataset.match==='1')); } else { list=Array.from(document.querySelectorAll('#dep-tree-'+modId+' .dep-node')).filter(li=>li.style.display!== 'none' && (!term || li.dataset.match==='1')); } (window.DEP_NAV||(window.DEP_NAV={}))[modId]={list:list, idx:list.length?0:-1, view:v}; const countEl=byId('dep-count-'+modId); if(countEl) countEl.textContent = term? (list.length+' match'+(list.length>1?'es':'')) : ''; updatePrevNextButtons(modId); focusCurrentMatch(modId); }\n");
    html.append("    function depNext(modId){ const nav=(window.DEP_NAV||{})[modId]; if(!nav||!nav.list||!nav.list.length) return; nav.idx=(nav.idx+1)%nav.list.length; focusCurrentMatch(modId); }\n");
    html.append("    function depPrev(modId){ const nav=(window.DEP_NAV||{})[modId]; if(!nav||!nav.list||!nav.list.length) return; nav.idx=(nav.idx-1+nav.list.length)%nav.list.length; focusCurrentMatch(modId); }\n");
    html.append("    function toggleQuickFilter(modId, prefix, btn){ window.DEP_QUICK=window.DEP_QUICK||{}; const set=(window.DEP_QUICK[modId]||(window.DEP_QUICK[modId]=new Set())); if(set.has(prefix)){ set.delete(prefix); if(btn) btn.classList.remove('active'); } else { set.add(prefix); if(btn) btn.classList.add('active'); } filterDependencies(modId); }\n");
    html.append("    function clearQuickFilters(modId){ const c=byId('dep-quick-'+modId); if(c){ c.querySelectorAll('.filter-chip.active').forEach(b=>b.classList.remove('active')); } if(window.DEP_QUICK&&window.DEP_QUICK[modId]) window.DEP_QUICK[modId].clear(); filterDependencies(modId); }\n");
    html.append("    function toggleQuickFilterFromBtn(btn, prefix){ const wrap=btn.closest('.quick-filters'); if(!wrap) return; const modId=(wrap.id||'').replace('dep-quick-',''); toggleQuickFilter(modId, prefix, btn); }\n");
    html.append("    function clearQuickFiltersFromBtn(btn){ const wrap=btn.closest('.quick-filters'); if(!wrap) return; const modId=(wrap.id||'').replace('dep-quick-',''); clearQuickFilters(modId); }\n");

    html.append("    function filterDependencies(modId){\n");
    html.append("      const term=(byId('dep-search-'+modId)?.value||'').toLowerCase();\n");
    html.append("      const depthLimit=parseInt(byId('dep-depth-'+modId)?.value||'-1',10);\n");
    html.append("      const scopesSel=document.querySelectorAll('input[data-scope-check=\\''+modId+'\\']:checked');\n");
    html.append("      const selected=new Set(Array.from(scopesSel).map(cb=>cb.value));\n");

    html.append("      window.DEP_QUICK=window.DEP_QUICK||{}; const famSel = Array.from(window.DEP_QUICK[modId]||[]);\n");
    html.append("      const rows=document.querySelectorAll('#dep-table-'+modId+' tr.dep-row');\n");
    html.append("      rows.forEach(row=>{\n");
    html.append("        const ga=(row.dataset.ga||'').toLowerCase();\n");
    html.append("        const ver=(row.dataset.version||'').toLowerCase();\n");
    html.append("        const scope=(row.dataset.scope||'');\n");
    html.append("        const depth=parseInt(row.dataset.depth||'1',10);\n");
    html.append("        let ok=true;\n");
    html.append("        if(selected.size>0 && !selected.has(scope)) ok=false;\n");
    html.append("        if(depthLimit>=0 && depth>depthLimit) ok=false;\n");
    html.append("        if(term && !(ga.includes(term)||ver.includes(term))) ok=false;\n");
    html.append("        if(famSel.length>0 && !famSel.some(p=>ga.startsWith(p))) ok=false;\n");
    html.append("        row.dataset.match = (ok && term && (ga.includes(term)||ver.includes(term))) ? '1' : '';\n");
    html.append("        row.style.display=ok?'':'none';\n");
    html.append("      });\n");
    html.append("      const dupesEl=byId('dep-dupes-'+modId);\n");
    html.append("      if(dupesEl){\n");
    html.append("        const vis=Array.from(document.querySelectorAll('#dep-table-'+modId+' tr.dep-row')).filter(r=>r.style.display!=='none');\n");
    html.append("        const map={};\n");
    html.append("        vis.forEach(r=>{ const ga=r.dataset.ga; const v=r.dataset.version||''; (map[ga]||(map[ga]=new Set())).add(v);});\n");
    html.append("        const entries=Object.entries(map).filter(([ga,set])=>set.size>1);\n");
    html.append("        if(entries.length){ dupesEl.innerHTML='⚠️ Duplicates detected: '+entries.map(([ga,set])=>ga+' → '+Array.from(set).join(', ')).join(' | '); } else { dupesEl.innerHTML=''; }\n");
    html.append("      }\n");
    html.append("      const treeRoot=document.querySelector('#dep-tree-'+modId);\n");
    html.append("      const nodes=Array.from(document.querySelectorAll('#dep-tree-'+modId+' .dep-node'));\n");
    html.append("      nodes.forEach(li=>{\n");
    html.append("        const ga=(li.dataset.ga||'').toLowerCase();\n");
    html.append("        const ver=(li.dataset.version||'').toLowerCase();\n");
    html.append("        const scope=(li.dataset.scope||'');\n");
    html.append("        const depth=parseInt(li.dataset.depth||'1',10);\n");
    html.append("        let ok=true;\n");
    html.append("        if(selected.size>0 && !selected.has(scope)) ok=false;\n");
    html.append("        if(depthLimit>=0 && depth>depthLimit) ok=false;\n");
    html.append("        if(term && !(ga.includes(term)||ver.includes(term))) ok=false;\n");
    html.append("        if(famSel.length>0 && !famSel.some(p=>ga.startsWith(p))) ok=false;\n");
    html.append("        li.dataset.match=ok?'1':'';\n");
    html.append("      });\n");
    html.append("      nodes.forEach(li=>{\n");
    html.append("        let show = li.dataset.match==='1' || !!li.querySelector('.dep-node[data-match=\"1\"]');\n");
    html.append("        li.style.display=show?'':'none';\n");
    html.append("        if(show && term){\n");
    html.append("          let p=li.parentElement;\n");
    html.append("          while(p && p!==treeRoot){\n");
    html.append("            if(p.matches && p.matches('ul')){\n");
    html.append("              const pli=p.closest('.dep-node');\n");
    html.append("              if(pli){\n");
    html.append("                pli.classList.remove('collapsed');\n");
    html.append("                const t=pli.querySelector(':scope > .tree-toggle');\n");
    html.append("                if(t) t.textContent='\u25be';\n");
    html.append("              }\n");
    html.append("            }\n");
    html.append("            p=p.parentElement;\n");
    html.append("          }\n");
    html.append("        }\n");
    html.append("      });\n");
    html.append("      highlightAll(modId, term); collectMatchesAndUpdateNav(modId, term);\n");
    html.append("    }\n");
    html.append("    function initDependenciesSection(modId){\n");
    html.append("      const sel=byId('dep-view-'+modId); if(sel) sel.addEventListener('change',()=>{ setDepView(modId); filterDependencies(modId); });\n");
    html.append("      const s=byId('dep-search-'+modId); if(s) s.addEventListener('input',()=>filterDependencies(modId));\n");
    html.append("      const d=byId('dep-depth-'+modId); if(d) d.addEventListener('input',()=>filterDependencies(modId));\n");
    html.append("      document.querySelectorAll('input[data-scope-check=\\''+modId+'\\']').forEach(cb=>cb.addEventListener('change',()=>filterDependencies(modId)));\n");
    html.append("      setDepView(modId); initTreeCollapse(modId); filterDependencies(modId);\n");
    html.append("    }\n");
    html.append("    function exportCsv(modId){\n");
    html.append("      const rows=Array.from(document.querySelectorAll('#dep-table-'+modId+' tr.dep-row')).filter(r=>r.style.display!=='none');\n");
    html.append("      let csv='groupId,artifactId,version,scope,type,optional,depth\\n';\n");
    html.append("      rows.forEach(r=>{ const parts=(r.dataset.ga||':').split(':'); const line=[parts[0]||'',parts[1]||'',r.dataset.version||'',r.dataset.scope||'',r.dataset.type||'',r.dataset.optional||'false',r.dataset.depth||'']; csv+=line.map(v=>\"\"+String(v).replace(/\"/g,'\"\"')+\"\").join(',')+'\\n'; });\n");
    html.append("      const blob=new Blob([csv],{type:'text/csv'}); const a=document.createElement('a'); a.href=URL.createObjectURL(blob); a.download=modId+'-dependencies.csv'; document.body.appendChild(a); a.click(); a.remove();\n");
    html.append("    }\n");

        html.append("    document.addEventListener('DOMContentLoaded', function() {\n");
        html.append("      const savedTheme = localStorage.getItem('theme');\n");
        html.append("      const themeIcon = document.querySelector('.theme-icon');\n");
        html.append("      \n");
        html.append("      if (savedTheme === 'dark') {\n");
        html.append("        document.body.classList.add('dark-mode');\n");
        html.append("        themeIcon.textContent = '☀️';\n");
        html.append("      }\n");
        html.append("    });\n");
        html.append("    document.addEventListener('DOMContentLoaded', function() {\n");
        html.append("      if (window.DEP_SECTIONS) { window.DEP_SECTIONS.forEach(function(id){ try { initDependenciesSection(id); } catch(e) {} }); }\n");
        html.append("    });\n");

        html.append("  </script>\n");
        html.append("</body>\n");
        html.append("</html>\n");

        Files.writeString(htmlPath, html.toString(), StandardCharsets.UTF_8);
        getLog().info("✓ HTML documentation generated: " + htmlPath.toAbsolutePath());
    }

    /**
     * Build transitive dependency trees for modules using Maven's DependencyGraphBuilder
     * and populate the descriptor's tree view (keeping existing flat view if present).
     */
    private void enrichDependencyTrees(ProjectDescriptor descriptor,
                                       io.github.tourem.maven.descriptor.model.DependencyTreeOptions options)
            throws DependencyGraphBuilderException {
        if (descriptor == null || descriptor.deployableModules() == null || descriptor.deployableModules().isEmpty()) {
            return;
        }
        if (session == null || dependencyGraphBuilder == null) {
            getLog().debug("Dependency graph services not available; skipping tree enrichment");
            return;
        }

        // Normalize configuration
        final Set<String> allowedScopes = (options.getScopes() == null || options.getScopes().isEmpty())
                ? new HashSet<>(Arrays.asList("compile", "runtime"))
                : new HashSet<>(options.getScopes().stream().map(String::toLowerCase).toList());
        final boolean includeOptionalDeps = options.isIncludeOptional();
        final int depthLimit = options.getDepth(); // -1 unlimited; 0 direct only; N max depth

        for (io.github.tourem.maven.descriptor.model.DeployableModule module : descriptor.deployableModules()) {
            try {
                // Find the MavenProject for this module in the session or build from its pom.xml
                MavenProject moduleProject = findModuleProject(module);
                if (moduleProject == null) {
                    getLog().debug("Module project not found for " + module.getArtifactId() + "; skipping tree");
                    continue;
                }

                ProjectBuildingRequest req = new DefaultProjectBuildingRequest(session.getProjectBuildingRequest());
                req.setProject(moduleProject);
                org.apache.maven.shared.dependency.graph.DependencyNode root =
                        dependencyGraphBuilder.buildDependencyGraph(req, null);
                if (root == null || root.getChildren() == null) {
                    continue;
                }

                // Convert to our model (top-level children of root are direct deps)
                List<io.github.tourem.maven.descriptor.model.DependencyNode> topNodes = new ArrayList<>();
                for (org.apache.maven.shared.dependency.graph.DependencyNode child : root.getChildren()) {
                    io.github.tourem.maven.descriptor.model.DependencyNode converted =
                            convertNode(child, allowedScopes, includeOptionalDeps, depthLimit, 1, new HashSet<>());
                    if (converted != null) {
                        topNodes.add(converted);
                    }
                }

                // Compute summary counters from built tree
                int direct = topNodes.size();
                SummaryCounters counters = new SummaryCounters();
                for (io.github.tourem.maven.descriptor.model.DependencyNode n : topNodes) {
                    accumulateCounters(n, counters);
                }
                int total = counters.total;
                int transitive = Math.max(0, total - direct);

                io.github.tourem.maven.descriptor.model.DependencySummary summary =
                        io.github.tourem.maven.descriptor.model.DependencySummary.builder()
                                .total(total)
                                .direct(direct)
                                .transitive(transitive)
                                .optional(counters.optional)
                                .scopes(counters.scopes)
                                .build();

                // Preserve existing flat entries if any
                List<io.github.tourem.maven.descriptor.model.DependencyFlatEntry> flat = null;
                if (module.getDependencies() != null) {
                    flat = module.getDependencies().getFlat();
                }

                io.github.tourem.maven.descriptor.model.DependencyTreeInfo info =
                        io.github.tourem.maven.descriptor.model.DependencyTreeInfo.builder()
                                .summary(summary)
                                .flat(flat)
                                .tree(topNodes)
                                .build();

                module.setDependencies(info);

            } catch (Exception ex) {
                getLog().debug("Failed to enrich dependency tree for module " + module.getArtifactId() + ": " + ex.getMessage(), ex);
            }
        }
    }

    private MavenProject findModuleProject(io.github.tourem.maven.descriptor.model.DeployableModule module) throws Exception {
        if (session != null && session.getAllProjects() != null) {
            for (MavenProject p : session.getAllProjects()) {
                if (Objects.equals(p.getGroupId(), module.getGroupId())
                        && Objects.equals(p.getArtifactId(), module.getArtifactId())
                        && Objects.equals(p.getVersion(), module.getVersion())) {
                    return p;
                }
            }
        }
        // Fallback: build from pom.xml
        if (projectBuilder != null && project != null && module.getModulePath() != null) {
            File base = project.getBasedir();
            File moduleDir = ".".equals(module.getModulePath()) ? base : new File(base, module.getModulePath());
            File pom = new File(moduleDir, "pom.xml");
            if (pom.isFile()) {
                ProjectBuildingRequest req = new DefaultProjectBuildingRequest(session.getProjectBuildingRequest());
                return projectBuilder.build(pom, req).getProject();
            }
        }
        return null;
    }

    /**
     * Pre-resolve dependencies for all reactor modules so that required POMs are available
     * in the local repository before license collection runs. This prevents "unknown" licenses
     * and null versions when the build is invoked directly on this goal without a prior resolve.
     */
    private void preResolveDependenciesForLicensesInSession() {
        if (session == null || dependencyGraphBuilder == null) {
            return;
        }
        if (session.getAllProjects() == null || session.getAllProjects().isEmpty()) {
            return;
        }
        getLog().debug("Pre-resolving dependencies for license collection across reactor modules");
        for (MavenProject p : session.getAllProjects()) {
            try {
                ProjectBuildingRequest req = new DefaultProjectBuildingRequest(session.getProjectBuildingRequest());
                req.setProject(p);
                org.apache.maven.shared.dependency.graph.DependencyNode root =
                        dependencyGraphBuilder.buildDependencyGraph(req, null);

                // Traverse the resolved graph to publish GA->V mapping so core can resolve versions fast
                if (root != null) {
                    java.util.ArrayDeque<org.apache.maven.shared.dependency.graph.DependencyNode> stack = new java.util.ArrayDeque<>();
                    java.util.HashSet<String> seen = new java.util.HashSet<>();
                    stack.push(root);
                    while (!stack.isEmpty()) {
                        org.apache.maven.shared.dependency.graph.DependencyNode n = stack.pop();
                        Artifact a = (n != null) ? n.getArtifact() : null;
                        if (a != null) {
                            String g = a.getGroupId();
                            String aId = a.getArtifactId();
                            String v = a.getVersion();
                            if (g != null && aId != null && v != null) {
                                String k = g + ":" + aId;
                                if (seen.add(k)) {
                                    System.setProperty("deploy.manifest.resolved.ga." + k, v);
                                }
                            }
                        }
                        if (n != null && n.getChildren() != null) {
                            for (org.apache.maven.shared.dependency.graph.DependencyNode c : n.getChildren()) {
                                if (c != null) stack.push(c);
                            }
                        }
                    }
                }
            } catch (Throwable t) {
                // Best-effort: do not fail plugin execution because of pre-resolution
                getLog().debug("Skipping pre-resolve for " + p.getGroupId() + ":" + p.getArtifactId() + ": " + t.getMessage());
            }
        }
    }


    private io.github.tourem.maven.descriptor.model.DependencyNode convertNode(
            org.apache.maven.shared.dependency.graph.DependencyNode node,
            Set<String> allowedScopes,
            boolean includeOptional,
            int depthLimit,
            int currentDepth,
            Set<String> visited) {
        Artifact a = node.getArtifact();
        if (a == null) return null;
        String scope = (a.getScope() == null || a.getScope().isEmpty()) ? "compile" : a.getScope().toLowerCase();
        if (!allowedScopes.contains(scope)) return null;
        boolean optional = a.isOptional();
        if (optional && !includeOptional) return null;

        String key = a.getGroupId() + ":" + a.getArtifactId() + ":" + a.getVersion();
        if (!visited.add(key)) {
            return null; // avoid cycles/duplicates
        }

        boolean canGoDeeper;
        if (depthLimit < 0) {
            canGoDeeper = true;
        } else {
            canGoDeeper = currentDepth < depthLimit; // depth=1 means direct
        }

        List<io.github.tourem.maven.descriptor.model.DependencyNode> children = null;
        if (canGoDeeper && node.getChildren() != null && !node.getChildren().isEmpty()) {
            children = new ArrayList<>();
            for (org.apache.maven.shared.dependency.graph.DependencyNode c : node.getChildren()) {
                io.github.tourem.maven.descriptor.model.DependencyNode cc =
                        convertNode(c, allowedScopes, includeOptional, depthLimit, currentDepth + 1, new HashSet<>(visited));
                if (cc != null) children.add(cc);
            }
            if (children.isEmpty()) children = null;
        }

        return io.github.tourem.maven.descriptor.model.DependencyNode.builder()
                .groupId(a.getGroupId())
                .artifactId(a.getArtifactId())
                .version(a.getVersion())
                .scope(scope)
                .type(a.getType())
                .optional(optional)
                .children(children)
                .build();
    }

    private static class SummaryCounters {
        int total = 0;
        int optional = 0;
        Map<String, Integer> scopes = new HashMap<>();
    }

    private void accumulateCounters(io.github.tourem.maven.descriptor.model.DependencyNode n, SummaryCounters c) {
        if (n == null) return;
        c.total++;
        if (n.isOptional()) c.optional++;
        String s = n.getScope() == null ? "" : n.getScope();
        c.scopes.put(s, c.scopes.getOrDefault(s, 0) + 1);
        if (n.getChildren() != null) {
            for (io.github.tourem.maven.descriptor.model.DependencyNode ch : n.getChildren()) {
                accumulateCounters(ch, c);
            }
        }
    }

    private void appendTreeNodeHtml(StringBuilder html, io.github.tourem.maven.descriptor.model.DependencyNode n, String moduleId, int depth) {
        String scope = n.getScope() == null ? "" : n.getScope();
        String ga = (n.getGroupId() == null ? "" : n.getGroupId()) + ":" + (n.getArtifactId() == null ? "" : n.getArtifactId());
        String version = n.getVersion() == null ? "" : n.getVersion();
        String type = n.getType() == null ? "" : n.getType();
        String optional = n.isOptional() ? "true" : "false";
        boolean hasChildren = n.getChildren() != null && !n.getChildren().isEmpty();
        html.append("            <li class=\"dep-node").append(hasChildren ? " has-children" : "")
            .append("\" data-module=\"").append(moduleId)
            .append("\" data-ga=\"").append(escapeHtml(ga))
            .append("\" data-scope=\"").append(escapeHtml(scope))
            .append("\" data-version=\"").append(escapeHtml(version))
            .append("\" data-type=\"").append(escapeHtml(type))
            .append("\" data-optional=\"").append(optional)
            .append("\" data-depth=\"").append(String.valueOf(depth)).append("\">");
        if (hasChildren) {
            html.append("<span class=\"tree-toggle\" onclick=\"toggleTreeNode(this)\">\u25be</span>");
        } else {
            html.append("<span class=\"tree-toggle\" style=\"visibility:hidden\">\u2022</span>");
        }
        html.append("<span class=\"dep-label\">");
        html.append(escapeHtml(ga)).append(": ");
        html.append(" <code>").append(escapeHtml(version)).append("</code>");
        if (!scope.isEmpty()) {
            html.append(" <span class=\"scope-badge scope-").append(escapeHtml(scope)).append("\">")
                .append(escapeHtml(scope)).append("</span>");
        }
        html.append("</span>");
        if (hasChildren) {
            html.append("\n              <ul>\n");
            for (io.github.tourem.maven.descriptor.model.DependencyNode ch : n.getChildren()) {
                appendTreeNodeHtml(html, ch, moduleId, depth + 1);
            }
            html.append("              </ul>\n            ");
        }
        html.append("</li>\n");
    }


    /**
     * Enrich BuildInfo with Maven runtime and executed goals (plugin-only info).
     */
    private io.github.tourem.maven.descriptor.model.ProjectDescriptor enrichBuildInfoWithProperties(io.github.tourem.maven.descriptor.model.ProjectDescriptor descriptor) {
        if (descriptor == null || descriptor.buildInfo() == null) return descriptor;
        var bi = descriptor.buildInfo();

        String mvnVersion = session != null && session.getSystemProperties() != null
                ? session.getSystemProperties().getProperty("maven.version")
                : System.getProperty("maven.version");
        String mvnHome = session != null && session.getSystemProperties() != null
                ? session.getSystemProperties().getProperty("maven.home")
                : System.getProperty("maven.home");
        var maven = io.github.tourem.maven.descriptor.model.MavenRuntimeInfo.builder()
                .version(mvnVersion)
                .home(mvnHome)
                .build();

        java.util.List<String> executed = session != null && session.getGoals() != null
                ? new java.util.ArrayList<>(session.getGoals())
                : null;
        var goals = io.github.tourem.maven.descriptor.model.BuildGoals.builder()
                .defaultGoal(project.getDefaultGoal())
                .executed(executed == null || executed.isEmpty() ? null : executed)
                .build();

        var newBuildInfo = io.github.tourem.maven.descriptor.model.BuildInfo.builder()
                .gitCommitSha(bi.gitCommitSha())
                .gitCommitShortSha(bi.gitCommitShortSha())
                .gitBranch(bi.gitBranch())
                .gitTag(bi.gitTag())
                .gitDirty(bi.gitDirty())
                .gitRemoteUrl(bi.gitRemoteUrl())
                .gitCommitMessage(bi.gitCommitMessage())
                .gitCommitAuthor(bi.gitCommitAuthor())
                .gitCommitTime(bi.gitCommitTime())
                .ciProvider(bi.ciProvider())
                .ciBuildId(bi.ciBuildId())
                .ciBuildNumber(bi.ciBuildNumber())
                .ciBuildUrl(bi.ciBuildUrl())
                .ciJobName(bi.ciJobName())
                .ciActor(bi.ciActor())
                .ciEventName(bi.ciEventName())
                .buildTimestamp(bi.buildTimestamp())
                .buildHost(bi.buildHost())
                .buildUser(bi.buildUser())
                .properties(bi.properties())
                .profiles(bi.profiles())
                .maven(maven)
                .goals(goals)
                .build();

        return io.github.tourem.maven.descriptor.model.ProjectDescriptor.builder()
                .projectGroupId(descriptor.projectGroupId())
                .projectArtifactId(descriptor.projectArtifactId())
                .projectVersion(descriptor.projectVersion())
                .projectName(descriptor.projectName())
                .projectDescription(descriptor.projectDescription())
                .generatedAt(descriptor.generatedAt())
                .deployableModules(descriptor.deployableModules())
                .totalModules(descriptor.totalModules())
                .deployableModulesCount(descriptor.deployableModulesCount())
                .buildInfo(newBuildInfo)
                .mavenRepositoryUrl(descriptor.mavenRepositoryUrl())
                .build();
    }



    /**
     * Escape HTML special characters to prevent XSS.
     */
    private String escapeHtml(String text) {
        if (text == null) {
            return "";
        }
        return text.replace("&", "&amp;")
                   .replace("<", "&lt;")
                   .replace(">", "&gt;")
                   .replace("\"", "&quot;")
                   .replace("'", "&#39;");
    }

    /**
     * Execute post-generation hook script/command.
     */
    private void executePostGenerationHook(Path generatedFile) {
        try {
            getLog().info("Executing post-generation hook: " + postGenerationHook);

            ProcessBuilder pb = new ProcessBuilder();
            pb.command("sh", "-c", postGenerationHook);
            pb.directory(project.getBasedir());

            // Set environment variable with generated file path
            pb.environment().put("DESCRIPTOR_FILE", generatedFile.toAbsolutePath().toString());
            pb.environment().put("PROJECT_NAME", project.getName());

            pb.environment().put("PROJECT_VERSION", project.getVersion());

            pb.redirectErrorStream(true);
            Process process = pb.start();

            // Read output
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    getLog().info("  [hook] " + line);
                }
            }

            int exitCode = process.waitFor();
            if (exitCode == 0) {
                getLog().info("✓ Post-generation hook completed successfully");
            } else {
                getLog().warn("Post-generation hook exited with code: " + exitCode);
            }

        } catch (Exception e) {
            getLog().warn("Failed to execute post-generation hook: " + e.getMessage());
            getLog().debug("Hook execution error details", e);
        }
    }
}

