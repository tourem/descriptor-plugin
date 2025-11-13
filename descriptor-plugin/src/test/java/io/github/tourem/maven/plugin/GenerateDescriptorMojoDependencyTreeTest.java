package io.github.tourem.maven.plugin;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.maven.project.MavenProject;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * E2E tests for dependency tree feature integration in the Mojo.
 * Verifies JSON contains dependency section when enabled and ignored when disabled.
 * @author tourem
 */
public class GenerateDescriptorMojoDependencyTreeTest {

    @TempDir
    Path tempDir;

    @Test
    void shouldIncludeDependencyTreeForExecutableModule_flatCompileRuntime() throws Exception {
        // Arrange: create simple Spring Boot module with dependencies
        Path projectDir = tempDir.resolve("boot-deps");
        Files.createDirectories(projectDir);
        String pom = """
                <project xmlns=\"http://maven.apache.org/POM/4.0.0\"
                         xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\"
                         xsi:schemaLocation=\"http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd\">
                  <modelVersion>4.0.0</modelVersion>
                  <groupId>com.example</groupId>
                  <artifactId>demo</artifactId>
                  <version>1.0.0</version>
                  <properties>
                    <maven.compiler.source>17</maven.compiler.source>
                    <maven.compiler.target>17</maven.compiler.target>
                    <project.build.sourceEncoding>UTF-8</project.build.sourceEncoding>
                    <main.class>com.example.demo.DemoApplication</main.class>
                  </properties>
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
        Files.writeString(projectDir.resolve("pom.xml"), pom);

        GenerateDescriptorMojo mojo = new GenerateDescriptorMojo();
        MavenProject mvnProject = new MavenProject();
        mvnProject.setGroupId("com.example");
        mvnProject.setArtifactId("demo");
        mvnProject.setVersion("1.0.0");
        File pomFile = projectDir.resolve("pom.xml").toFile();
        mvnProject.setFile(pomFile);
        setBasedir(mvnProject, pomFile.getParentFile());
        setField(mojo, "project", mvnProject);
        setField(mojo, "outputDirectory", projectDir.resolve("target").toString());
        setField(mojo, "outputFile", "descriptor.json");
        setField(mojo, "exportFormat", "json");
        setField(mojo, "prettyPrint", true);
        setField(mojo, "format", "zip");
        setField(mojo, "attach", false);
        setField(mojo, "generateHtml", false);
        setField(mojo, "classifier", "descriptor");


        // Enable dependency tree and limit scopes to compile,runtime
        setField(mojo, "includeDependencyTree", true);
        setField(mojo, "dependencyTreeDepth", -1);
        setField(mojo, "dependencyScopes", "compile,runtime");
        setField(mojo, "dependencyTreeFormat", "flat");
        setField(mojo, "excludeTransitive", false);
        setField(mojo, "includeOptional", false);

        // Act
        mojo.execute();

        // Assert: read JSON and validate dependencies.flat contains compile+runtime deps only
        Path jsonPath = projectDir.resolve("target/descriptor.json");
        String json = Files.readString(jsonPath);
        ObjectMapper mapper = new ObjectMapper();
        JsonNode root = mapper.readTree(json);

        assertThat(root.path("deployableModules").isArray()).isTrue();
        JsonNode first = root.path("deployableModules").get(0);
        assertThat(first).isNotNull();
        JsonNode deps = first.path("dependencies");
        assertThat(deps.isMissingNode()).isFalse();
        assertThat(deps.path("flat").isArray()).isTrue();
        String flat = deps.path("flat").toString();
        assertThat(flat).contains("spring-boot-starter-web"); // compile
        assertThat(flat).contains("snakeyaml"); // runtime
        assertThat(flat).doesNotContain("lombok"); // provided excluded by default

        // Clean
        assertThat(Files.exists(projectDir.resolve("target/demo-1.0.0-descriptor.zip"))).isTrue();
    }

    // Helpers (copied pattern from other tests)
    private static void setField(Object target, String fieldName, Object value) throws Exception {
        var f = GenerateDescriptorMojo.class.getDeclaredField(fieldName);
        f.setAccessible(true);
        f.set(target, value);
    }

    private static void setBasedir(MavenProject project, File basedir) throws Exception {
        try {
            java.lang.reflect.Field basedirField = MavenProject.class.getDeclaredField("basedir");
            basedirField.setAccessible(true);
            basedirField.set(project, basedir);
        } catch (NoSuchFieldException ignored) { }
    }
}

