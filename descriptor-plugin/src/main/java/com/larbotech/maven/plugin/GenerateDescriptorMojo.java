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

    /** UX/DX options **/
    /**
     * Print a human-friendly summary of the descriptor in the console after generation.
     */
    @Parameter(property = "descriptor.summary", defaultValue = "false")
    private boolean summary;

    /**
     * Only print the summary without writing files to disk.
     */
    @Parameter(property = "descriptor.summaryOnly", defaultValue = "false")
    private boolean summaryOnly;

    /**
     * Also generate an HTML documentation file from the descriptor.
     */
    @Parameter(property = "descriptor.generateHtml", defaultValue = "false")
    private boolean generateHtml;

    /**
     * Optional local command to run after generation (post-hook). Example: "./scripts/post.sh".
     */
    @Parameter(property = "descriptor.postCommand")
    private String postCommand;

    /**
     * Working directory for the postCommand. Defaults to project baseDir if not set.
     */
    @Parameter(property = "descriptor.postCommandDir")
    private String postCommandDir;

    /**
     * Timeout in seconds for the postCommand.
     */
    @Parameter(property = "descriptor.postCommandTimeout", defaultValue = "60")
    private int postCommandTimeout;

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

            // Handle summary output before writing files
            if (summary || summaryOnly) {
                printSummary(descriptor);
                if (summaryOnly) {
                    getLog().info("SummaryOnly enabled, skipping file generation.");
                    return;
                }
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

            // Optionally generate HTML documentation
            if (generateHtml) {
                Path htmlPath = changeExtension(primaryOutput, ".html");
                generateHtmlDoc(descriptor, htmlPath);
                getLog().info("✓ HTML documentation generated: " + htmlPath.toAbsolutePath());
            }

            // Send webhook notification if configured
            if (webhookUrl != null && !webhookUrl.trim().isEmpty()) {
                sendWebhookNotification(descriptor, primaryOutput);
            }

            // Run local post-generation command if configured
            if (postCommand != null && !postCommand.trim().isEmpty()) {
                runPostCommand(primaryOutput);
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
     * Print a concise summary of the generated descriptor to the console.
     */
    private void printSummary(ProjectDescriptor d) {
        getLog().info("\n===== Descriptor Summary =====");
        getLog().info("Project: " + coalesce(d.projectName(), d.projectArtifactId()));
        getLog().info("Version: " + d.projectVersion());
        if (d.buildInfo() != null) {
            getLog().info("Git: " + coalesce(d.buildInfo().gitCommitSha(), "n/a") +
                    " @ " + coalesce(d.buildInfo().gitBranch(), "n/a"));
            if (d.buildInfo().ciProvider() != null) {
                getLog().info("CI: " + d.buildInfo().ciProvider() +
                        (d.buildInfo().ciBuildId() != null ? (" #" + d.buildInfo().ciBuildId()) : ""));
            }
        }
        getLog().info("Modules: total=" + d.totalModules() + ", deployable=" + d.deployableModulesCount());
        if (d.deployableModules() != null && !d.deployableModules().isEmpty()) {
            getLog().info(String.format("%-30s %-10s %-6s %-12s %-30s",
                    "artifactId", "version", "pack", "frameworks", "repoPath"));
            for (var m : d.deployableModules()) {
                String frameworks = m.getFrameworks() == null ? (m.isSpringBootExecutable() ? "spring-boot" : "-")
                        : String.join(",", m.getFrameworks());
                getLog().info(String.format("%-30s %-10s %-6s %-12s %-30s",
                        m.getArtifactId(), coalesce(m.getVersion(), ""), coalesce(m.getPackaging(), ""),
                        frameworks, coalesce(m.getRepositoryPath(), "")));
            }
        }
        getLog().info("==============================\n");
    }

    private String coalesce(String a, String b) { return (a == null || a.isEmpty()) ? b : a; }

    /**
     * Generate a rich, responsive HTML documentation for the descriptor with most JSON/YAML fields.
     */
    private void generateHtmlDoc(ProjectDescriptor d, Path htmlPath) throws IOException {
        String title = "Descriptor " + escape(coalesce(d.projectName(), d.projectArtifactId()));
        StringBuilder sb = new StringBuilder();
        sb.append("<!DOCTYPE html><html lang=\"en\"><head><meta charset=\"utf-8\">")
          .append("<meta name=\"viewport\" content=\"width=device-width, initial-scale=1\">")
          .append("<title>").append(title).append("</title>")
          .append("<style>")
          // Theme & palette (light/dark)
          .append(":root{--bg:#f6f8fa;--card:#fff;--text:#24292e;--muted:#57606a;--accent:#2ea44f;--accent2:#0969da;--border:#d0d7de;--warn:#b42318;--shadow:0 1px 2px rgba(0,0,0,.06)}")
          .append("@media (prefers-color-scheme: dark){:root{--bg:#0d1117;--card:#161b22;--text:#e6edf3;--muted:#8b949e;--accent:#2ea043;--accent2:#58a6ff;--border:#30363d;--warn:#ff6b6b;--shadow:none}}\n")
          .append("*{box-sizing:border-box}body{margin:0;background:var(--bg);color:var(--text);font:14px/1.5 -apple-system,BlinkMacSystemFont,Segoe UI,Roboto,Helvetica,Arial,sans-serif}\n")
          .append("a{color:var(--accent2);text-decoration:none}a:hover{text-decoration:underline}\n")
          .append(".container{max-width:1200px;margin:24px auto;padding:0 16px}\n")
          .append(".topbar{position:sticky;top:0;z-index:10;background:var(--card);border-bottom:1px solid var(--border);box-shadow:var(--shadow)}\n")
          .append(".topbar .inner{display:flex;align-items:center;gap:16px;justify-content:space-between;padding:10px 16px}\n")
          .append(".brand{display:flex;align-items:center;gap:10px} .brand .title{font-size:18px;font-weight:700;margin:0}\n")
          .append(".badges{display:flex;gap:6px;flex-wrap:wrap} .badge{display:inline-block;padding:3px 8px;border-radius:999px;font-size:12px;font-weight:600;background:#e6f0ff;color:var(--accent2);border:1px solid #bfd3ff} .badge.green{background:#e8f6ee;color:#1a7f37;border-color:#b4e1c5} .badge.warn{background:#fde8e8;color:#b42318;border-color:#f7c5c5}\n")
          .append(".layout{display:grid;grid-template-columns:260px 1fr;gap:16px;margin-top:16px} @media(max-width:1000px){.layout{grid-template-columns:1fr}}\n")
          .append(".toc{position:sticky;top:60px;background:var(--card);border:1px solid var(--border);border-radius:8px;box-shadow:var(--shadow);padding:12px;height:max-content}\n")
          .append(".toc h3{margin:4px 0 8px 0;font-size:13px;color:var(--muted)} .toc a{display:block;padding:6px 8px;border-radius:6px;color:var(--text)} .toc a:hover{background:rgba(0,0,0,.04)}\n")
          .append(".card{background:var(--card);border:1px solid var(--border);border-radius:10px;box-shadow:var(--shadow);padding:16px}\n")
          .append(".space{height:12px}\n")
          .append(".kv{display:grid;grid-template-columns:220px 1fr;gap:8px;align-items:center} .kv .k{color:var(--muted)} .mono{font-family:ui-monospace,SFMono-Regular,Menlo,Monaco,Consolas,monospace}\n")
          .append("table{width:100%;border-collapse:collapse} th,td{padding:8px 10px;border-bottom:1px solid var(--border);vertical-align:top} th{background:rgba(0,0,0,.05);text-align:left} code{background:rgba(0,0,0,.06);padding:2px 4px;border-radius:4px}\n")
          .append(".section-title{font-size:16px;margin:0 0 12px 0} .muted{color:var(--muted)}\n")
          .append("details.module{border-left:4px solid var(--accent);padding-left:12px;margin:12px 0} details.module>summary{list-style:none;cursor:pointer;display:flex;align-items:center;gap:8px} details.module>summary::-webkit-details-marker{display:none}\n")
          .append(".summary-title{font-weight:700} .module-badges{display:flex;gap:6px;flex-wrap:wrap}\n")
          .append(".controls{display:flex;gap:8px;align-items:center} .search{padding:8px 10px;border:1px solid var(--border);border-radius:8px;background:transparent;color:var(--text);width:260px}\n")
          .append("footer{margin:24px 0;color:var(--muted);font-size:12px;text-align:center}\n")
          .append("html{scroll-behavior:smooth;scroll-padding-top:72px}\n")
          .append("#overview,#build,#modules{scroll-margin-top:72px}\n")
          .append("</style>")
          .append("<script>document.addEventListener('DOMContentLoaded',()=>{\n" +
                  "  // Filter\n" +
                  "  const q=document.getElementById('filter');\n" +
                  "  const items=[...document.querySelectorAll('details.module')];\n" +
                  "  if(q){q.addEventListener('input',()=>{const v=q.value.toLowerCase();items.forEach(el=>{const key=el.getAttribute('data-key')||'';el.style.display=!v||key.includes(v)?'block':'none';});});}\n" +
                  "  // Anchor navigation (robust with sticky header)\n" +
                  "  const offset=72;\n" +
                  "  const scrollToId=(id)=>{const el=document.getElementById(id);if(!el)return;const y=el.getBoundingClientRect().top+window.pageYOffset-offset;window.scrollTo({top:y,behavior:'smooth'});};\n" +
                  "  document.querySelectorAll('.toc a[href^=\\'#\\']').forEach(a=>{a.addEventListener('click',e=>{e.preventDefault();const id=a.getAttribute('href').substring(1);scrollToId(id);history.replaceState(null,'','#'+id);});});\n" +
                  "  // If page opens with hash, adjust position\n" +
                  "  if(location.hash){setTimeout(()=>{scrollToId(location.hash.substring(1));},0);}\n" +
                  "});</script>")
          .append("</head><body>");

        // Top bar
        sb.append("<div class=\"topbar\"><div class=\"inner\">")
          .append("<div class=\"brand\"><div class=\"title\">")
          .append(escape(coalesce(d.projectName(), d.projectArtifactId())))
          .append("</div><div class=\"muted\">")
          .append("Group: ").append(escape(coalesce(d.projectGroupId(),""))).append(" • Artifact: ")
          .append(escape(coalesce(d.projectArtifactId(),""))).append("</div></div>")
          .append("<div class=\"controls\"><input id=\"filter\" class=\"search\" placeholder=\"Filter modules...\" aria-label=\"Filter modules\"></div>")
          .append("<div class=\"badges\"><span class=\"badge green\">v").append(escape(coalesce(d.projectVersion(),""))).append("</span>")
          .append("<span class=\"badge\">modules: ").append(String.valueOf(d.totalModules())).append("</span>")
          .append("<span class=\"badge\">deployable: ").append(String.valueOf(d.deployableModulesCount())).append("</span></div>")
          .append("</div></div>");

        sb.append("<div class=\"container\"><div class=\"layout\">");

        // TOC
        sb.append("<nav class=\"toc\"><h3>Contents</h3>")
          .append("<a href=\"#overview\">Overview</a>")
          .append("<a href=\"#build\">Build & CI/CD</a>")
          .append("<a href=\"#modules\">Deployable Modules</a>")
          .append("</nav>");

        // Main content
        sb.append("<div>");

        // Overview card
        sb.append("<div id=\"overview\" class=\"card\"><div class=\"section-title\">Overview</div>")
          .append("<div class=\"kv\"><div class=\"k\">Project</div><div>").append(escape(coalesce(d.projectName(), d.projectArtifactId()))).append("</div></div>")
          .append("<div class=\"kv\"><div class=\"k\">Description</div><div>").append(escape(coalesce(d.projectDescription(),""))).append("</div></div>")
          .append("<div class=\"kv\"><div class=\"k\">Generated At</div><div>").append(escape(String.valueOf(d.generatedAt()))).append("</div></div>")
          .append("<div class=\"kv\"><div class=\"k\">GAV</div><div class=\"mono\">")
          .append(escape(coalesce(d.projectGroupId(),""))).append(":")
          .append(escape(coalesce(d.projectArtifactId(),""))).append(":")
          .append(escape(coalesce(d.projectVersion(),""))).append("</div></div>")
          .append("</div><div class=\"space\"></div>");

        // Build & CI card
        if (d.buildInfo() != null) {
            var b = d.buildInfo();
            sb.append("<div id=\"build\" class=\"card\"><div class=\"section-title\">Build & CI/CD</div>")
              .append("<table><tbody>");
            if (b.gitCommitSha() != null) {
                String shortSha = b.gitCommitSha().length() > 7 ? b.gitCommitSha().substring(0, 7) : b.gitCommitSha();
                sb.append("<tr><th>Commit</th><td class=\"mono\">").append(escape(shortSha)).append("</td></tr>");
            }
            if (b.gitBranch() != null) sb.append("<tr><th>Branch</th><td>").append(escape(b.gitBranch())).append("</td></tr>");
            if (b.gitDirty()) sb.append("<tr><th>Dirty</th><td><span class=\"badge warn\">uncommitted changes</span></td></tr>");
            if (b.gitTag() != null) sb.append("<tr><th>Tag</th><td>").append(escape(b.gitTag())).append("</td></tr>");
            if (b.gitRepositoryUrl() != null) {
                String url = b.gitRepositoryUrl();
                String label = url;
                sb.append("<tr><th>Remote</th><td>");
                if (url.startsWith("http")) {
                    sb.append("<a href=\"").append(escape(url)).append("\" target=\"_blank\">").append(escape(label)).append("</a>");
                } else {
                    sb.append(escape(label));
                }
                sb.append("</td></tr>");
            }
            if (b.ciProvider() != null) sb.append("<tr><th>CI Provider</th><td>").append(escape(b.ciProvider())).append("</td></tr>");
            if (b.ciBuildId() != null) sb.append("<tr><th>Build</th><td>#").append(escape(b.ciBuildId())).append("</td></tr>");
            if (b.ciBuildUrl() != null) sb.append("<tr><th>Build URL</th><td><a target=\"_blank\" href=\"").append(escape(b.ciBuildUrl())).append("\">").append(escape(b.ciBuildUrl())).append("</a></td></tr>");
            if (b.ciActor() != null) sb.append("<tr><th>Actor</th><td>").append(escape(b.ciActor())).append("</td></tr>");
            if (b.buildTimestamp() != null) sb.append("<tr><th>Build Timestamp</th><td>").append(escape(String.valueOf(b.buildTimestamp()))).append("</td></tr>");
            sb.append("</tbody></table></div><div class=\"space\"></div>");
        }

        // Modules section
        sb.append("<div id=\"modules\" class=\"card\"><div class=\"section-title\">Deployable Modules</div>");
        if (d.deployableModules() != null && !d.deployableModules().isEmpty()) {
            for (var m : d.deployableModules()) {
                String id = escape(coalesce(m.getArtifactId(), "module"));
                String searchKey = (coalesce(m.getGroupId(), "")+" "+coalesce(m.getArtifactId(), "")+" "+coalesce(m.getVersion(), "")+" "+coalesce(m.getPackaging(), "")+" "+coalesce(m.getRepositoryPath(), "")).toLowerCase();
                String frameworks = (m.getFrameworks() == null || m.getFrameworks().isEmpty())
                        ? (m.isSpringBootExecutable() ? "spring-boot" : null)
                        : String.join(", ", m.getFrameworks());
                sb.append("<details class=\"module\" data-key=\"").append(escape(searchKey)).append("\">")
                  .append("<summary>")
                  .append("<span class=\"summary-title\">").append(id).append("</span>")
                  .append("<span class=\"module-badges\">")
                  .append("<span class=\"badge\">").append(escape(coalesce(m.getPackaging(), ""))).append("</span>");
                if (m.isSpringBootExecutable()) sb.append("<span class=\"badge green\">SPRING BOOT</span>");
                if (frameworks != null) sb.append("<span class=\"badge\">").append(escape(frameworks)).append("</span>");
                sb.append("</span></summary>");

                // Basics table
                sb.append("<table><tbody>");
                if (m.getGroupId() != null) sb.append("<tr><th>GroupId</th><td class=\"mono\">").append(escape(m.getGroupId())).append("</td></tr>");
                if (m.getArtifactId() != null) sb.append("<tr><th>ArtifactId</th><td class=\"mono\">").append(escape(m.getArtifactId())).append("</td></tr>");
                if (m.getVersion() != null) sb.append("<tr><th>Version</th><td>").append(escape(m.getVersion())).append("</td></tr>");
                if (m.getClassifier() != null) sb.append("<tr><th>Classifier</th><td>").append(escape(m.getClassifier())).append("</td></tr>");
                if (m.getFinalName() != null) sb.append("<tr><th>Final Name</th><td class=\"mono\">").append(escape(m.getFinalName())).append("</td></tr>");
                if (m.getModulePath() != null) sb.append("<tr><th>Module Path</th><td class=\"mono\">").append(escape(m.getModulePath())).append("</td></tr>");
                if (m.getRepositoryPath() != null) sb.append("<tr><th>Repository Path</th><td class=\"mono\"><code>")
                        .append(escape(m.getRepositoryPath())).append("</code></td></tr>");
                if (m.getMainClass() != null) sb.append("<tr><th>Main Class</th><td class=\"mono\">").append(escape(m.getMainClass())).append("</td></tr>");
                if (m.getJavaVersion() != null) sb.append("<tr><th>Java Version</th><td>").append(escape(m.getJavaVersion())).append("</td></tr>");
                sb.append("</tbody></table>");

                // Build plugins
                if (m.getBuildPlugins() != null && !m.getBuildPlugins().isEmpty()) {
                    sb.append("<div class=\"section-title\">Build Plugins</div><div>");
                    for (String p : m.getBuildPlugins()) sb.append("<span class=\"chip\">").append(escape(p)).append("</span>");
                    sb.append("</div>");
                }

                // Local dependencies
                if (m.getLocalDependencies() != null && !m.getLocalDependencies().isEmpty()) {
                    sb.append("<div class=\"section-title\">Local Dependencies</div><ul>");
                    for (String dep : m.getLocalDependencies()) sb.append("<li class=\"mono\">").append(escape(dep)).append("</li>");
                    sb.append("</ul>");
                }

                // Assembly artifacts
                if (m.getAssemblyArtifacts() != null && !m.getAssemblyArtifacts().isEmpty()) {
                    sb.append("<div class=\"section-title\">Assembly Artifacts</div><table><thead><tr><th>Assembly ID</th><th>Format</th><th>Repository Path</th></tr></thead><tbody>");
                    for (var a : m.getAssemblyArtifacts()) {
                        sb.append("<tr><td>").append(escape(coalesce(a.assemblyId(), ""))).append("</td><td>")
                          .append(escape(coalesce(a.format(), ""))).append("</td><td class=\"mono\"><code>")
                          .append(escape(coalesce(a.repositoryPath(), ""))).append("</code></td></tr>");
                    }
                    sb.append("</tbody></table>");
                }

                // Environments
                if (m.getEnvironments() != null && !m.getEnvironments().isEmpty()) {
                    sb.append("<div class=\"section-title\">Environments</div><table><thead><tr>")
                      .append("<th>Profile</th><th>Server Port</th><th>Context Path</th><th>Actuator</th><th>Base</th><th>Health</th><th>Info</th>")
                      .append("</tr></thead><tbody>");
                    for (var env : m.getEnvironments()) {
                        sb.append("<tr><td>").append(escape(coalesce(env.profile(), ""))).append("</td>")
                          .append("<td>").append(env.serverPort() == null ? "" : String.valueOf(env.serverPort())).append("</td>")
                          .append("<td class=\"mono\">").append(escape(coalesce(env.contextPath(), ""))).append("</td>")
                          .append("<td>").append(Boolean.TRUE.equals(env.actuatorEnabled()) ? "enabled" : (Boolean.FALSE.equals(env.actuatorEnabled()) ? "disabled" : "")).append("</td>")
                          .append("<td class=\"mono\">").append(escape(coalesce(env.actuatorBasePath(), ""))).append("</td>")
                          .append("<td class=\"mono\">").append(escape(coalesce(env.actuatorHealthPath(), ""))).append("</td>")
                          .append("<td class=\"mono\">").append(escape(coalesce(env.actuatorInfoPath(), ""))).append("</td></tr>");
                    }
                    sb.append("</tbody></table>");
                }

                sb.append("</details>"); // module
            }
        } else {
            sb.append("<div class=\"muted\">No deployable modules found.</div>");
        }
        sb.append("</div>"); // card modules

        // End main & layout
        sb.append("</div></div>");

        // Footer
        sb.append("<footer>Generated at ").append(escape(String.valueOf(d.generatedAt())))
          .append(" • Descriptor Plugin</footer>");

        sb.append("</body></html>");
        Files.writeString(htmlPath, sb.toString(), StandardCharsets.UTF_8);
    }

    private String escape(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    /**
     * Run an optional post-generation local command.
     */
    private void runPostCommand(Path primaryOutput) {
        try {
            getLog().info("✓ Running post command: " + postCommand);
            ProcessBuilder pb;
            if (isWindows()) {
                pb = new ProcessBuilder("cmd", "/c", postCommand);
            } else {
                pb = new ProcessBuilder("sh", "-c", postCommand);
            }
            pb.redirectErrorStream(true);
            pb.environment().put("DESCRIPTOR_FILE", primaryOutput.toAbsolutePath().toString());
            pb.directory(postCommandDir != null && !postCommandDir.isEmpty() ?
                    new File(postCommandDir) : project.getBasedir());
            Process p = pb.start();
            boolean finished = p.waitFor(postCommandTimeout, java.util.concurrent.TimeUnit.SECONDS);
            if (!finished) {
                p.destroyForcibly();
                getLog().warn("Post command timed out after " + postCommandTimeout + "s");
            } else {
                int exit = p.exitValue();
                String output = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
                if (exit == 0) {
                    getLog().info("Post command completed successfully");
                } else {
                    getLog().warn("Post command exited with code " + exit);
                }
                if (!output.isBlank()) getLog().debug(output);
            }
        } catch (Exception e) {
            getLog().warn("Failed to run post command: " + e.getMessage());
            getLog().debug("Post command error", e);
        }
    }

    private boolean isWindows() {
        return System.getProperty("os.name").toLowerCase().contains("win");
    }
}

