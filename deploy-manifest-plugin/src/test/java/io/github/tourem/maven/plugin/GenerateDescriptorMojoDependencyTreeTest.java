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

    @Test
    void shouldIncludeDependencyTree_treeFormat() throws Exception {
        Path projectDir = tempDir.resolve("boot-deps-tree");
        Files.createDirectories(projectDir);
        String pom = """
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
                          <execution><goals><goal>repackage</goal></goals></execution>
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

        setField(mojo, "includeDependencyTree", true);
        setField(mojo, "dependencyTreeDepth", -1);
        setField(mojo, "dependencyScopes", "compile,runtime");
        setField(mojo, "dependencyTreeFormat", "tree");
        setField(mojo, "excludeTransitive", false);
        setField(mojo, "includeOptional", false);

        mojo.execute();

        Path jsonPath = projectDir.resolve("target/descriptor.json");
        String json = Files.readString(jsonPath);
        ObjectMapper mapper = new ObjectMapper();
        JsonNode root = mapper.readTree(json);
        JsonNode deps = root.path("deployableModules").get(0).path("dependencies");
        assertThat(deps.isMissingNode()).isFalse();
        assertThat(deps.has("flat")).isFalse();
        assertThat(deps.path("tree").isArray()).isTrue();
        assertThat(deps.path("tree").toString()).contains("spring-boot-starter-web", "snakeyaml");
    }

    @Test
    void shouldIncludeDependencyTree_bothFormat() throws Exception {
        Path projectDir = tempDir.resolve("boot-deps-both");
        Files.createDirectories(projectDir);
        String pom = """
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
                  </dependencies>
                  <build>
                    <plugins>
                      <plugin>
                        <groupId>org.springframework.boot</groupId>
                        <artifactId>spring-boot-maven-plugin</artifactId>
                        <version>3.2.0</version>
                        <executions>
                          <execution><goals><goal>repackage</goal></goals></execution>
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
        setField(mojo, "generateHtml", true); // also test HTML is written alongside
        setField(mojo, "classifier", "descriptor");

        setField(mojo, "includeDependencyTree", true);
        setField(mojo, "dependencyTreeDepth", -1);
        setField(mojo, "dependencyScopes", "compile,runtime");
        setField(mojo, "dependencyTreeFormat", "both");
        setField(mojo, "excludeTransitive", false);
        setField(mojo, "includeOptional", false);

        mojo.execute();

        Path jsonPath = projectDir.resolve("target/descriptor.json");
        String json = Files.readString(jsonPath);
        ObjectMapper mapper = new ObjectMapper();
        JsonNode root = mapper.readTree(json);
        JsonNode deps = root.path("deployableModules").get(0).path("dependencies");
        assertThat(deps.has("flat")).isTrue();
        assertThat(deps.has("tree")).isTrue();

        // HTML should exist and contain Dependencies section
        Path htmlPath = projectDir.resolve("target/descriptor.html");
        assertThat(Files.exists(htmlPath)).isTrue();
        String html = Files.readString(htmlPath);
        assertThat(html).contains("Dependencies");
        assertThat(html).contains("dep-table-demo");
    }

    @Test
    void shouldIncludeOptionalDependencies_whenEnabled() throws Exception {
        Path projectDir = tempDir.resolve("boot-optional");
        Files.createDirectories(projectDir);
        String pom = """
                <project xmlns=\"http://maven.apache.org/POM/4.0.0\"
                         xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\"
                         xsi:schemaLocation=\"http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd\">
                  <modelVersion>4.0.0</modelVersion>
                  <groupId>com.example</groupId>
                  <artifactId>demo</artifactId>
                  <version>1.0.0</version>
                  <dependencies>
                    <dependency>
                      <groupId>org.example</groupId>
                      <artifactId>opt</artifactId>
                      <version>1.0</version>
                      <optional>true</optional>
                    </dependency>
                  </dependencies>
                  <build>
                    <plugins>
                      <plugin>
                        <groupId>org.springframework.boot</groupId>
                        <artifactId>spring-boot-maven-plugin</artifactId>
                        <version>3.2.0</version>
                        <executions>
                          <execution><goals><goal>repackage</goal></goals></execution>
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

        setField(mojo, "includeDependencyTree", true);
        setField(mojo, "dependencyTreeDepth", -1);
        setField(mojo, "dependencyScopes", "compile,runtime");
        setField(mojo, "dependencyTreeFormat", "flat");
        setField(mojo, "excludeTransitive", false);
        setField(mojo, "includeOptional", true);

        mojo.execute();

        Path jsonPath = projectDir.resolve("target/descriptor.json");
        JsonNode deps = new ObjectMapper().readTree(Files.readString(jsonPath))
                .path("deployableModules").get(0).path("dependencies");
        assertThat(deps.path("flat").toString()).contains("\"artifactId\":\"opt\"", "\"optional\":true");
    }

    @Test
    void shouldCollectDependencies_forNonExecutableModule() throws Exception {
        // Since v2.6.0, dependency tree is collected for ALL deployable modules (not just executables)
        Path projectDir = tempDir.resolve("plain-jar-plugin");
        Files.createDirectories(projectDir);
        String pom = """
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
        Files.writeString(projectDir.resolve("pom.xml"), pom);

        GenerateDescriptorMojo mojo = new GenerateDescriptorMojo();
        MavenProject mvnProject = new MavenProject();
        mvnProject.setGroupId("com.example");
        mvnProject.setArtifactId("plain");
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
        setField(mojo, "classifier", "descriptor");

        setField(mojo, "includeDependencyTree", true);
        setField(mojo, "dependencyScopes", "compile,runtime");
        setField(mojo, "dependencyTreeFormat", "flat");

        mojo.execute();

        Path jsonPath = projectDir.resolve("target/descriptor.json");
        JsonNode deps = new ObjectMapper().readTree(Files.readString(jsonPath))
                .path("deployableModules").get(0).path("dependencies");
        // v2.6.0: Dependencies are now collected for all deployable modules
        assertThat(deps.isMissingNode()).isFalse();
        assertThat(deps.isNull()).isFalse();
        assertThat(deps.path("flat").isArray()).isTrue();
        assertThat(deps.path("flat").size()).isEqualTo(1);
        assertThat(deps.path("flat").get(0).path("artifactId").asText()).isEqualTo("snakeyaml");
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

