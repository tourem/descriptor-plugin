package io.github.tourem.maven.plugin;

import org.apache.maven.project.MavenProject;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.assertj.core.api.Assertions.assertThat;

public class GenerateDescriptorMojoContainerTest {

    @TempDir
    Path tempDir;

    @Test
    void generates_container_block_for_jib_project_in_json_and_html() throws Exception {
        Path projectRoot = Files.createTempDirectory("jib-project-");
        writeString(projectRoot.resolve("pom.xml"), jibPom());

        GenerateDescriptorMojo mojo = new GenerateDescriptorMojo();
        MavenProject project = new MavenProject();
        project.setArtifactId("demo");
        project.setVersion("1.0.0");
        File pomFile = projectRoot.resolve("pom.xml").toFile();
        project.setFile(pomFile);
        setBasedir(project, pomFile.getParentFile());

        setField(mojo, "project", project);
        setField(mojo, "outputDirectory", tempDir.toString());
        setField(mojo, "outputFile", "descriptor.json");
        setField(mojo, "exportFormat", "json");
        setField(mojo, "generateHtml", true);
        setField(mojo, "attach", false);
        setField(mojo, "skip", false);

        mojo.execute();

        Path json = Paths.get(tempDir.toString(), "descriptor.json");
        Path html = Paths.get(tempDir.toString(), "descriptor.html");
        assertThat(json).exists();
        assertThat(html).exists();

        String jsonContent = Files.readString(json);
        assertThat(jsonContent).contains("\"container\"");
        assertThat(jsonContent).contains("\"tool\":\"jib\"");
        assertThat(jsonContent).contains("\"image\":\"ghcr.io/acme/demo\"");
        assertThat(jsonContent).contains("\"tag\":\"1.0.0\"");
        assertThat(jsonContent).contains("\"additionalTags\"");

        String htmlContent = Files.readString(html);
        assertThat(htmlContent).contains("Container Image");
        assertThat(htmlContent).contains("ghcr.io/acme/demo");
    }

    @Test
    void generates_container_block_for_spring_boot_build_image() throws Exception {
        Path projectRoot = Files.createTempDirectory("boot-project-");
        writeString(projectRoot.resolve("pom.xml"), springBootPom());

        GenerateDescriptorMojo mojo = new GenerateDescriptorMojo();
        MavenProject project = new MavenProject();
        project.setArtifactId("demo");
        project.setVersion("1.0.0");
        File pomFile = projectRoot.resolve("pom.xml").toFile();
        project.setFile(pomFile);
        setBasedir(project, pomFile.getParentFile());

        setField(mojo, "project", project);
        setField(mojo, "outputDirectory", tempDir.toString());
        setField(mojo, "outputFile", "descriptor.json");
        setField(mojo, "exportFormat", "json");
        setField(mojo, "generateHtml", true);
        setField(mojo, "attach", false);
        setField(mojo, "skip", false);

        mojo.execute();

        Path json = Paths.get(tempDir.toString(), "descriptor.json");
        Path html = Paths.get(tempDir.toString(), "descriptor.html");
        assertThat(json).exists();
        assertThat(html).exists();

        String jsonContent = Files.readString(json);
        assertThat(jsonContent).contains("\"container\"");
        assertThat(jsonContent).contains("\"tool\":\"spring-boot\"");
        assertThat(jsonContent).contains("\"image\":\"docker.io/acme/demo\"");
        assertThat(jsonContent).contains("\"tag\":\"1.0.0\"");
        assertThat(jsonContent).contains("\"builderImage\":\"paketobuildpacks/builder-jammy-base\"");
        assertThat(jsonContent).contains("\"runImage\":\"paketobuildpacks/run-jammy-base\"");
        assertThat(jsonContent).contains("\"publish\":true");

        String htmlContent = Files.readString(html);
        assertThat(htmlContent).contains("Container Image");
        assertThat(htmlContent).contains("docker.io/acme/demo");
    }

    // --- helpers ---

    private static void setField(Object target, String fieldName, Object value) throws Exception {
        Field f = target.getClass().getDeclaredField(fieldName);
        f.setAccessible(true);
        f.set(target, value);
    }

    private static void setBasedir(MavenProject project, File basedir) throws Exception {
        try {
            Field basedirField = MavenProject.class.getDeclaredField("basedir");
            basedirField.setAccessible(true);
            basedirField.set(project, basedir);
        } catch (NoSuchFieldException ignored) { }
    }

    private static void writeString(Path path, String content) throws Exception {
        Files.createDirectories(path.getParent());
        Files.writeString(path, content);
    }

    private static String jibPom() {
        return "" +
            "<project xmlns=\"http://maven.apache.org/POM/4.0.0\" xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\"\n" +
            "  xsi:schemaLocation=\"http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd\">\n" +
            "  <modelVersion>4.0.0</modelVersion>\n" +
            "  <groupId>com.example</groupId>\n" +
            "  <artifactId>demo</artifactId>\n" +
            "  <version>1.0.0</version>\n" +
            "  <packaging>jar</packaging>\n" +
            "  <build>\n" +
            "    <plugins>\n" +
            "      <plugin>\n" +
            "        <groupId>com.google.cloud.tools</groupId>\n" +
            "        <artifactId>jib-maven-plugin</artifactId>\n" +
            "        <configuration>\n" +
            "          <to>\n" +
            "            <image>ghcr.io/acme/demo</image>\n" +
            "            <tags>\n" +
            "              <tag>1.0.0</tag>\n" +
            "              <tag>latest</tag>\n" +
            "            </tags>\n" +
            "          </to>\n" +
            "          <from>\n" +
            "            <image>eclipse-temurin:17-jre</image>\n" +
            "          </from>\n" +
            "        </configuration>\n" +
            "      </plugin>\n" +
            "    </plugins>\n" +
            "  </build>\n" +
            "</project>\n";
    }

    private static String springBootPom() {
        return "" +
            "<project xmlns=\"http://maven.apache.org/POM/4.0.0\" xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\"\n" +
            "  xsi:schemaLocation=\"http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd\">\n" +
            "  <modelVersion>4.0.0</modelVersion>\n" +
            "  <groupId>com.example</groupId>\n" +
            "  <artifactId>demo</artifactId>\n" +
            "  <version>1.0.0</version>\n" +
            "  <packaging>jar</packaging>\n" +
            "  <build>\n" +
            "    <plugins>\n" +
            "      <plugin>\n" +
            "        <groupId>org.springframework.boot</groupId>\n" +
            "        <artifactId>spring-boot-maven-plugin</artifactId>\n" +
            "        <executions>\n" +
            "          <execution>\n" +
            "            <goals><goal>build-image</goal></goals>\n" +
            "          </execution>\n" +
            "        </executions>\n" +
            "        <configuration>\n" +
            "          <image>\n" +
            "            <name>docker.io/acme/demo</name>\n" +
            "            <tags><tag>1.0.0</tag><tag>latest</tag></tags>\n" +
            "            <builder>paketobuildpacks/builder-jammy-base</builder>\n" +
            "            <runImage>paketobuildpacks/run-jammy-base</runImage>\n" +
            "            <publish>true</publish>\n" +
            "          </image>\n" +
            "        </configuration>\n" +
            "      </plugin>\n" +
            "    </plugins>\n" +
            "  </build>\n" +
            "</project>\n";
    }

    @Test
    void generates_container_block_for_quarkus_from_pom_properties() throws Exception {
        Path projectRoot = Files.createTempDirectory("quarkus-pom-props-");
        writeString(projectRoot.resolve("pom.xml"), quarkusPomWithPomProperties());

        GenerateDescriptorMojo mojo = new GenerateDescriptorMojo();
        MavenProject project = new MavenProject();
        project.setArtifactId("demo");
        project.setVersion("2.1.0");
        File pomFile = projectRoot.resolve("pom.xml").toFile();
        project.setFile(pomFile);
        setBasedir(project, pomFile.getParentFile());

        setField(mojo, "project", project);
        setField(mojo, "outputDirectory", tempDir.toString());
        setField(mojo, "outputFile", "descriptor.json");
        setField(mojo, "exportFormat", "json");
        setField(mojo, "generateHtml", true);
        setField(mojo, "attach", false);
        setField(mojo, "skip", false);

        mojo.execute();

        Path json = Paths.get(tempDir.toString(), "descriptor.json");
        Path html = Paths.get(tempDir.toString(), "descriptor.html");
        assertThat(json).exists();
        assertThat(html).exists();

        String jsonContent = Files.readString(json);
        assertThat(jsonContent).contains("\"tool\":\"quarkus\"");
        assertThat(jsonContent).contains("\"image\":\"ghcr.io/acme/demo\"");
        assertThat(jsonContent).contains("\"tag\":\"2.1.0\"");
        assertThat(jsonContent).contains("latest");

        String htmlContent = Files.readString(html);
        assertThat(htmlContent).contains("Container Image");
        assertThat(htmlContent).contains("ghcr.io/acme/demo");
    }

    @Test
    void generates_container_block_for_quarkus_from_application_properties() throws Exception {
        Path projectRoot = Files.createTempDirectory("quarkus-app-props-");
        writeString(projectRoot.resolve("pom.xml"), quarkusPomPluginOnly());
        // write src/main/resources/application.properties
        Path resourcesDir = projectRoot.resolve("src/main/resources");
        Files.createDirectories(resourcesDir);
        String props = String.join("\n",
                "quarkus.container-image.registry=docker.io",
                "quarkus.container-image.group=acme",
                "quarkus.container-image.name=demo",
                "quarkus.container-image.tag=3.0.0",
                "quarkus.container-image.additional-tags=latest,stage");
        writeString(resourcesDir.resolve("application.properties"), props + "\n");

        GenerateDescriptorMojo mojo = new GenerateDescriptorMojo();
        MavenProject project = new MavenProject();
        project.setArtifactId("demo");
        project.setVersion("3.0.0");
        File pomFile = projectRoot.resolve("pom.xml").toFile();
        project.setFile(pomFile);
        setBasedir(project, pomFile.getParentFile());

        setField(mojo, "project", project);
        setField(mojo, "outputDirectory", tempDir.toString());
        setField(mojo, "outputFile", "descriptor.json");
        setField(mojo, "exportFormat", "json");
        setField(mojo, "generateHtml", true);
        setField(mojo, "attach", false);
        setField(mojo, "skip", false);

        mojo.execute();

        Path json = Paths.get(tempDir.toString(), "descriptor.json");
        Path html = Paths.get(tempDir.toString(), "descriptor.html");
        assertThat(json).exists();
        assertThat(html).exists();

        String jsonContent = Files.readString(json);
        assertThat(jsonContent).contains("\"tool\":\"quarkus\"");
        assertThat(jsonContent).contains("\"image\":\"docker.io/acme/demo\"");
        assertThat(jsonContent).contains("\"tag\":\"3.0.0\"");
        assertThat(jsonContent).contains("stage");

        String htmlContent = Files.readString(html);
        assertThat(htmlContent).contains("Container Image");
        assertThat(htmlContent).contains("docker.io/acme/demo");
    }

    @Test
    void generates_container_block_for_fabric8() throws Exception {
        Path projectRoot = Files.createTempDirectory("fabric8-project-");
        writeString(projectRoot.resolve("pom.xml"), fabric8Pom());

        GenerateDescriptorMojo mojo = new GenerateDescriptorMojo();
        MavenProject project = new MavenProject();
        project.setArtifactId("demo");
        project.setVersion("1.0.0");
        File pomFile = projectRoot.resolve("pom.xml").toFile();
        project.setFile(pomFile);
        setBasedir(project, pomFile.getParentFile());

        setField(mojo, "project", project);
        setField(mojo, "outputDirectory", tempDir.toString());
        setField(mojo, "outputFile", "descriptor.json");
        setField(mojo, "exportFormat", "json");
        setField(mojo, "generateHtml", true);
        setField(mojo, "attach", false);
        setField(mojo, "skip", false);

        mojo.execute();

        Path json = Paths.get(tempDir.toString(), "descriptor.json");
        assertThat(json).exists();
        String jsonContent = Files.readString(json);
        assertThat(jsonContent).contains("\"tool\":\"fabric8\"");
        assertThat(jsonContent).contains("\"image\":\"registry.gitlab.com/acme/demo\"");
        assertThat(jsonContent).contains("\"baseImage\":\"eclipse-temurin:21-jre\"");
    }

    @Test
    void generates_container_block_for_micronaut() throws Exception {
        Path projectRoot = Files.createTempDirectory("micronaut-project-");
        writeString(projectRoot.resolve("pom.xml"), micronautPom());

        GenerateDescriptorMojo mojo = new GenerateDescriptorMojo();
        MavenProject project = new MavenProject();
        project.setArtifactId("demo");
        project.setVersion("2.0.0");
        File pomFile = projectRoot.resolve("pom.xml").toFile();
        project.setFile(pomFile);
        setBasedir(project, pomFile.getParentFile());

        setField(mojo, "project", project);
        setField(mojo, "outputDirectory", tempDir.toString());
        setField(mojo, "outputFile", "descriptor.json");
        setField(mojo, "exportFormat", "json");
        setField(mojo, "generateHtml", true);
        setField(mojo, "attach", false);
        setField(mojo, "skip", false);

        mojo.execute();

        Path json = Paths.get(tempDir.toString(), "descriptor.json");
        assertThat(json).exists();
        String jsonContent = Files.readString(json);
        assertThat(jsonContent).contains("\"tool\":\"micronaut\"");
        assertThat(jsonContent).contains("\"image\":\"ghcr.io/acme/demo\"");
        assertThat(jsonContent).contains("\"tag\":\"2.0.0\"");
    }

    @Test
    void generates_container_block_for_jkube() throws Exception {
        Path projectRoot = Files.createTempDirectory("jkube-project-");
        writeString(projectRoot.resolve("pom.xml"), jkubePom());

        GenerateDescriptorMojo mojo = new GenerateDescriptorMojo();
        MavenProject project = new MavenProject();
        project.setArtifactId("demo");
        project.setVersion("1.0.0");
        File pomFile = projectRoot.resolve("pom.xml").toFile();
        project.setFile(pomFile);
        setBasedir(project, pomFile.getParentFile());

        setField(mojo, "project", project);
        setField(mojo, "outputDirectory", tempDir.toString());
        setField(mojo, "outputFile", "descriptor.json");
        setField(mojo, "exportFormat", "json");
        setField(mojo, "generateHtml", true);
        setField(mojo, "attach", false);
        setField(mojo, "skip", false);

        mojo.execute();

        Path json = Paths.get(tempDir.toString(), "descriptor.json");
        assertThat(json).exists();
        String jsonContent = Files.readString(json);
        assertThat(jsonContent).contains("\"tool\":\"jkube\"");
        assertThat(jsonContent).contains("\"image\":\"quay.io/acme/demo\"");
        assertThat(jsonContent).contains("\"baseImage\":\"ubi8/openjdk-17-runtime\"");
    }

    // --- extra POM builders ---

    private static String quarkusPomWithPomProperties() {
        return "" +
            "<project xmlns=\"http://maven.apache.org/POM/4.0.0\" xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\"\n" +
            "  xsi:schemaLocation=\"http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd\">\n" +
            "  <modelVersion>4.0.0</modelVersion>\n" +
            "  <groupId>com.example</groupId>\n" +
            "  <artifactId>demo</artifactId>\n" +
            "  <version>2.1.0</version>\n" +
            "  <packaging>jar</packaging>\n" +
            "  <properties>\n" +
            "    <quarkus.container-image.registry>ghcr.io</quarkus.container-image.registry>\n" +
            "    <quarkus.container-image.group>acme</quarkus.container-image.group>\n" +
            "    <quarkus.container-image.name>demo</quarkus.container-image.name>\n" +
            "    <quarkus.container-image.tag>2.1.0</quarkus.container-image.tag>\n" +
            "    <quarkus.container-image.additional-tags>latest,rc-1</quarkus.container-image.additional-tags>\n" +
            "  </properties>\n" +
            "  <build>\n" +
            "    <plugins>\n" +
            "      <plugin>\n" +
            "        <groupId>io.quarkus</groupId>\n" +
            "        <artifactId>quarkus-maven-plugin</artifactId>\n" +
            "      </plugin>\n" +
            "    </plugins>\n" +
            "  </build>\n" +
            "</project>\n";
    }

    private static String quarkusPomPluginOnly() {
        return "" +
            "<project xmlns=\"http://maven.apache.org/POM/4.0.0\" xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\"\n" +
            "  xsi:schemaLocation=\"http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd\">\n" +
            "  <modelVersion>4.0.0</modelVersion>\n" +
            "  <groupId>com.example</groupId>\n" +
            "  <artifactId>demo</artifactId>\n" +
            "  <version>3.0.0</version>\n" +
            "  <packaging>jar</packaging>\n" +
            "  <build>\n" +
            "    <plugins>\n" +
            "      <plugin>\n" +
            "        <groupId>io.quarkus</groupId>\n" +
            "        <artifactId>quarkus-maven-plugin</artifactId>\n" +
            "      </plugin>\n" +
            "    </plugins>\n" +
            "  </build>\n" +
            "</project>\n";
    }

    private static String fabric8Pom() {
        return "" +
            "<project xmlns=\"http://maven.apache.org/POM/4.0.0\" xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\"\n" +
            "  xsi:schemaLocation=\"http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd\">\n" +
            "  <modelVersion>4.0.0</modelVersion>\n" +
            "  <groupId>com.example</groupId>\n" +
            "  <artifactId>demo</artifactId>\n" +
            "  <version>1.0.0</version>\n" +
            "  <packaging>jar</packaging>\n" +
            "  <build>\n" +
            "    <plugins>\n" +
            "      <plugin>\n" +
            "        <groupId>io.fabric8</groupId>\n" +
            "        <artifactId>docker-maven-plugin</artifactId>\n" +
            "        <configuration>\n" +
            "          <images>\n" +
            "            <image>\n" +
            "              <name>registry.gitlab.com/acme/demo</name>\n" +
            "              <build>\n" +
            "                <from>eclipse-temurin:21-jre</from>\n" +
            "                <tags>\n" +
            "                  <tag>1.0.0</tag>\n" +
            "                  <tag>latest</tag>\n" +
            "                </tags>\n" +
            "              </build>\n" +
            "            </image>\n" +
            "          </images>\n" +
            "        </configuration>\n" +
            "      </plugin>\n" +
            "    </plugins>\n" +
            "  </build>\n" +
            "</project>\n";
    }

    private static String micronautPom() {
        return "" +
            "<project xmlns=\"http://maven.apache.org/POM/4.0.0\" xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\"\n" +
            "  xsi:schemaLocation=\"http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd\">\n" +
            "  <modelVersion>4.0.0</modelVersion>\n" +
            "  <groupId>com.example</groupId>\n" +
            "  <artifactId>demo</artifactId>\n" +
            "  <version>2.0.0</version>\n" +
            "  <packaging>jar</packaging>\n" +
            "  <build>\n" +
            "    <plugins>\n" +
            "      <plugin>\n" +
            "        <groupId>io.micronaut.maven</groupId>\n" +
            "        <artifactId>micronaut-maven-plugin</artifactId>\n" +
            "        <configuration>\n" +
            "          <dockerRegistry>ghcr.io</dockerRegistry>\n" +
            "          <dockerGroup>acme</dockerGroup>\n" +
            "          <dockerName>demo</dockerName>\n" +
            "          <dockerTag>2.0.0</dockerTag>\n" +
            "        </configuration>\n" +
            "      </plugin>\n" +
            "    </plugins>\n" +
            "  </build>\n" +
            "</project>\n";
    }

    private static String jkubePom() {
        return "" +
            "<project xmlns=\"http://maven.apache.org/POM/4.0.0\" xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\"\n" +
            "  xsi:schemaLocation=\"http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd\">\n" +
            "  <modelVersion>4.0.0</modelVersion>\n" +
            "  <groupId>com.example</groupId>\n" +
            "  <artifactId>demo</artifactId>\n" +
            "  <version>1.0.0</version>\n" +
            "  <packaging>jar</packaging>\n" +
            "  <build>\n" +
            "    <plugins>\n" +
            "      <plugin>\n" +
            "        <groupId>org.eclipse.jkube</groupId>\n" +
            "        <artifactId>kubernetes-maven-plugin</artifactId>\n" +
            "        <configuration>\n" +
            "          <images>\n" +
            "            <image>\n" +
            "              <name>quay.io/acme/demo</name>\n" +
            "              <build>\n" +
            "                <from>ubi8/openjdk-17-runtime</from>\n" +
            "                <tags>\n" +
            "                  <tag>1.0.0</tag>\n" +
            "                  <tag>latest</tag>\n" +
            "                </tags>\n" +
            "              </build>\n" +
            "            </image>\n" +
            "          </images>\n" +
            "        </configuration>\n" +
            "      </plugin>\n" +
            "    </plugins>\n" +
            "  </build>\n" +
            "</project>\n";
    }

}

