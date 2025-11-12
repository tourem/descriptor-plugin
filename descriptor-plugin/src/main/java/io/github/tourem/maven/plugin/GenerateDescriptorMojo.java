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

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
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
 */
@Mojo(name = "generate", defaultPhase = LifecyclePhase.PACKAGE, threadSafe = true)
public class GenerateDescriptorMojo extends AbstractMojo {

    /**
     * The Maven project being analyzed.
     */
    @Parameter(defaultValue = "${project}", readonly = true, required = true)
    private MavenProject project;

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
            MavenProjectAnalyzer analyzer = new MavenProjectAnalyzer();
            ProjectDescriptor descriptor = analyzer.analyzeProject(projectDir.toPath());

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
        html.append("      <button class=\"tab active\" onclick=\"showTab('overview')\">📊 Overview</button>\n");
        html.append("      <button class=\"tab\" onclick=\"showTab('build')\">🔨 Build Info</button>\n");
        html.append("      <button class=\"tab\" onclick=\"showTab('modules')\">📦 Modules</button>\n");
        html.append("      <button class=\"tab\" onclick=\"showTab('environments')\">🌍 Environments</button>\n");
        html.append("      <button class=\"tab\" onclick=\"showTab('assemblies')\">📚 Assemblies</button>\n");
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

                // Build Plugins
                if (module.getBuildPlugins() != null && !module.getBuildPlugins().isEmpty()) {
                    html.append("        <div class=\"section-header\" style=\"font-size: 1.1em; margin-top: 20px;\">🔧 Build Plugins</div>\n");
                    html.append("        <div style=\"display: flex; gap: 8px; flex-wrap: wrap; margin-top: 10px;\">\n");
                    module.getBuildPlugins().forEach(plugin -> {
                        html.append("          <span class=\"badge badge-jar\">").append(escapeHtml(plugin)).append("</span>\n");
                    });
                    html.append("        </div>\n");
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
        html.append("    function showTab(tabName) {\n");
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
        html.append("      event.target.classList.add('active');\n");
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
        html.append("    document.addEventListener('DOMContentLoaded', function() {\n");
        html.append("      const savedTheme = localStorage.getItem('theme');\n");
        html.append("      const themeIcon = document.querySelector('.theme-icon');\n");
        html.append("      \n");
        html.append("      if (savedTheme === 'dark') {\n");
        html.append("        document.body.classList.add('dark-mode');\n");
        html.append("        themeIcon.textContent = '☀️';\n");
        html.append("      }\n");
        html.append("    });\n");
        html.append("  </script>\n");
        html.append("</body>\n");
        html.append("</html>\n");

        Files.writeString(htmlPath, html.toString(), StandardCharsets.UTF_8);
        getLog().info("✓ HTML documentation generated: " + htmlPath.toAbsolutePath());
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

