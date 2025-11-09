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

            // Send webhook notification if configured
            if (webhookUrl != null && !webhookUrl.trim().isEmpty()) {
                sendWebhookNotification(descriptor, primaryOutput);
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
}

