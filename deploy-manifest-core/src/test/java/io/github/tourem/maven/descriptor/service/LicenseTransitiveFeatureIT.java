package io.github.tourem.maven.descriptor.service;

import io.github.tourem.maven.descriptor.model.LicenseOptions;
import io.github.tourem.maven.descriptor.model.ProjectDescriptor;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class LicenseTransitiveFeatureIT {

    @TempDir
    Path tempDir;

    private String previousRepo;

    @AfterEach
    void cleanup() {
        if (previousRepo != null) {
            System.setProperty("maven.repo.local", previousRepo);
        }
    }

    @Test
    void shouldCollectTransitiveLicenses_andAggregate_andMultiLicense() throws Exception {
        Path projectDir = tempDir.resolve("proj-transitive");
        Files.createDirectories(projectDir);
        Files.writeString(projectDir.resolve("pom.xml"), springBootPomWithDeps("1"));

        // Fake local repo
        Path repo = tempDir.resolve("m2repo");
        Files.createDirectories(repo);
        previousRepo = System.getProperty("maven.repo.local");
        System.setProperty("maven.repo.local", repo.toString());

        // direct-a: Apache-2.0 and depends on trans-b
        writePom(repo, "com.example", "direct-a", "1.0",
                """
                <licenses>
                  <license>
                    <name>Apache-2.0</name>
                    <url>https://www.apache.org/licenses/LICENSE-2.0</url>
                  </license>
                </licenses>
                <dependencies>
                  <dependency>
                    <groupId>com.example</groupId>
                    <artifactId>trans-b</artifactId>
                    <version>1.0</version>
                    <scope>runtime</scope>
                  </dependency>
                </dependencies>
                """);

        // trans-b: multi-license MPL-2.0 OR EPL-1.0
        writePom(repo, "com.example", "trans-b", "1.0",
                """
                <licenses>
                  <license><name>MPL-2.0</name><url>https://www.mozilla.org/MPL/2.0/</url></license>
                  <license><name>EPL-1.0</name><url>https://www.eclipse.org/legal/epl-v10.html</url></license>
                </licenses>
                """);

        LicenseOptions lopts = LicenseOptions.builder()
                .include(true)
                .licenseWarnings(true)
                .includeTransitiveLicenses(true)
                .build();

        MavenProjectAnalyzer analyzer = new MavenProjectAnalyzer(
                io.github.tourem.maven.descriptor.model.DependencyTreeOptions.builder().include(false).build(),
                lopts
        );
        ProjectDescriptor descriptor = analyzer.analyzeProject(projectDir);

        var lic = descriptor.deployableModules().get(0).getLicenses();
        assertThat(lic).isNotNull();
        assertThat(lic.getSummary().getTotal()).isEqualTo(2); // direct-a + trans-b
        assertThat(lic.getSummary().getUnknown()).isZero();
        assertThat(lic.getSummary().getByType()).isNotEmpty();
        assertThat(lic.getSummary().getByType().get("Apache-2.0")).isEqualTo(1);
        // Each token contributes to aggregation
        assertThat(lic.getSummary().getByType().get("MPL-2.0")).isEqualTo(1);
        assertThat(lic.getSummary().getByType().get("EPL-1.0")).isEqualTo(1);
        // Detail for trans-b is multi-license
        assertThat(lic.getDetails().stream().anyMatch(d -> Boolean.TRUE.equals(d.getMultiLicense()) && d.getLicense().contains("MPL-2.0") && d.getDepth() == 2)).isTrue();
    }

    @Test
    void shouldDetectIncompatibleLicenses_andSetCompliance() throws Exception {
        Path projectDir = tempDir.resolve("proj-incompat");
        Files.createDirectories(projectDir);
        Files.writeString(projectDir.resolve("pom.xml"), springBootPomWithDeps("2"));

        // Fake local repo
        Path repo = tempDir.resolve("m2repo2");
        Files.createDirectories(repo);
        previousRepo = System.getProperty("maven.repo.local");
        System.setProperty("maven.repo.local", repo.toString());

        // gpl-lib: GPL-3.0 incompatible
        writePom(repo, "com.example", "gpl-lib", "2.1.0",
                """
                <licenses>
                  <license>
                    <name>GPL-3.0</name>
                    <url>https://www.gnu.org/licenses/gpl-3.0.html</url>
                  </license>
                </licenses>
                """);

        LicenseOptions lopts = LicenseOptions.builder()
                .include(true)
                .licenseWarnings(true)
                .includeTransitiveLicenses(true)
                .incompatibleLicenses(new java.util.HashSet<>(java.util.Set.of("GPL-3.0", "AGPL-3.0")))
                .build();

        MavenProjectAnalyzer analyzer = new MavenProjectAnalyzer(
                io.github.tourem.maven.descriptor.model.DependencyTreeOptions.builder().include(false).build(),
                lopts
        );
        ProjectDescriptor descriptor = analyzer.analyzeProject(projectDir);

        var lic = descriptor.deployableModules().get(0).getLicenses();
        assertThat(lic).isNotNull();
        assertThat(lic.getCompliance().getHasIncompatibleLicenses()).isTrue();
        assertThat(lic.getCompliance().getIncompatibleCount()).isGreaterThan(0);
        assertThat(lic.getCompliance().getCommerciallyViable()).isFalse();
        // warnings should include HIGH severity for GPL-3.0
        assertThat(lic.getWarnings().stream().anyMatch(w -> "HIGH".equals(w.getSeverity()) && w.getLicense().contains("GPL-3.0"))).isTrue();
    }

    private static void writePom(Path repo, String g, String a, String v, String inner) throws Exception {
        Path dir = repo.resolve(g.replace('.', '/') + "/" + a + "/" + v);
        Files.createDirectories(dir);
        String pom = "<project xmlns=\"http://maven.apache.org/POM/4.0.0\"\n" +
                "         xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\"\n" +
                "         xsi:schemaLocation=\"http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd\">\n" +
                "  <modelVersion>4.0.0</modelVersion>\n" +
                "  <groupId>" + g + "</groupId>\n" +
                "  <artifactId>" + a + "</artifactId>\n" +
                "  <version>" + v + "</version>\n" +
                inner + "\n" +
                "</project>\n";
        Files.writeString(dir.resolve(a + "-" + v + ".pom"), pom);
    }

    private static String springBootPomWithDeps(String variant) {
        // variant 1: depends on direct-a:1.0
        // variant 2: depends on gpl-lib:2.1.0
        String dep;
        if ("2".equals(variant)) {
            dep = "<dependency>\n" +
                    "  <groupId>com.example</groupId>\n" +
                    "  <artifactId>gpl-lib</artifactId>\n" +
                    "  <version>2.1.0</version>\n" +
                    "</dependency>\n";
        } else {
            dep = "<dependency>\n" +
                    "  <groupId>com.example</groupId>\n" +
                    "  <artifactId>direct-a</artifactId>\n" +
                    "  <version>1.0</version>\n" +
                    "</dependency>\n";
        }
        return "<project xmlns=\"http://maven.apache.org/POM/4.0.0\"\n" +
                "         xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\"\n" +
                "         xsi:schemaLocation=\"http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd\">\n" +
                "  <modelVersion>4.0.0</modelVersion>\n" +
                "  <groupId>com.example</groupId>\n" +
                "  <artifactId>demo" + variant + "</artifactId>\n" +
                "  <version>1.0.0</version>\n" +
                "  <dependencies>\n" +
                dep +
                "  </dependencies>\n" +
                "  <build>\n" +
                "    <plugins>\n" +
                "      <plugin>\n" +
                "        <groupId>org.springframework.boot</groupId>\n" +
                "        <artifactId>spring-boot-maven-plugin</artifactId>\n" +
                "        <version>3.2.0</version>\n" +
                "        <executions>\n" +
                "          <execution>\n" +
                "            <goals><goal>repackage</goal></goals>\n" +
                "          </execution>\n" +
                "        </executions>\n" +
                "      </plugin>\n" +
                "    </plugins>\n" +
                "  </build>\n" +
                "</project>\n";
    }
}

