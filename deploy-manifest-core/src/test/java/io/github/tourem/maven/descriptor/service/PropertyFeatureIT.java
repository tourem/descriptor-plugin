package io.github.tourem.maven.descriptor.service;

import io.github.tourem.maven.descriptor.model.ProjectDescriptor;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

public class PropertyFeatureIT {

    @TempDir
    Path tempDir;

    @Test
    void shouldNotCollectProperties_whenFeatureDisabled() throws Exception {
        // given
        Path pom = writePom("com.acme", "demo-app",
                "<properties>\n" +
                        "  <spring-boot.version>3.2.5</spring-boot.version>\n" +
                        "  <database.password>secret</database.password>\n" +
                        "</properties>\n");

        var analyzer = new MavenProjectAnalyzer(
                io.github.tourem.maven.descriptor.model.DependencyTreeOptions.builder().include(false).build(),
                io.github.tourem.maven.descriptor.model.LicenseOptions.builder().include(false).build(),
                io.github.tourem.maven.descriptor.model.PropertyOptions.builder().include(false).build()
        );

        // when
        ProjectDescriptor descriptor = analyzer.analyzeProject(pom.getParent());

        // then
        assertThat(descriptor).isNotNull();
        assertThat(descriptor.buildInfo()).isNotNull();
        assertThat(descriptor.buildInfo().properties()).isNull();
    }

    @Test
    void shouldCollectProperties_andProfiles_andMaskSensitive() throws Exception {
        // given
        Path pom = writePom("com.acme", "demo-app",
                "<properties>\n" +
                        "  <spring-boot.version>3.2.5</spring-boot.version>\n" +
                        "  <database.password>supersecret</database.password>\n" +
                        "  <maven.compiler.source>17</maven.compiler.source>\n" +
                        "</properties>\n" +
                        "<profiles>\n" +
                        "  <profile>\n" +
                        "    <id>dev</id>\n" +
                        "    <activation><activeByDefault>true</activeByDefault></activation>\n" +
                        "  </profile>\n" +
                        "  <profile><id>prod</id></profile>\n" +
                        "</profiles>\n");

        var propOpts = io.github.tourem.maven.descriptor.model.PropertyOptions.builder()
                .include(true)
                .includeSystemProperties(false)
                .includeEnvironmentVariables(false)
                .filterSensitiveProperties(true)
                .maskSensitiveValues(true)
                .build();
        var analyzer = new MavenProjectAnalyzer(
                io.github.tourem.maven.descriptor.model.DependencyTreeOptions.builder().include(false).build(),
                io.github.tourem.maven.descriptor.model.LicenseOptions.builder().include(false).build(),
                propOpts
        );

        // when
        ProjectDescriptor descriptor = analyzer.analyzeProject(pom.getParent());

        // then
        assertThat(descriptor.buildInfo().properties()).isNotNull();
        var props = descriptor.buildInfo().properties();
        assertThat(props.getProject()).containsEntry("project.groupId", "com.acme");
        assertThat(props.getProject()).containsEntry("project.artifactId", "demo-app");
        assertThat(props.getCustom()).containsEntry("spring-boot.version", "3.2.5");
        assertThat(props.getMaven()).containsEntry("maven.compiler.source", "17");
        assertThat(props.getCustom()).containsEntry("database.password", "***MASKED***");
        assertThat(props.getMaskedCount()).isGreaterThanOrEqualTo(1);

        assertThat(descriptor.buildInfo().profiles()).isNotNull();
        var profiles = descriptor.buildInfo().profiles();
        assertThat(profiles.getAvailable()).contains("dev", "prod");
        assertThat(profiles.getDefaultProfile()).isEqualTo("dev");
    }

    private Path writePom(String groupId, String artifactId, String extra) throws IOException {
        String xml = "" +
                "<project xmlns=\"http://maven.apache.org/POM/4.0.0\"\n" +
                "         xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\"\n" +
                "         xsi:schemaLocation=\"http://maven.apache.org/POM/4.0.0\n" +
                "                             http://maven.apache.org/xsd/maven-4.0.0.xsd\">\n" +
                "  <modelVersion>4.0.0</modelVersion>\n" +
                "  <groupId>" + groupId + "</groupId>\n" +
                "  <artifactId>" + artifactId + "</artifactId>\n" +
                "  <version>1.0.0</version>\n" +
                "  <packaging>jar</packaging>\n" +
                "  <name>" + artifactId + "</name>\n" +
                extra +
                "</project>\n";
        Path pom = tempDir.resolve("pom.xml");
        Files.writeString(pom, xml);
        return pom;
    }
}

