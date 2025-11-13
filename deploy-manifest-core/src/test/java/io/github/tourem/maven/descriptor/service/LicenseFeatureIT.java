package io.github.tourem.maven.descriptor.service;

import io.github.tourem.maven.descriptor.model.LicenseOptions;
import io.github.tourem.maven.descriptor.model.ProjectDescriptor;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class LicenseFeatureIT {

    @TempDir
    Path tempDir;

    @Test
    void shouldNotCollectLicenses_whenFeatureDisabled() throws Exception {
        Path projectDir = tempDir.resolve("boot-disabled");
        Files.createDirectories(projectDir);
        Files.writeString(projectDir.resolve("pom.xml"), bootPomWithDeps());

        LicenseOptions lopts = LicenseOptions.builder()
                .include(false)
                .build();

        MavenProjectAnalyzer analyzer = new MavenProjectAnalyzer(
                io.github.tourem.maven.descriptor.model.DependencyTreeOptions.builder().include(false).build(),
                lopts
        );
        ProjectDescriptor descriptor = analyzer.analyzeProject(projectDir);

        assertThat(descriptor.deployableModules()).hasSize(1);
        assertThat(descriptor.deployableModules().get(0).getLicenses()).isNull();
    }

    @Test
    void shouldCollectUnknownLicenses_forDirectDependencies_whenEnabled() throws Exception {
        Path projectDir = tempDir.resolve("boot-enabled");
        Files.createDirectories(projectDir);
        Files.writeString(projectDir.resolve("pom.xml"), bootPomWithDeps());

        LicenseOptions lopts = LicenseOptions.builder()
                .include(true)
                .licenseWarnings(true)
                .includeTransitiveLicenses(false)
                .build();

        MavenProjectAnalyzer analyzer = new MavenProjectAnalyzer(
                io.github.tourem.maven.descriptor.model.DependencyTreeOptions.builder().include(false).build(),
                lopts
        );
        ProjectDescriptor descriptor = analyzer.analyzeProject(projectDir);

        var lic = descriptor.deployableModules().get(0).getLicenses();
        assertThat(lic).isNotNull();
        assertThat(lic.getSummary()).isNotNull();
        // compile + runtime only => 2 dependencies in fixture
        assertThat(lic.getSummary().getTotal()).isEqualTo(2);
        assertThat(lic.getSummary().getUnknown()).isEqualTo(0);
        // No unknown => no warnings even if licenseWarnings=true
        assertThat(lic.getWarnings() == null || lic.getWarnings().isEmpty()).isTrue();
        assertThat(lic.getCompliance()).isNotNull();
        assertThat(lic.getCompliance().getUnknownCount()).isEqualTo(0);
        assertThat(lic.getCompliance().getHasIncompatibleLicenses()).isFalse();
        assertThat(lic.getCompliance().getCommerciallyViable()).isTrue();
    }

    private static String bootPomWithDeps() {
        return """
            <project xmlns=\"http://maven.apache.org/POM/4.0.0\"
                     xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\"
                     xsi:schemaLocation=\"http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd\">
              <modelVersion>4.0.0</modelVersion>
              <groupId>com.example</groupId>
              <artifactId>demo</artifactId>
              <version>1.0.0</version>
              <dependencies>
                <dependency>
                  <groupId>org.springframework.boot</groupId>
                  <artifactId>spring-boot-starter-web</artifactId>
                  <version>3.2.0</version>
                </dependency>
                <dependency>
                  <groupId>org.yaml</groupId>
                  <artifactId>snakeyaml</artifactId>
                  <version>2.2</version>
                  <scope>runtime</scope>
                </dependency>
                <dependency>
                  <groupId>org.projectlombok</groupId>
                  <artifactId>lombok</artifactId>
                  <version>1.18.32</version>
                  <scope>provided</scope>
                </dependency>
              </dependencies>
              <build>
                <plugins>
                  <plugin>
                    <groupId>org.springframework.boot</groupId>
                    <artifactId>spring-boot-maven-plugin</artifactId>
                    <version>3.2.0</version>
                    <executions>
                      <execution>
                        <goals><goal>repackage</goal></goals>
                      </execution>
                    </executions>
                  </plugin>
                </plugins>
              </build>
            </project>
            """;
    }
}

