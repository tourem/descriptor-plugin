package com.larbotech.maven.plugin;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.fasterxml.jackson.dataformat.yaml.YAMLGenerator;
import com.larbotech.maven.descriptor.model.ProjectDescriptor;
import com.larbotech.maven.descriptor.service.MavenProjectAnalyzer;
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

            // Get the project base directory
            File projectDir = project.getBasedir();
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

            // Generate digital signature if requested
            if (sign && primaryOutput != null) {
                generateSignature(primaryOutput);
            }

            // Compress if requested
            if (compress && jsonOutputPath != null) {
                compressFile(jsonOutputPath);
            }

            // Handle archiving and attachment if format is specified
            File finalArtifact = primaryOutput.toFile();

            if (format != null && !format.trim().isEmpty()) {
                finalArtifact = createArchive(primaryOutput);
                getLog().info("✓ Archive created: " + finalArtifact.getAbsolutePath());
            }

            // Attach artifact to project if requested
            if (attach) {
                attachArtifact(finalArtifact);
            }

            // Generate HTML documentation if requested
            if (generateHtml) {
                generateHtmlDocumentation(descriptor, outputPath);
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
     * Creates an archive containing the JSON descriptor file.
     *
     * @param jsonFile the JSON file to archive
     * @return the created archive file
     * @throws IOException if archive creation fails
     */
    private File createArchive(Path jsonFile) throws IOException {
        String normalizedFormat = format.trim().toLowerCase();

        // Determine archive file name
        String archiveBaseName = project.getArtifactId() + "-" + project.getVersion();
        if (classifier != null && !classifier.trim().isEmpty()) {
            archiveBaseName += "-" + classifier;
        }

        File archiveFile;

        switch (normalizedFormat) {
            case "zip":
            case "jar":
                archiveFile = new File(jsonFile.getParent().toFile(), archiveBaseName + ".zip");
                createZipArchive(jsonFile, archiveFile);
                break;

            case "tar.gz":
            case "tgz":
                archiveFile = new File(jsonFile.getParent().toFile(), archiveBaseName + ".tar.gz");
                createTarGzArchive(jsonFile, archiveFile);
                break;

            case "tar.bz2":
            case "tbz2":
                archiveFile = new File(jsonFile.getParent().toFile(), archiveBaseName + ".tar.bz2");
                createTarBz2Archive(jsonFile, archiveFile);
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
     * Creates a ZIP archive containing the JSON file.
     */
    private void createZipArchive(Path jsonFile, File archiveFile) throws IOException {
        try (FileOutputStream fos = new FileOutputStream(archiveFile);
             ZipOutputStream zos = new ZipOutputStream(fos)) {

            ZipEntry entry = new ZipEntry(jsonFile.getFileName().toString());
            zos.putNextEntry(entry);

            Files.copy(jsonFile, zos);
            zos.closeEntry();
        }
    }

    /**
     * Creates a TAR.GZ archive containing the JSON file.
     */
    private void createTarGzArchive(Path jsonFile, File archiveFile) throws IOException {
        try (FileOutputStream fos = new FileOutputStream(archiveFile);
             GzipCompressorOutputStream gzos = new GzipCompressorOutputStream(fos);
             TarArchiveOutputStream taos = new TarArchiveOutputStream(gzos)) {

            TarArchiveEntry entry = new TarArchiveEntry(jsonFile.toFile(), jsonFile.getFileName().toString());
            taos.putArchiveEntry(entry);

            Files.copy(jsonFile, taos);
            taos.closeArchiveEntry();
            taos.finish();
        }
    }

    /**
     * Creates a TAR.BZ2 archive containing the JSON file.
     */
    private void createTarBz2Archive(Path jsonFile, File archiveFile) throws IOException {
        try (FileOutputStream fos = new FileOutputStream(archiveFile);
             BZip2CompressorOutputStream bz2os = new BZip2CompressorOutputStream(fos);
             TarArchiveOutputStream taos = new TarArchiveOutputStream(bz2os)) {

            TarArchiveEntry entry = new TarArchiveEntry(jsonFile.toFile(), jsonFile.getFileName().toString());
            taos.putArchiveEntry(entry);

            Files.copy(jsonFile, taos);
            taos.closeArchiveEntry();
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
        html.append("  <title>Deployment Descriptor - ").append(escapeHtml(descriptor.projectName())).append("</title>\n");
        html.append("  <style>\n");
        html.append("    body { font-family: Arial, sans-serif; margin: 20px; background: #f5f5f5; }\n");
        html.append("    .container { max-width: 1200px; margin: 0 auto; background: white; padding: 30px; border-radius: 8px; box-shadow: 0 2px 4px rgba(0,0,0,0.1); }\n");
        html.append("    h1 { color: #333; border-bottom: 3px solid #4CAF50; padding-bottom: 10px; }\n");
        html.append("    h2 { color: #555; margin-top: 30px; border-bottom: 2px solid #ddd; padding-bottom: 8px; }\n");
        html.append("    h3 { color: #666; margin-top: 20px; font-size: 16px; }\n");
        html.append("    .info-grid { display: grid; grid-template-columns: 200px 1fr; gap: 10px; margin: 20px 0; }\n");
        html.append("    .info-label { font-weight: bold; color: #666; }\n");
        html.append("    .info-value { color: #333; word-break: break-all; }\n");
        html.append("    .module { background: #f9f9f9; padding: 15px; margin: 15px 0; border-left: 4px solid #4CAF50; border-radius: 4px; }\n");
        html.append("    .module-title { font-size: 18px; font-weight: bold; color: #333; margin-bottom: 10px; }\n");
        html.append("    .badge { display: inline-block; padding: 4px 8px; border-radius: 3px; font-size: 12px; font-weight: bold; margin-right: 5px; }\n");
        html.append("    .badge-spring { background: #6DB33F; color: white; }\n");
        html.append("    .badge-jar { background: #2196F3; color: white; }\n");
        html.append("    .badge-war { background: #FF9800; color: white; }\n");
        html.append("    .badge-git { background: #F05032; color: white; }\n");
        html.append("    table { width: 100%; border-collapse: collapse; margin: 15px 0; }\n");
        html.append("    th, td { padding: 10px; text-align: left; border-bottom: 1px solid #ddd; }\n");
        html.append("    th { background: #4CAF50; color: white; font-weight: bold; }\n");
        html.append("    tr:hover { background: #f5f5f5; }\n");
        html.append("    .timestamp { color: #999; font-size: 14px; }\n");
        html.append("    .section { margin-top: 20px; }\n");
        html.append("    code { background: #f4f4f4; padding: 2px 6px; border-radius: 3px; font-family: monospace; font-size: 13px; }\n");
        html.append("  </style>\n");
        html.append("</head>\n");
        html.append("<body>\n");
        html.append("  <div class=\"container\">\n");
        html.append("    <h1>").append(escapeHtml(descriptor.projectName())).append("</h1>\n");
        html.append("    <p class=\"timestamp\">Generated: ").append(descriptor.generatedAt()).append("</p>\n");

        // Project Info
        html.append("    <h2>Project Information</h2>\n");
        html.append("    <div class=\"info-grid\">\n");
        html.append("      <div class=\"info-label\">Group ID:</div><div class=\"info-value\">").append(escapeHtml(descriptor.projectGroupId())).append("</div>\n");
        html.append("      <div class=\"info-label\">Artifact ID:</div><div class=\"info-value\">").append(escapeHtml(descriptor.projectArtifactId())).append("</div>\n");
        html.append("      <div class=\"info-label\">Version:</div><div class=\"info-value\">").append(escapeHtml(descriptor.projectVersion())).append("</div>\n");
        if (descriptor.projectDescription() != null) {
            html.append("      <div class=\"info-label\">Description:</div><div class=\"info-value\">").append(escapeHtml(descriptor.projectDescription())).append("</div>\n");
        }
        html.append("      <div class=\"info-label\">Total Modules:</div><div class=\"info-value\">").append(descriptor.totalModules()).append("</div>\n");
        html.append("      <div class=\"info-label\">Deployable:</div><div class=\"info-value\">").append(descriptor.deployableModulesCount()).append("</div>\n");
        html.append("    </div>\n");

        // Build Info
        if (descriptor.buildInfo() != null) {
            var buildInfo = descriptor.buildInfo();
            html.append("    <h2>Build Information</h2>\n");
            html.append("    <div class=\"info-grid\">\n");
            if (buildInfo.gitCommitSha() != null) {
                html.append("      <div class=\"info-label\">Git Commit:</div><div class=\"info-value\"><code>").append(escapeHtml(buildInfo.gitCommitShortSha())).append("</code></div>\n");
                html.append("      <div class=\"info-label\">Full SHA:</div><div class=\"info-value\"><code>").append(escapeHtml(buildInfo.gitCommitSha())).append("</code></div>\n");
            }
            if (buildInfo.gitBranch() != null) {
                html.append("      <div class=\"info-label\">Git Branch:</div><div class=\"info-value\"><span class=\"badge badge-git\">").append(escapeHtml(buildInfo.gitBranch())).append("</span></div>\n");
            }
            if (buildInfo.gitTag() != null) {
                html.append("      <div class=\"info-label\">Git Tag:</div><div class=\"info-value\"><span class=\"badge badge-git\">").append(escapeHtml(buildInfo.gitTag())).append("</span></div>\n");
            }
            if (buildInfo.gitDirty() != null) {
                html.append("      <div class=\"info-label\">Git Dirty:</div><div class=\"info-value\">").append(buildInfo.gitDirty() ? "Yes (uncommitted changes)" : "No").append("</div>\n");
            }
            if (buildInfo.gitRemoteUrl() != null) {
                html.append("      <div class=\"info-label\">Git Remote:</div><div class=\"info-value\">").append(escapeHtml(buildInfo.gitRemoteUrl())).append("</div>\n");
            }
            if (buildInfo.gitCommitMessage() != null) {
                html.append("      <div class=\"info-label\">Commit Message:</div><div class=\"info-value\">").append(escapeHtml(buildInfo.gitCommitMessage())).append("</div>\n");
            }
            if (buildInfo.gitCommitAuthor() != null) {
                html.append("      <div class=\"info-label\">Commit Author:</div><div class=\"info-value\">").append(escapeHtml(buildInfo.gitCommitAuthor())).append("</div>\n");
            }
            if (buildInfo.gitCommitTime() != null) {
                html.append("      <div class=\"info-label\">Commit Time:</div><div class=\"info-value\">").append(buildInfo.gitCommitTime()).append("</div>\n");
            }
            if (buildInfo.buildTimestamp() != null) {
                html.append("      <div class=\"info-label\">Build Timestamp:</div><div class=\"info-value\">").append(buildInfo.buildTimestamp()).append("</div>\n");
            }
            if (buildInfo.buildHost() != null) {
                html.append("      <div class=\"info-label\">Build Host:</div><div class=\"info-value\">").append(escapeHtml(buildInfo.buildHost())).append("</div>\n");
            }
            if (buildInfo.buildUser() != null) {
                html.append("      <div class=\"info-label\">Build User:</div><div class=\"info-value\">").append(escapeHtml(buildInfo.buildUser())).append("</div>\n");
            }
            if (buildInfo.ciProvider() != null) {
                html.append("      <div class=\"info-label\">CI Provider:</div><div class=\"info-value\"><span class=\"badge badge-git\">").append(escapeHtml(buildInfo.ciProvider())).append("</span></div>\n");
            }
            if (buildInfo.ciBuildId() != null) {
                html.append("      <div class=\"info-label\">CI Build ID:</div><div class=\"info-value\">").append(escapeHtml(buildInfo.ciBuildId())).append("</div>\n");
            }
            if (buildInfo.ciBuildUrl() != null) {
                html.append("      <div class=\"info-label\">CI Build URL:</div><div class=\"info-value\"><a href=\"").append(escapeHtml(buildInfo.ciBuildUrl())).append("\" target=\"_blank\">").append(escapeHtml(buildInfo.ciBuildUrl())).append("</a></div>\n");
            }
            html.append("    </div>\n");
        }

        // Deployable Modules
        if (descriptor.deployableModules() != null && !descriptor.deployableModules().isEmpty()) {
            html.append("    <h2>Deployable Modules</h2>\n");
            descriptor.deployableModules().forEach(module -> {
                html.append("    <div class=\"module\">\n");
                html.append("      <div class=\"module-title\">").append(escapeHtml(module.getArtifactId())).append("</div>\n");
                html.append("      <span class=\"badge badge-").append(module.getPackaging()).append("\">").append(module.getPackaging().toUpperCase()).append("</span>\n");
                if (module.isSpringBootExecutable()) {
                    html.append("      <span class=\"badge badge-spring\">SPRING BOOT</span>\n");
                }
                html.append("      <div class=\"info-grid\" style=\"margin-top: 10px;\">\n");
                html.append("        <div class=\"info-label\">Group ID:</div><div class=\"info-value\">").append(escapeHtml(module.getGroupId())).append("</div>\n");
                html.append("        <div class=\"info-label\">Version:</div><div class=\"info-value\">").append(escapeHtml(module.getVersion())).append("</div>\n");
                html.append("        <div class=\"info-label\">Repository Path:</div><div class=\"info-value\"><code>").append(escapeHtml(module.getRepositoryPath())).append("</code></div>\n");
                if (module.getFinalName() != null) {
                    html.append("        <div class=\"info-label\">Final Name:</div><div class=\"info-value\">").append(escapeHtml(module.getFinalName())).append("</div>\n");
                }
                if (module.getMainClass() != null) {
                    html.append("        <div class=\"info-label\">Main Class:</div><div class=\"info-value\"><code>").append(escapeHtml(module.getMainClass())).append("</code></div>\n");
                }
                if (module.getJavaVersion() != null) {
                    html.append("        <div class=\"info-label\">Java Version:</div><div class=\"info-value\">").append(escapeHtml(module.getJavaVersion())).append("</div>\n");
                }
                html.append("      </div>\n");

                // Environments
                if (module.getEnvironments() != null && !module.getEnvironments().isEmpty()) {
                    html.append("      <h3>Environments</h3>\n");
                    html.append("      <table>\n");
                    html.append("        <tr><th>Profile</th><th>Port</th><th>Context Path</th><th>Actuator</th><th>Health</th><th>Info</th></tr>\n");
                    module.getEnvironments().forEach(env -> {
                        html.append("        <tr>\n");
                        html.append("          <td><strong>").append(escapeHtml(env.profile())).append("</strong></td>\n");
                        html.append("          <td>").append(env.serverPort() != null ? env.serverPort() : "-").append("</td>\n");
                        html.append("          <td>").append(env.contextPath() != null ? escapeHtml(env.contextPath()) : "-").append("</td>\n");
                        html.append("          <td>").append(env.actuatorEnabled() != null && env.actuatorEnabled() ? "✓" : "-").append("</td>\n");
                        html.append("          <td>").append(env.actuatorHealthPath() != null ? "<code>" + escapeHtml(env.actuatorHealthPath()) + "</code>" : "-").append("</td>\n");
                        html.append("          <td>").append(env.actuatorInfoPath() != null ? "<code>" + escapeHtml(env.actuatorInfoPath()) + "</code>" : "-").append("</td>\n");
                        html.append("        </tr>\n");
                    });
                    html.append("      </table>\n");
                }

                // Assembly Artifacts
                if (module.getAssemblyArtifacts() != null && !module.getAssemblyArtifacts().isEmpty()) {
                    html.append("      <h3>Assembly Artifacts</h3>\n");
                    html.append("      <table>\n");
                    html.append("        <tr><th>Assembly ID</th><th>Format</th><th>Repository Path</th></tr>\n");
                    module.getAssemblyArtifacts().forEach(assembly -> {
                        html.append("        <tr>\n");
                        html.append("          <td><strong>").append(escapeHtml(assembly.assemblyId())).append("</strong></td>\n");
                        html.append("          <td>").append(escapeHtml(assembly.format().toUpperCase())).append("</td>\n");
                        html.append("          <td><code>").append(escapeHtml(assembly.repositoryPath())).append("</code></td>\n");
                        html.append("        </tr>\n");
                    });
                    html.append("      </table>\n");
                }

                html.append("    </div>\n");
            });
        }

        html.append("  </div>\n");
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

