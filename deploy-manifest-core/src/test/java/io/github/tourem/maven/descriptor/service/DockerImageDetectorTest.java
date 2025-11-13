package io.github.tourem.maven.descriptor.service;

import io.github.tourem.maven.descriptor.model.ContainerInfo;
import org.apache.maven.model.Build;
import org.apache.maven.model.Model;
import org.apache.maven.model.Plugin;
import org.apache.maven.model.PluginExecution;
import org.codehaus.plexus.util.xml.Xpp3Dom;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;

class DockerImageDetectorTest {

    private DockerImageDetector detector;

    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() {
        detector = new DockerImageDetector();
    }

    private Model baseModel() {
        Model model = new Model();
        model.setGroupId("com.example");
        model.setArtifactId("demo");
        model.setVersion("1.0.0");
        Build build = new Build();
        model.setBuild(build);
        return model;
    }

    @Test
    void detectsJib() {
        Model model = baseModel();
        Plugin jib = new Plugin();
        jib.setGroupId("com.google.cloud.tools");
        jib.setArtifactId("jib-maven-plugin");

        Xpp3Dom cfg = new Xpp3Dom("configuration");
        Xpp3Dom to = new Xpp3Dom("to");
        Xpp3Dom image = new Xpp3Dom("image"); image.setValue("ghcr.io/acme/demo");
        Xpp3Dom tags = new Xpp3Dom("tags");
        Xpp3Dom tag1 = new Xpp3Dom("tag"); tag1.setValue("1.0.0");
        Xpp3Dom tag2 = new Xpp3Dom("tag"); tag2.setValue("latest");
        tags.addChild(tag1); tags.addChild(tag2);
        to.addChild(image); to.addChild(tags);
        Xpp3Dom from = new Xpp3Dom("from");
        Xpp3Dom base = new Xpp3Dom("image"); base.setValue("eclipse-temurin:17-jre");
        from.addChild(base);
        cfg.addChild(to); cfg.addChild(from);
        jib.setConfiguration(cfg);
        model.getBuild().addPlugin(jib);

        ContainerInfo info = detector.detect(model, tempDir);
        assertThat(info).isNotNull();
        assertThat(info.getTool()).isEqualTo("jib");
        assertThat(info.getImage()).isEqualTo("ghcr.io/acme/demo");
        assertThat(info.getRegistry()).isEqualTo("ghcr.io");
        assertThat(info.getGroup()).isEqualTo("acme");
        assertThat(info.getTag()).isEqualTo("1.0.0");
        assertThat(info.getAdditionalTags()).contains("latest");
        assertThat(info.getBaseImage()).isEqualTo("eclipse-temurin:17-jre");
    }

    @Test
    void detectsSpringBootBuildImage() {
        Model model = baseModel();
        Plugin boot = new Plugin();
        boot.setGroupId("org.springframework.boot");
        boot.setArtifactId("spring-boot-maven-plugin");
        PluginExecution exec = new PluginExecution();
        exec.setGoals(Collections.singletonList("build-image"));
        boot.setExecutions(Collections.singletonList(exec));

        Xpp3Dom cfg = new Xpp3Dom("configuration");
        Xpp3Dom image = new Xpp3Dom("image");
        Xpp3Dom name = new Xpp3Dom("name"); name.setValue("docker.io/acme/demo");
        Xpp3Dom tags = new Xpp3Dom("tags");
        Xpp3Dom tag = new Xpp3Dom("tag"); tag.setValue("1.0.0");
        tags.addChild(tag);
        Xpp3Dom builder = new Xpp3Dom("builder"); builder.setValue("paketobuildpacks/builder-jammy-base");
        Xpp3Dom run = new Xpp3Dom("runImage"); run.setValue("paketobuildpacks/run-jammy-base");
        Xpp3Dom publish = new Xpp3Dom("publish"); publish.setValue("true");
        image.addChild(name); image.addChild(tags); image.addChild(builder); image.addChild(run); image.addChild(publish);
        cfg.addChild(image);
        boot.setConfiguration(cfg);
        model.getBuild().addPlugin(boot);

        ContainerInfo info = detector.detect(model, tempDir);
        assertThat(info).isNotNull();
        assertThat(info.getTool()).isEqualTo("spring-boot");
        assertThat(info.getImage()).isEqualTo("docker.io/acme/demo");
        assertThat(info.getRegistry()).isEqualTo("docker.io");
        assertThat(info.getGroup()).isEqualTo("acme");
        assertThat(info.getTag()).isEqualTo("1.0.0");
        assertThat(info.getBuilderImage()).isEqualTo("paketobuildpacks/builder-jammy-base");
        assertThat(info.getRunImage()).isEqualTo("paketobuildpacks/run-jammy-base");
        assertThat(info.getPublish()).isTrue();
    }

    @Test
    void detectsFabric8() {
        Model model = baseModel();
        Plugin f8 = new Plugin();
        f8.setGroupId("io.fabric8");
        f8.setArtifactId("docker-maven-plugin");

        Xpp3Dom cfg = new Xpp3Dom("configuration");
        Xpp3Dom images = new Xpp3Dom("images");
        Xpp3Dom image = new Xpp3Dom("image");
        Xpp3Dom name = new Xpp3Dom("name"); name.setValue("ghcr.io/acme/demo");
        Xpp3Dom build = new Xpp3Dom("build");
        Xpp3Dom tags = new Xpp3Dom("tags");
        Xpp3Dom tag1 = new Xpp3Dom("tag"); tag1.setValue("1.0.0");
        Xpp3Dom tag2 = new Xpp3Dom("tag"); tag2.setValue("latest");
        tags.addChild(tag1); tags.addChild(tag2);
        Xpp3Dom from = new Xpp3Dom("from"); from.setValue("eclipse-temurin:17");
        build.addChild(tags); build.addChild(from);
        image.addChild(name); image.addChild(build);
        images.addChild(image);
        cfg.addChild(images);
        f8.setConfiguration(cfg);
        model.getBuild().addPlugin(f8);

        ContainerInfo info = detector.detect(model, tempDir);
        assertThat(info).isNotNull();
        assertThat(info.getTool()).isEqualTo("fabric8");
        assertThat(info.getImage()).isEqualTo("ghcr.io/acme/demo");
        assertThat(info.getTag()).isEqualTo("1.0.0");
        assertThat(info.getAdditionalTags()).contains("latest");
        assertThat(info.getBaseImage()).isEqualTo("eclipse-temurin:17");
    }

    @Test
    void detectsQuarkusFromPomProperties() throws Exception {
        Model model = baseModel();
        Plugin q = new Plugin();
        q.setGroupId("io.quarkus");
        q.setArtifactId("quarkus-maven-plugin");
        model.getBuild().addPlugin(q);

        Properties props = new Properties();
        props.setProperty("quarkus.container-image.registry", "ghcr.io");
        props.setProperty("quarkus.container-image.group", "acme");
        props.setProperty("quarkus.container-image.name", "demo");
        props.setProperty("quarkus.container-image.tag", "1.0.0");
        props.setProperty("quarkus.container-image.additional-tags", "latest,1.0");
        model.setProperties(props);

        ContainerInfo info = detector.detect(model, tempDir);
        assertThat(info).isNotNull();
        assertThat(info.getTool()).isEqualTo("quarkus");
        assertThat(info.getImage()).isEqualTo("ghcr.io/acme/demo");
        assertThat(info.getTag()).isEqualTo("1.0.0");
        assertThat(info.getAdditionalTags()).contains("latest", "1.0");
    }

    @Test
    void detectsQuarkusFromApplicationProperties() throws Exception {
        Model model = baseModel();
        Plugin q = new Plugin();
        q.setGroupId("io.quarkus");
        q.setArtifactId("quarkus-maven-plugin");
        model.getBuild().addPlugin(q);

        Path res = tempDir.resolve("src/main/resources");
        Files.createDirectories(res);
        Files.writeString(res.resolve("application.properties"), String.join("\n",
                "quarkus.container-image.registry=ghcr.io",
                "quarkus.container-image.group=acme",
                "quarkus.container-image.name=demo",
                "quarkus.container-image.tag=1.0.0",
                "quarkus.container-image.additional-tags=latest"
        ));

        ContainerInfo info = detector.detect(model, tempDir);
        assertThat(info).isNotNull();
        assertThat(info.getImage()).isEqualTo("ghcr.io/acme/demo");
        assertThat(info.getTag()).isEqualTo("1.0.0");
        assertThat(info.getAdditionalTags()).contains("latest");
    }

    @Test
    void detectsMicronautPlugin() {
        Model model = baseModel();
        Plugin m = new Plugin();
        m.setGroupId("io.micronaut.maven");
        m.setArtifactId("micronaut-maven-plugin");

        Xpp3Dom cfg = new Xpp3Dom("configuration");
        Xpp3Dom reg = new Xpp3Dom("dockerRegistry"); reg.setValue("ghcr.io");
        Xpp3Dom grp = new Xpp3Dom("dockerGroup"); grp.setValue("acme");
        Xpp3Dom name = new Xpp3Dom("dockerName"); name.setValue("demo");
        Xpp3Dom tag = new Xpp3Dom("dockerTag"); tag.setValue("1.0.0");
        Xpp3Dom extra = new Xpp3Dom("dockerExtraTags");
        Xpp3Dom t = new Xpp3Dom("tag"); t.setValue("latest");
        extra.addChild(t);
        cfg.addChild(reg); cfg.addChild(grp); cfg.addChild(name); cfg.addChild(tag); cfg.addChild(extra);
        m.setConfiguration(cfg);
        model.getBuild().addPlugin(m);

        ContainerInfo info = detector.detect(model, tempDir);
        assertThat(info).isNotNull();
        assertThat(info.getTool()).isEqualTo("micronaut");
        assertThat(info.getImage()).isEqualTo("ghcr.io/acme/demo");
        assertThat(info.getTag()).isEqualTo("1.0.0");
        assertThat(info.getAdditionalTags()).contains("latest");
    }

    @Test
    void detectsEclipseJKube() {
        Model model = baseModel();
        Plugin jk = new Plugin();
        jk.setGroupId("org.eclipse.jkube");
        jk.setArtifactId("kubernetes-maven-plugin");

        Xpp3Dom cfg = new Xpp3Dom("configuration");
        Xpp3Dom images = new Xpp3Dom("images");
        Xpp3Dom image = new Xpp3Dom("image");
        Xpp3Dom name = new Xpp3Dom("name"); name.setValue("ghcr.io/acme/demo");
        Xpp3Dom build = new Xpp3Dom("build");
        Xpp3Dom tags = new Xpp3Dom("tags");
        Xpp3Dom tag1 = new Xpp3Dom("tag"); tag1.setValue("1.0.0");
        Xpp3Dom tag2 = new Xpp3Dom("tag"); tag2.setValue("latest");
        tags.addChild(tag1); tags.addChild(tag2);
        Xpp3Dom from = new Xpp3Dom("from"); from.setValue("eclipse-temurin:17");
        build.addChild(tags); build.addChild(from);
        image.addChild(name); image.addChild(build);
        images.addChild(image);
        cfg.addChild(images);
        jk.setConfiguration(cfg);
        model.getBuild().addPlugin(jk);

        ContainerInfo info = detector.detect(model, tempDir);
        assertThat(info).isNotNull();
        assertThat(info.getTool()).isEqualTo("jkube");
        assertThat(info.getImage()).isEqualTo("ghcr.io/acme/demo");
        assertThat(info.getTag()).isEqualTo("1.0.0");
        assertThat(info.getAdditionalTags()).contains("latest");
        assertThat(info.getBaseImage()).isEqualTo("eclipse-temurin:17");
    }

}

