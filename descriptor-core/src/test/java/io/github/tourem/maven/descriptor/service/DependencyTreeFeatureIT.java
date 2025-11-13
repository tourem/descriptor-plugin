package io.github.tourem.maven.descriptor.service;

import io.github.tourem.maven.descriptor.model.DependencyTreeFormat;
import io.github.tourem.maven.descriptor.model.DependencyTreeOptions;
import io.github.tourem.maven.descriptor.model.ProjectDescriptor;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class DependencyTreeFeatureIT {

    @TempDir
    Path tempDir;

    @Test
    void shouldNotCollectDependencies_whenFeatureDisabled() throws Exception {
        Path projectDir = tempDir.resolve("boot-disabled");
        Files.createDirectories(projectDir);
        Files.writeString(projectDir.resolve("pom.xml"), bootPomWithDeps());

        DependencyTreeOptions opts = DependencyTreeOptions.builder()
                .include(false) // disabled
                .scopes(Set.of("compile","runtime"))
                .format(DependencyTreeFormat.FLAT)
                .build();

        MavenProjectAnalyzer analyzer = new MavenProjectAnalyzer(opts);
        ProjectDescriptor descriptor = analyzer.analyzeProject(projectDir);

        assertThat(descriptor.deployableModules()).hasSize(1);
        assertThat(descriptor.deployableModules().get(0).getDependencies()).isNull();
    }

    @Test
    void shouldCollectDependencies_forSpringBootExecutable_whenEnabled_flat() throws Exception {
        Path projectDir = tempDir.resolve("boot-enabled");
        Files.createDirectories(projectDir);
        Files.writeString(projectDir.resolve("pom.xml"), bootPomWithDeps());

        DependencyTreeOptions opts = DependencyTreeOptions.builder()
                .include(true)
                .scopes(Set.of("compile","runtime"))
                .format(DependencyTreeFormat.FLAT)
                .build();

        MavenProjectAnalyzer analyzer = new MavenProjectAnalyzer(opts);
        ProjectDescriptor descriptor = analyzer.analyzeProject(projectDir);

        var deps = descriptor.deployableModules().get(0).getDependencies();
        assertThat(deps).isNotNull();
        assertThat(deps.getFlat().toString()).contains("spring-boot-starter-web"); // compile
        assertThat(deps.getFlat().toString()).contains("snakeyaml"); // runtime
        assertThat(deps.getFlat().toString()).doesNotContain("lombok"); // provided excluded
    }

    @Test
    void shouldNotCollectForNonExecutableModule_evenWhenEnabled() throws Exception {
        Path projectDir = tempDir.resolve("plain-jar");
        Files.createDirectories(projectDir);
        Files.writeString(projectDir.resolve("pom.xml"), plainJarPomWithDeps());

        DependencyTreeOptions opts = DependencyTreeOptions.builder()
                .include(true)
                .scopes(Set.of("compile","runtime"))
                .format(DependencyTreeFormat.FLAT)
                .build();

        MavenProjectAnalyzer analyzer = new MavenProjectAnalyzer(opts);
        ProjectDescriptor descriptor = analyzer.analyzeProject(projectDir);

        assertThat(descriptor.deployableModules()).hasSize(1);
        assertThat(descriptor.deployableModules().get(0).getDependencies()).isNull();
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

    private static String plainJarPomWithDeps() {
        return """
            <project xmlns=\"http://maven.apache.org/POM/4.0.0\"
                     xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\"
                     xsi:schemaLocation=\"http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd\">
              <modelVersion>4.0.0</modelVersion>
              <groupId>com.example</groupId>
              <artifactId>plain</artifactId>
              <version>1.0.0</version>
              <dependencies>
                <dependency>
                  <groupId>org.yaml</groupId>
                  <artifactId>snakeyaml</artifactId>
                  <version>2.2</version>
                </dependency>
              </dependencies>
              <packaging>jar</packaging>
            </project>
            """;
    }
}

