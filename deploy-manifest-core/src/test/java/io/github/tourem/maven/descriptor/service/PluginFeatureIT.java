package io.github.tourem.maven.descriptor.service;

import io.github.tourem.maven.descriptor.model.ProjectDescriptor;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

public class PluginFeatureIT {

    @TempDir
    Path tempDir;

    @Test
    void shouldNotCollectPlugins_whenFeatureDisabled() throws Exception {
        // given
        Path pom = writePom("com.acme", "demo-app",
                "<build>\n" +
                        "  <plugins>\n" +
                        "    <plugin>\n" +
                        "      <groupId>org.apache.maven.plugins</groupId>\n" +
                        "      <artifactId>maven-compiler-plugin</artifactId>\n" +
                        "      <version>3.11.0</version>\n" +
                        "    </plugin>\n" +
                        "  </plugins>\n" +
                        "</build>\n");

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
        assertThat(descriptor.buildInfo().plugins()).isNull();
    }

    @Test
    void shouldCollectPlugins_withConfig_andMgmt_andMasking() throws Exception {
        // given
        Path pom = writePom("com.acme", "demo-app",
                "<build>\n" +
                        "  <pluginManagement>\n" +
                        "    <plugins>\n" +
                        "      <plugin>\n" +
                        "        <groupId>org.apache.maven.plugins</groupId>\n" +
                        "        <artifactId>maven-deploy-plugin</artifactId>\n" +
                        "        <version>3.1.1</version>\n" +
                        "      </plugin>\n" +
                        "    </plugins>\n" +
                        "  </pluginManagement>\n" +
                        "  <plugins>\n" +
                        "    <plugin>\n" +
                        "      <groupId>org.apache.maven.plugins</groupId>\n" +
                        "      <artifactId>maven-compiler-plugin</artifactId>\n" +
                        "      <version>3.11.0</version>\n" +
                        "      <executions>\n" +
                        "        <execution>\n" +
                        "          <id>default-compile</id>\n" +
                        "          <phase>compile</phase>\n" +
                        "          <goals><goal>compile</goal></goals>\n" +
                        "        </execution>\n" +
                        "      </executions>\n" +
                        "      <configuration>\n" +
                        "        <source>17</source>\n" +
                        "        <username>alice</username>\n" +
                        "        <password>s3cr3t</password>\n" +
                        "      </configuration>\n" +
                        "    </plugin>\n" +
                        "  </plugins>\n" +
                        "</build>\n");

        var pluginOpts = io.github.tourem.maven.descriptor.model.PluginOptions.builder()
                .include(true)
                .includePluginConfiguration(true)
                .includePluginManagement(true)
                .checkPluginUpdates(false)
                .filterSensitivePluginConfig(true)
                .build();
        var analyzer = new MavenProjectAnalyzer(
                io.github.tourem.maven.descriptor.model.DependencyTreeOptions.builder().include(false).build(),
                io.github.tourem.maven.descriptor.model.LicenseOptions.builder().include(false).build(),
                io.github.tourem.maven.descriptor.model.PropertyOptions.builder().include(false).build(),
                pluginOpts
        );

        // when
        ProjectDescriptor descriptor = analyzer.analyzeProject(pom.getParent());

        // then
        assertThat(descriptor.buildInfo().plugins()).isNotNull();
        var plugins = descriptor.buildInfo().plugins();
        assertThat(plugins.getSummary().getTotal()).isEqualTo(1);
        assertThat(plugins.getSummary().getWithConfiguration()).isEqualTo(1);
        assertThat(plugins.getSummary().getFromManagement()).isEqualTo(1);

        assertThat(plugins.getList()).hasSize(1);
        var compiler = plugins.getList().get(0);
        assertThat(compiler.getGroupId()).isEqualTo("org.apache.maven.plugins");
        assertThat(compiler.getArtifactId()).isEqualTo("maven-compiler-plugin");
        assertThat(compiler.getVersion()).isEqualTo("3.11.0");
        assertThat(compiler.getPhase()).isEqualTo("compile");
        assertThat(compiler.getGoals()).contains("compile");
        assertThat(compiler.getConfiguration()).as("config map present").isNotNull();
        @SuppressWarnings("unchecked")
        var cfg = (java.util.Map<String, Object>) compiler.getConfiguration();
        assertThat(cfg).containsEntry("source", "17");
        assertThat(cfg).containsEntry("username", "***MASKED***");
        assertThat(cfg).containsEntry("password", "***MASKED***");

        assertThat(plugins.getManagement()).hasSize(1);
        var mgmt = plugins.getManagement().get(0);
        assertThat(mgmt.getArtifactId()).isEqualTo("maven-deploy-plugin");
        assertThat(Boolean.FALSE.equals(mgmt.getUsedInBuild())).isTrue();
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

