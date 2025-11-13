package io.github.tourem.maven.plugin;

import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;
import org.apache.commons.compress.compressors.gzip.GzipCompressorInputStream;
import org.apache.maven.project.MavenProject;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.FileInputStream;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import static org.assertj.core.api.Assertions.assertThat;

public class GenerateDescriptorMojoArchiveTest {

    private Path tempDir;

    @BeforeEach
    void setUp() throws Exception {
        tempDir = Files.createTempDirectory("deploy-manifest-plugin-test-");
    }

    @AfterEach
    void tearDown() throws Exception {
        if (tempDir != null) {
            // Cleanup generated files
            Files.walk(tempDir)
                .sorted((a,b) -> b.getNameCount() - a.getNameCount())
                .forEach(p -> {
                    try { Files.deleteIfExists(p); } catch (Exception ignored) {}
                });
        }
    }

    @Test
    void zip_contains_json_only_when_export_json() throws Exception {
        File archive = runMojo("json", false, false, "zip");
        assertThat(archive).exists();

        Set<String> names = zipEntries(archive);
        assertThat(names).contains("descriptor.json");
        assertThat(names).doesNotContain("descriptor.yaml");
    }

    @Test
    void zip_contains_yaml_only_when_export_yaml() throws Exception {
        File archive = runMojo("yaml", false, false, "zip");
        assertThat(archive).exists();

        Set<String> names = zipEntries(archive);
        assertThat(names).contains("descriptor.yaml");
        assertThat(names).doesNotContain("descriptor.json");
    }

    @Test
    void zip_contains_json_and_yaml_when_export_both() throws Exception {
        File archive = runMojo("both", false, false, "zip");
        assertThat(archive).exists();

        Set<String> names = zipEntries(archive);
        assertThat(names).contains("descriptor.json", "descriptor.yaml");
    }

    @Test
    void zip_contains_html_when_generateHtml_true() throws Exception {
        File archive = runMojo("json", true, false, "zip");
        assertThat(archive).exists();

        Set<String> names = zipEntries(archive);
        assertThat(names).contains("descriptor.json", "descriptor.html");
    }

    @Test
    void zip_contains_json_gz_when_compress_true() throws Exception {
        File archive = runMojo("json", false, true, "zip");
        assertThat(archive).exists();

        Set<String> names = zipEntries(archive);
        assertThat(names).contains("descriptor.json", "descriptor.json.gz");
    }

    @Test
    void targz_contains_json_and_yaml_when_export_both() throws Exception {
        File archive = runMojo("both", false, false, "tar.gz");
        assertThat(archive).exists();

        Set<String> names = tarGzEntries(archive);
        assertThat(names).contains("descriptor.json", "descriptor.yaml");
    }

    // --- helpers ---
    @Test
    void tarbz2_contains_json_and_yaml_when_export_both() throws Exception {
        File archive = runMojo("both", false, false, "tar.bz2");
        assertThat(archive).exists();

        Set<String> names = tarEntriesBz2(archive);
        assertThat(names).contains("descriptor.json", "descriptor.yaml");
    }

    @Test
    void jar_behaves_like_zip_and_contains_both_when_export_both() throws Exception {
        File archive = runMojo("both", false, false, "jar");
        assertThat(archive).exists();

        Set<String> names = zipEntries(archive);
        assertThat(names).contains("descriptor.json", "descriptor.yaml");
    }

    @Test
    void zip_contains_json_yaml_and_gz_when_both_and_compress_true() throws Exception {
        File archive = runMojo("both", false, true, "zip");
        assertThat(archive).exists();

        Set<String> names = zipEntries(archive);
        assertThat(names).contains("descriptor.json", "descriptor.yaml", "descriptor.json.gz");
    }

    @Test
    void zip_contains_yaml_and_html_when_yaml_and_html_true() throws Exception {
        File archive = runMojo("yaml", true, false, "zip");
        assertThat(archive).exists();

        Set<String> names = zipEntries(archive);
        assertThat(names).contains("descriptor.yaml", "descriptor.html");
        assertThat(names).doesNotContain("descriptor.json");
    }

    @Test
    void zip_contains_json_yaml_and_html_when_both_and_html_true() throws Exception {
        File archive = runMojo("both", true, false, "zip");
        assertThat(archive).exists();

        Set<String> names = zipEntries(archive);
        assertThat(names).contains("descriptor.json", "descriptor.yaml", "descriptor.html");
    }

    @Test
    void jar_contains_json_yaml_and_html_when_both_and_html_true() throws Exception {
        File archive = runMojo("both", true, false, "jar");
        assertThat(archive).exists();

        Set<String> names = zipEntries(archive);
        assertThat(names).contains("descriptor.json", "descriptor.yaml", "descriptor.html");
    }

    @Test
    void targz_contains_json_and_html_when_json_and_html_true() throws Exception {
        File archive = runMojo("json", true, false, "tar.gz");
        assertThat(archive).exists();

        Set<String> names = tarGzEntries(archive);
        assertThat(names).contains("descriptor.json", "descriptor.html");
    }

    @Test
    void targz_contains_json_yaml_and_gz_when_both_and_compress_true() throws Exception {
        File archive = runMojo("both", false, true, "tar.gz");
        assertThat(archive).exists();

        Set<String> names = tarGzEntries(archive);
        assertThat(names).contains("descriptor.json", "descriptor.yaml", "descriptor.json.gz");
    }

    @Test
    void tarbz2_contains_json_yaml_and_gz_when_both_and_compress_true() throws Exception {
        File archive = runMojo("both", false, true, "tar.bz2");
        assertThat(archive).exists();

        Set<String> names = tarEntriesBz2(archive);
        assertThat(names).contains("descriptor.json", "descriptor.yaml", "descriptor.json.gz");
    }


    private File runMojo(String exportFormat, boolean generateHtml, boolean compress, String archiveFormat) throws Exception {
        GenerateDescriptorMojo mojo = new GenerateDescriptorMojo();

        MavenProject project = new MavenProject();
        project.setArtifactId("test-artifact");
        project.setVersion("1.0.0");
        // Point to the deploy-manifest-plugin module as the project under analysis (current module)
        File modulePom = Paths.get("pom.xml").toFile();
        project.setFile(modulePom);
        // Ensure basedir is set for the analyzer
        try {
            Field basedirField = MavenProject.class.getDeclaredField("basedir");
            basedirField.setAccessible(true);
            basedirField.set(project, modulePom.getParentFile());
        } catch (NoSuchFieldException ignored) {
            // Fall back to setFile's behavior
        }

        setField(mojo, "project", project);
        setField(mojo, "outputDirectory", tempDir.toString());
        setField(mojo, "outputFile", "descriptor.json");
        setField(mojo, "exportFormat", exportFormat);
        setField(mojo, "generateHtml", generateHtml);
        setField(mojo, "compress", compress);
        setField(mojo, "format", archiveFormat);
        setField(mojo, "attach", false);
        setField(mojo, "skip", false);
        setField(mojo, "summary", false);
        setField(mojo, "classifier", "descriptor");

        mojo.execute();

        String ext;
        switch (archiveFormat) {
            case "tar.gz":
            case "tgz":
                ext = ".tar.gz";
                break;
            case "tar.bz2":
            case "tbz2":
                ext = ".tar.bz2";
                break;
            case "zip":
            case "jar":
            default:
                ext = ".zip";
        }
        String archiveName = "test-artifact-1.0.0-descriptor" + ext;
        return tempDir.resolve(archiveName).toFile();
    }

    private static void setField(Object target, String fieldName, Object value) throws Exception {
        Field f = target.getClass().getDeclaredField(fieldName);
        f.setAccessible(true);
        f.set(target, value);
    }

    private static Set<String> zipEntries(File zip) throws Exception {
        Set<String> names = new HashSet<>();
        try (ZipFile zf = new ZipFile(zip)) {
            Enumeration<? extends ZipEntry> entries = zf.entries();
            while (entries.hasMoreElements()) {
                names.add(entries.nextElement().getName());
            }
        }
        return names;
    }

    private static Set<String> tarGzEntries(File tarGz) throws Exception {
        Set<String> names = new HashSet<>();
        try (FileInputStream fis = new FileInputStream(tarGz);
             GzipCompressorInputStream gzis = new GzipCompressorInputStream(fis);
             TarArchiveInputStream tais = new TarArchiveInputStream(gzis)) {
            TarArchiveEntry entry;
            while ((entry = tais.getNextTarEntry()) != null) {
                if (entry.isFile()) {
                    names.add(entry.getName());
                }
            }
        }
        return names;
    }
    private static Set<String> tarEntriesBz2(File tarBz2) throws Exception {
        Set<String> names = new HashSet<>();
        try (FileInputStream fis = new FileInputStream(tarBz2);
             org.apache.commons.compress.compressors.bzip2.BZip2CompressorInputStream bzIn = new org.apache.commons.compress.compressors.bzip2.BZip2CompressorInputStream(fis);
             TarArchiveInputStream tais = new TarArchiveInputStream(bzIn)) {
            TarArchiveEntry entry;
            while ((entry = tais.getNextTarEntry()) != null) {
                if (entry.isFile()) {
                    names.add(entry.getName());
                }
            }
        }
        return names;
    }

}

