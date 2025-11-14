package io.github.tourem.maven.plugin;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import io.github.tourem.maven.descriptor.model.analysis.AnalyzedDependency;
import io.github.tourem.maven.descriptor.model.analysis.DependencyAnalysisResult;
import org.apache.maven.artifact.Artifact;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.shared.dependency.analyzer.ProjectDependencyAnalysis;
import org.apache.maven.shared.dependency.analyzer.ProjectDependencyAnalyzer;
import org.apache.maven.shared.dependency.graph.DependencyGraphBuilder;
import org.apache.maven.shared.dependency.graph.DependencyNode;
import org.apache.maven.project.DefaultProjectBuildingRequest;

import org.eclipse.jgit.api.BlameCommand;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.blame.BlameResult;
import org.eclipse.jgit.revwalk.RevCommit;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;

@Mojo(name = "analyze-dependencies", defaultPhase = LifecyclePhase.VERIFY, threadSafe = true)
public class AnalyzeDependenciesMojo extends AbstractMojo {

    @Parameter(defaultValue = "${project}", readonly = true, required = true)
    private MavenProject project;

    @Parameter(defaultValue = "${session}", readonly = true)
    private MavenSession session;

    @org.apache.maven.plugins.annotations.Component
    private DependencyGraphBuilder dependencyGraphBuilder;

    @org.apache.maven.plugins.annotations.Component
    private ProjectDependencyAnalyzer projectDependencyAnalyzer;

    /** Output directory for analysis JSON. */
    @Parameter(property = "deployment.analysisOutputDir")
    private File analysisOutputDir;

    /** Output file name for analysis JSON. */
    @Parameter(property = "deployment.analysisOutputFile", defaultValue = "dependency-analysis.json")
    private String analysisOutputFile;

    // Phase 2 controls
    @Parameter(property = "descriptor.addGitContext", defaultValue = "true")
    private boolean addGitContext;

    @Parameter(property = "descriptor.handleFalsePositives", defaultValue = "true")
    private boolean handleFalsePositives;

    @Parameter(property = "descriptor.generateRecommendations", defaultValue = "true")
    private boolean generateRecommendations;

    @Parameter(property = "descriptor.detectConflicts", defaultValue = "true")
    private boolean detectConflicts;

    @Parameter(property = "descriptor.aggregateModules", defaultValue = "false")
    private boolean aggregateModules;

    @Parameter(property = "descriptor.generateHtml", defaultValue = "true")
    private boolean generateHtml;

    @Override
    public void execute() throws MojoExecutionException {
        try {
            ProjectDependencyAnalysis result = projectDependencyAnalyzer.analyze(project);

            List<AnalyzedDependency> unused = mapArtifacts(result.getUnusedDeclaredArtifacts());
            List<AnalyzedDependency> undeclared = mapArtifacts(result.getUsedUndeclaredArtifacts());

            DependencyAnalysisResult.RawResults raw = DependencyAnalysisResult.RawResults.builder()
                    .unused(unused)
                    .undeclared(undeclared)
                    .build();

            DependencyAnalysisResult.Summary summary = DependencyAnalysisResult.Summary.builder()
                    .totalDependencies(safeCount(project.getArtifacts()))
                    .directDependencies(safeCount(project.getDependencies()))
                    .transitiveDependencies(Math.max(0, safeCount(project.getArtifacts()) - safeCount(project.getDependencies())))
                    .issues(DependencyAnalysisResult.Issues.builder()
                            .unused(unused.size())
                            .undeclared(undeclared.size())
                            .totalIssues(unused.size() + undeclared.size())
                            .build())
                    .potentialSavings(estimateSavings(unused))
                    .build();

            DependencyAnalysisResult.DependencyAnalysisResultBuilder builder = DependencyAnalysisResult.builder()
                    .timestamp(Instant.now())
                    .rawResults(raw)
                    .summary(summary);

            if (addGitContext) {
                attachGitContextToUnused(unused);
            }
            if (handleFalsePositives) {
                detectFalsePositives(unused);
            }
            List<io.github.tourem.maven.descriptor.model.analysis.Recommendation> recs = null;
            if (generateRecommendations) {
                recs = generateRecommendations(unused);
                builder.recommendations(recs);
            }

            java.util.List<io.github.tourem.maven.descriptor.model.analysis.VersionConflict> conflicts = null;
            if (detectConflicts) {
                conflicts = detectVersionConflicts();
                builder.versionConflicts(conflicts);
            }
            if (aggregateModules && session != null && isExecutionRoot()) {
                io.github.tourem.maven.descriptor.model.analysis.MultiModuleAnalysis mma = aggregateAcrossModules();
                builder.multiModule(mma);
            }

            // Phase 3: Health score
            io.github.tourem.maven.descriptor.model.analysis.HealthScore health = calculateHealthScore(unused, undeclared, conflicts);
            builder.healthScore(health);

            DependencyAnalysisResult out = builder.build();

            writeJson(out);
            if (generateHtml) {
                writeHtml(out);
                getLog().info("Dependency analysis HTML generated: " + getHtmlOutputPath());
            }
            getLog().info("Dependency analysis generated: " + getOutputPath());
        } catch (Exception e) {
            throw new MojoExecutionException("Failed to analyze dependencies", e);
        }
    }

    private int safeCount(Collection<?> c) {
        return c == null ? 0 : c.size();
    }

    private List<AnalyzedDependency> mapArtifacts(Set<Artifact> artifacts) {
        List<AnalyzedDependency> list = new ArrayList<>();
        if (artifacts == null) return list;
        for (Artifact a : artifacts) {
            AnalyzedDependency.AnalyzedDependencyBuilder b = AnalyzedDependency.builder()
                    .groupId(a.getGroupId())
                    .artifactId(a.getArtifactId())
                    .version(a.getVersion())
                    .scope(a.getScope());

            File file = resolveFile(a);
            if (file != null && file.isFile()) {
                b.metadata(AnalyzedDependency.Metadata.builder()
                        .sizeBytes(file.length())
                        .sizeKB(round(file.length() / 1024.0))
                        .sizeMB(round(file.length() / (1024.0 * 1024.0)))
                        .fileLocation(file.getAbsolutePath())
                        .sha256(sha256Hex(file))
                        .packaging(a.getType())
                        .build());
            }
            list.add(b.build());
        }
        return list;
    }

    private File resolveFile(Artifact a) {
        File f = a.getFile();
        if (f != null && f.exists()) return f;
        // fallback: try standard local repo layout
        try {
            String rel = a.getGroupId().replace('.', '/') + "/" + a.getArtifactId() + "/" + a.getVersion() +
                    "/" + a.getArtifactId() + "-" + a.getVersion() + (a.getClassifier() != null ? ("-" + a.getClassifier()) : "") + "." + a.getType();
            Path localRepo = Path.of(project.getProjectBuildingRequest().getLocalRepository().getBasedir());
            Path p = localRepo.resolve(rel);
            if (Files.exists(p)) return p.toFile();
        } catch (Exception ignored) {
            // ignore
        }
        return null;
    }

    private boolean isExecutionRoot() {
        return project.isExecutionRoot();
    }

    private void attachGitContextToUnused(List<AnalyzedDependency> unused) {
        if (unused == null || unused.isEmpty()) return;
        File pom = project.getFile();
        if (pom == null || !pom.exists()) return;
        try {
            File repoRoot = findGitRoot(project.getBasedir());
            if (repoRoot == null) return;
            try (Git git = Git.open(repoRoot)) {
                String relPath = repoRoot.toPath().relativize(pom.toPath()).toString().replace('\\', '/');
                List<String> lines = Files.readAllLines(pom.toPath());
                BlameCommand bc = new BlameCommand(git.getRepository());
                bc.setFilePath(relPath);
                BlameResult br = bc.call();
                if (br == null) return;
                for (AnalyzedDependency d : unused) {
                    int line = findDependencyDeclarationLine(lines, d.getGroupId(), d.getArtifactId());
                    if (line >= 0) {
                        RevCommit c = br.getSourceCommit(line);
                        if (c != null) {
                            long when = (long) c.getAuthorIdent().getWhen().getTime();
                            long days = Math.max(0L, (System.currentTimeMillis() - when) / (1000L * 60 * 60 * 24));
                            d.setGit(io.github.tourem.maven.descriptor.model.analysis.GitInfo.builder()
                                    .commitId(c.getName())
                                    .authorName(c.getAuthorIdent().getName())
                                    .authorEmail(c.getAuthorIdent().getEmailAddress())
                                    .authorWhen(java.time.Instant.ofEpochMilli(when))
                                    .commitMessage(c.getFullMessage())
                                    .daysAgo(days)
                                    .build());
                        }
                    }
                }
            }
        } catch (Exception e) {
            getLog().warn("Git context attachment failed: " + e.getMessage());
        }
    }

    private int findDependencyDeclarationLine(List<String> lines, String groupId, String artifactId) {
        // naive search: locate the first block containing both <groupId>..</groupId> and <artifactId>..</artifactId>
        String g = "<groupId>" + groupId + "</groupId>";
        String a = "<artifactId>" + artifactId + "</artifactId>";
        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i);
            if (line.contains(g)) {
                // scan within next ~10 lines for artifactId
                for (int j = i; j < Math.min(lines.size(), i + 12); j++) {
                    if (lines.get(j).contains(a)) {
                        return j; // blame on artifactId line
                    }
                }
            }
        }
        return -1;
    }

    private File findGitRoot(File start) {
        File cur = start;
        while (cur != null) {
            File dotgit = new File(cur, ".git");
            if (dotgit.exists()) return cur;
            cur = cur.getParentFile();
        }
        return null;
    }

    private void detectFalsePositives(List<AnalyzedDependency> deps) {
        for (AnalyzedDependency d : deps) {
            List<String> reasons = new ArrayList<>();
            String aid = d.getArtifactId() == null ? "" : d.getArtifactId();
            String gid = d.getGroupId() == null ? "" : d.getGroupId();
            String scope = d.getScope();

            // Provided scope dependencies
            if ("provided".equals(scope)) reasons.add("provided-scope");

            // Annotation processors
            if (aid.equals("lombok") || (gid.equals("org.projectlombok"))) reasons.add("annotation-processor:lombok");
            if (aid.endsWith("-processor")) reasons.add("annotation-processor");

            // Dev tools
            if (aid.contains("devtools")) reasons.add("devtools");

            // Runtime agents
            if (aid.equals("aspectjweaver")) reasons.add("runtime-agent:aspectjweaver");

            // Spring Boot Starters (meta-dependencies)
            if (isSpringBootStarter(gid, aid)) {
                reasons.add("spring-boot-starter:" + aid);
            }

            if (!reasons.isEmpty()) {
                d.setSuspectedFalsePositive(true);
                d.setFalsePositiveReasons(reasons);
                d.setConfidence(0.5);
            } else {
                d.setSuspectedFalsePositive(false);
                d.setConfidence(0.9);
            }
        }
    }

    /**
     * Check if a dependency is a Spring Boot Starter.
     * Spring Boot Starters are meta-dependencies that bring transitive dependencies.
     * They often appear as "unused" because the code uses the transitive dependencies,
     * not the starter itself.
     */
    private boolean isSpringBootStarter(String groupId, String artifactId) {
        if (!"org.springframework.boot".equals(groupId)) {
            return false;
        }

        // Common Spring Boot Starters
        return artifactId != null && (
            artifactId.equals("spring-boot-starter-web") ||
            artifactId.equals("spring-boot-starter-data-jpa") ||
            artifactId.equals("spring-boot-starter-data-mongodb") ||
            artifactId.equals("spring-boot-starter-data-redis") ||
            artifactId.equals("spring-boot-starter-security") ||
            artifactId.equals("spring-boot-starter-actuator") ||
            artifactId.equals("spring-boot-starter-test") ||
            artifactId.equals("spring-boot-starter-validation") ||
            artifactId.equals("spring-boot-starter-webflux") ||
            artifactId.equals("spring-boot-starter-amqp") ||
            artifactId.equals("spring-boot-starter-batch") ||
            artifactId.equals("spring-boot-starter-cache") ||
            artifactId.equals("spring-boot-starter-mail") ||
            artifactId.equals("spring-boot-starter-oauth2-client") ||
            artifactId.equals("spring-boot-starter-oauth2-resource-server") ||
            artifactId.equals("spring-boot-starter-thymeleaf") ||
            artifactId.equals("spring-boot-starter-websocket") ||
            artifactId.equals("spring-boot-starter-aop") ||
            artifactId.equals("spring-boot-starter-artemis") ||
            artifactId.equals("spring-boot-starter-data-cassandra") ||
            artifactId.equals("spring-boot-starter-data-elasticsearch") ||
            artifactId.equals("spring-boot-starter-data-jdbc") ||
            artifactId.equals("spring-boot-starter-data-r2dbc") ||
            artifactId.equals("spring-boot-starter-freemarker") ||
            artifactId.equals("spring-boot-starter-graphql") ||
            artifactId.equals("spring-boot-starter-integration") ||
            artifactId.equals("spring-boot-starter-jdbc") ||
            artifactId.equals("spring-boot-starter-jooq") ||
            artifactId.equals("spring-boot-starter-json") ||
            artifactId.equals("spring-boot-starter-logging") ||
            artifactId.equals("spring-boot-starter-quartz") ||
            artifactId.equals("spring-boot-starter-rsocket") ||
            artifactId.startsWith("spring-boot-starter-") // Catch-all for other starters
        );
    }

    private List<io.github.tourem.maven.descriptor.model.analysis.Recommendation> generateRecommendations(List<AnalyzedDependency> unused) {
        List<io.github.tourem.maven.descriptor.model.analysis.Recommendation> recs = new ArrayList<>();
        for (AnalyzedDependency d : unused) {
            if (Boolean.TRUE.equals(d.getSuspectedFalsePositive())) continue;
            String patch = "<!-- Remove unused dependency -->\n" +
                    "<!-- groupId: " + d.getGroupId() + ", artifactId: " + d.getArtifactId() + " -->";
            io.github.tourem.maven.descriptor.model.analysis.Recommendation.RecommendationBuilder rb =
                    io.github.tourem.maven.descriptor.model.analysis.Recommendation.builder()
                            .type(io.github.tourem.maven.descriptor.model.analysis.Recommendation.Type.REMOVE_DEPENDENCY)
                            .groupId(d.getGroupId()).artifactId(d.getArtifactId()).version(d.getVersion())
                            .pomPatch(patch)
                            .verifyCommands(java.util.Arrays.asList("mvn -q -DskipTests -DskipITs clean verify"))
                            .rollbackCommands(java.util.Arrays.asList("git checkout -- pom.xml"));
            if (d.getMetadata() != null && d.getMetadata().getSizeBytes() != null) {
                long bytes = d.getMetadata().getSizeBytes();
                rb.impact(io.github.tourem.maven.descriptor.model.analysis.Recommendation.Impact.builder()
                        .sizeSavingsBytes(bytes)
                        .sizeSavingsKB(round(bytes / 1024.0))
                        .sizeSavingsMB(round(bytes / (1024.0 * 1024.0)))
                        .build());
            }
            recs.add(rb.build());
        }
        return recs;
    }

    private List<io.github.tourem.maven.descriptor.model.analysis.VersionConflict> detectVersionConflicts() {
        List<io.github.tourem.maven.descriptor.model.analysis.VersionConflict> out = new ArrayList<>();
        try {
            DefaultProjectBuildingRequest req = new DefaultProjectBuildingRequest(session.getProjectBuildingRequest());
            req.setProject(project);
            DependencyNode root = dependencyGraphBuilder.buildDependencyGraph(req, null);
            java.util.Map<String, java.util.Set<String>> versionsByGa = new java.util.HashMap<>();
            collectVersions(root, versionsByGa);
            java.util.Map<String, String> selectedByGa = new java.util.HashMap<>();
            for (Artifact a : project.getArtifacts()) {
                selectedByGa.put(a.getGroupId()+":"+a.getArtifactId(), a.getVersion());
            }
            for (java.util.Map.Entry<String, java.util.Set<String>> e : versionsByGa.entrySet()) {
                if (e.getValue().size() > 1) {
                    String[] ga = e.getKey().split(":", 2);
                    String selected = selectedByGa.get(e.getKey());
                    io.github.tourem.maven.descriptor.model.analysis.VersionConflict.RiskLevel risk = riskLevel(e.getValue());
                    out.add(io.github.tourem.maven.descriptor.model.analysis.VersionConflict.builder()
                            .groupId(ga[0]).artifactId(ga[1])
                            .versions(new java.util.ArrayList<>(e.getValue()))
                            .selectedVersion(selected)
                            .riskLevel(risk)
                            .build());
                }
            }
        } catch (Exception e) {
            getLog().warn("Version conflict detection failed: " + e.getMessage());
        }
        return out;
    }

    private void collectVersions(DependencyNode node, java.util.Map<String, java.util.Set<String>> map) {
        if (node == null) return;
        Artifact a = node.getArtifact();
        if (a != null) {
            String ga = a.getGroupId()+":"+a.getArtifactId();
            map.computeIfAbsent(ga, k -> new java.util.HashSet<>()).add(a.getVersion());
        }
        if (node.getChildren() != null) {
            for (DependencyNode c : node.getChildren()) collectVersions(c, map);
        }
    }

    private io.github.tourem.maven.descriptor.model.analysis.VersionConflict.RiskLevel riskLevel(java.util.Set<String> versions) {
        String major = null; String minor = null;
        boolean diffMajor = false; boolean diffMinor = false;
        for (String v : versions) {
            String[] parts = v.split("\\.");
            String m = parts.length>0?parts[0]:v; String n = parts.length>1?parts[1]:"0";
            if (major==null) { major=m; minor=n; }
            else {
                if (!m.equals(major)) diffMajor = true;
                if (!n.equals(minor)) diffMinor = true;
            }
        }
        if (diffMajor) return io.github.tourem.maven.descriptor.model.analysis.VersionConflict.RiskLevel.HIGH;
        if (diffMinor) return io.github.tourem.maven.descriptor.model.analysis.VersionConflict.RiskLevel.MEDIUM;
        return io.github.tourem.maven.descriptor.model.analysis.VersionConflict.RiskLevel.LOW;
    }

    private io.github.tourem.maven.descriptor.model.analysis.MultiModuleAnalysis aggregateAcrossModules() {
        java.util.Map<String, java.util.List<String>> unusedModules = new java.util.HashMap<>();
        int count = 0; int analyzed = 0;
        for (MavenProject p : session.getAllProjects()) {
            if (p.getPackaging()!=null && p.getPackaging().equals("pom")) continue;
            count++;
            try {
                File outDir = new File(p.getBuild().getOutputDirectory());
                if (!outDir.exists()) continue; // skip not built
                analyzed++;
                ProjectDependencyAnalysis res = projectDependencyAnalyzer.analyze(p);
                for (Artifact a : res.getUnusedDeclaredArtifacts()) {
                    String ga = a.getGroupId()+":"+a.getArtifactId();
                    unusedModules.computeIfAbsent(ga, k -> new java.util.ArrayList<>()).add(p.getArtifactId());
                }
            } catch (Exception ignored) {}
        }
        java.util.List<io.github.tourem.maven.descriptor.model.analysis.MultiModuleAnalysis.CommonUnused> commons = new java.util.ArrayList<>();
        for (java.util.Map.Entry<String, java.util.List<String>> e : unusedModules.entrySet()) {
            if (e.getValue().size() >= 2) {
                String[] ga = e.getKey().split(":",2);
                commons.add(io.github.tourem.maven.descriptor.model.analysis.MultiModuleAnalysis.CommonUnused.builder()
                        .groupId(ga[0]).artifactId(ga[1]).modules(e.getValue()).build());
            }
        }
        return io.github.tourem.maven.descriptor.model.analysis.MultiModuleAnalysis.builder()
                .moduleCount(count)
                .analyzedModuleCount(analyzed)
                .commonUnused(commons)
                .build();
    }


    private String sha256Hex(File file) {
        try (DigestInputStream dis = new DigestInputStream(Files.newInputStream(file.toPath()), MessageDigest.getInstance("SHA-256"))) {
            byte[] buffer = new byte[8192];
            while (dis.read(buffer) != -1) { /* consume */ }
            byte[] digest = dis.getMessageDigest().digest();
            StringBuilder sb = new StringBuilder();
            for (byte b : digest) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (Exception e) {
            getLog().warn("Failed to compute sha256 for " + file + ": " + e.getMessage());
            return null;
        }
    }

    private DependencyAnalysisResult.PotentialSavings estimateSavings(List<AnalyzedDependency> unused) {
        long bytes = 0L;
        for (AnalyzedDependency d : unused) {
            if (d.getMetadata() != null && d.getMetadata().getSizeBytes() != null) {
                bytes += d.getMetadata().getSizeBytes();
            }
        }
        return DependencyAnalysisResult.PotentialSavings.builder()
                .bytes(bytes)
                .kb(round(bytes / 1024.0))
                .mb(round(bytes / (1024.0 * 1024.0)))
                .build();
    }

    private double round(double v) {
        return Math.round(v * 100.0) / 100.0;
    }

    private void writeJson(DependencyAnalysisResult out) throws IOException {
        File dir = analysisOutputDir != null ? analysisOutputDir : new File(project.getBuild().getDirectory());
        if (!dir.exists() && !dir.mkdirs()) {
            throw new IOException("Cannot create output dir: " + dir);
        }
        File file = new File(dir, analysisOutputFile);
        ObjectMapper mapper = new ObjectMapper()
                .enable(SerializationFeature.INDENT_OUTPUT)
                .findAndRegisterModules(); // Auto-register JSR310 module for Instant support
        try (FileOutputStream fos = new FileOutputStream(file)) {
            mapper.writeValue(fos, out);
        }
    }

    private io.github.tourem.maven.descriptor.model.analysis.HealthScore calculateHealthScore(
            java.util.List<AnalyzedDependency> unused,
            java.util.List<AnalyzedDependency> undeclared,
            java.util.List<io.github.tourem.maven.descriptor.model.analysis.VersionConflict> conflicts
    ) {
        int unusedReal = 0;
        if (unused != null) {
            for (AnalyzedDependency d : unused) {
                if (!Boolean.TRUE.equals(d.getSuspectedFalsePositive())) unusedReal++;
            }
        }
        int undeclaredCount = undeclared == null ? 0 : undeclared.size();
        int medium = 0, high = 0;
        if (conflicts != null) {
            for (io.github.tourem.maven.descriptor.model.analysis.VersionConflict vc : conflicts) {
                var rl = vc.getRiskLevel();
                if (rl == io.github.tourem.maven.descriptor.model.analysis.VersionConflict.RiskLevel.HIGH) high++;
                else if (rl == io.github.tourem.maven.descriptor.model.analysis.VersionConflict.RiskLevel.MEDIUM) medium++;
            }
        }

        // Build breakdown
        java.util.List<io.github.tourem.maven.descriptor.model.analysis.HealthScore.Factor> cleanFactors = new java.util.ArrayList<>();
        if (unusedReal > 0) cleanFactors.add(io.github.tourem.maven.descriptor.model.analysis.HealthScore.Factor.builder()
                .factor(unusedReal + " unused dependencies")
                .impact(-(unusedReal * 2))
                .details("2 points per unused (excluding false positives)")
                .build());
        if (undeclaredCount > 0) cleanFactors.add(io.github.tourem.maven.descriptor.model.analysis.HealthScore.Factor.builder()
                .factor(undeclaredCount + " undeclared dependencies")
                .impact(-(undeclaredCount * 2))
                .details("2 points per undeclared")
                .build());
        int cleanScore = Math.max(0, 100 - (unusedReal * 2 + undeclaredCount * 2));

        java.util.List<io.github.tourem.maven.descriptor.model.analysis.HealthScore.Factor> maintFactors = new java.util.ArrayList<>();
        if (medium > 0) maintFactors.add(io.github.tourem.maven.descriptor.model.analysis.HealthScore.Factor.builder()
                .factor(medium + " version conflicts (MEDIUM)")
                .impact(-(medium * 3))
                .details("3 points per conflict")
                .build());
        if (high > 0) maintFactors.add(io.github.tourem.maven.descriptor.model.analysis.HealthScore.Factor.builder()
                .factor(high + " version conflicts (HIGH)")
                .impact(-(high * 5))
                .details("5 points per conflict")
                .build());
        int maintScore = Math.max(0, 100 - (medium * 3 + high * 5));

        double overallD = 0.4 * cleanScore + 0.3 * 100 + 0.2 * maintScore + 0.1 * 100;
        int score = (int) Math.round(Math.max(0, Math.min(100, overallD)));

        io.github.tourem.maven.descriptor.model.analysis.HealthScore.Breakdown bd =
                io.github.tourem.maven.descriptor.model.analysis.HealthScore.Breakdown.builder()
                        .cleanliness(io.github.tourem.maven.descriptor.model.analysis.HealthScore.Category.builder()
                                .score(cleanScore).outOf(100).weight(0.4).details(unusedReal + " unused, " + undeclaredCount + " undeclared").factors(cleanFactors).build())
                        .security(io.github.tourem.maven.descriptor.model.analysis.HealthScore.Category.builder()
                                .score(100).outOf(100).weight(0.3).details("Security not evaluated in this run").build())
                        .maintainability(io.github.tourem.maven.descriptor.model.analysis.HealthScore.Category.builder()
                                .score(maintScore).outOf(100).weight(0.2).details(medium + " MED, " + high + " HIGH conflicts").factors(maintFactors).build())
                        .licenses(io.github.tourem.maven.descriptor.model.analysis.HealthScore.Category.builder()
                                .score(100).outOf(100).weight(0.1).details("License compliance not evaluated in this run").build())
                        .build();

        java.util.List<io.github.tourem.maven.descriptor.model.analysis.HealthScore.ActionableImprovement> improvements = new java.util.ArrayList<>();
        if (unusedReal > 0) improvements.add(io.github.tourem.maven.descriptor.model.analysis.HealthScore.ActionableImprovement.builder()
                .action("Remove " + unusedReal + " unused dependencies")
                .scoreImpact(unusedReal * 2)
                .effort("LOW")
                .priority(1)
                .build());
        if ((medium + high) > 0) improvements.add(io.github.tourem.maven.descriptor.model.analysis.HealthScore.ActionableImprovement.builder()
                .action("Fix " + (medium + high) + " version conflicts")
                .scoreImpact(medium * 3 + high * 5)
                .effort("MEDIUM")
                .priority(2)
                .build());
        if (undeclaredCount > 0) improvements.add(io.github.tourem.maven.descriptor.model.analysis.HealthScore.ActionableImprovement.builder()
                .action("Declare " + undeclaredCount + " undeclared dependencies")
                .scoreImpact(undeclaredCount * 2)
                .effort("LOW")
                .priority(3)
                .build());

        return io.github.tourem.maven.descriptor.model.analysis.HealthScore.builder()
                .overall(score)
                .grade(grade(score))
                .breakdown(bd)
                .actionableImprovements(improvements)
                .build();
    }

    private String grade(int score) {
        if (score >= 97) return "A+";
        if (score >= 93) return "A";
        if (score >= 90) return "A-";
        if (score >= 87) return "B+";
        if (score >= 83) return "B";
        if (score >= 80) return "B-";
        if (score >= 77) return "C+";
        if (score >= 73) return "C";
        if (score >= 70) return "C-";
        if (score >= 60) return "D";
        return "F";
    }

    private void writeHtml(DependencyAnalysisResult out) throws IOException {
        File dir = analysisOutputDir != null ? analysisOutputDir : new File(project.getBuild().getDirectory());
        if (!dir.exists() && !dir.mkdirs()) throw new IOException("Cannot create output dir: " + dir);
        String htmlName = analysisOutputFile != null && analysisOutputFile.endsWith(".json")
                ? analysisOutputFile.replace(".json", ".html")
                : (analysisOutputFile == null || analysisOutputFile.isBlank() ? "dependency-analysis.html" : analysisOutputFile + ".html");
        File file = new File(dir, htmlName);

        StringBuilder sb = new StringBuilder(8192);
        int total = out.getSummary() != null && out.getSummary().getTotalDependencies() != null ? out.getSummary().getTotalDependencies() : 0;
        int unused = out.getRawResults() != null && out.getRawResults().getUnused() != null ? out.getRawResults().getUnused().size() : 0;
        int undeclared = out.getRawResults() != null && out.getRawResults().getUndeclared() != null ? out.getRawResults().getUndeclared().size() : 0;
        int conflicts = out.getVersionConflicts() != null ? out.getVersionConflicts().size() : 0;
        int score = out.getHealthScore() != null && out.getHealthScore().getOverall() != null ? out.getHealthScore().getOverall() : 0;
        String grade = out.getHealthScore() != null ? out.getHealthScore().getGrade() : "";

        sb.append("<!DOCTYPE html><html lang='en'><head><meta charset='UTF-8'><meta name='viewport' content='width=device-width, initial-scale=1.0'>");
        sb.append("<title>Dependency Analysis</title><style>");
        sb.append("body{font-family:Segoe UI,Tahoma,Arial,sans-serif;background:#0f172a;color:#e2e8f0;margin:0;padding:24px;}\n");
        sb.append(".container{max-width:1200px;margin:0 auto;}\n.header{display:flex;align-items:center;justify-content:space-between;margin-bottom:20px;}\n");
        sb.append(".score{font-size:48px;font-weight:800;} .grade{font-size:16px;opacity:.85;margin-left:8px;}\n");
        sb.append(".cards{display:grid;grid-template-columns:repeat(auto-fit,minmax(180px,1fr));gap:12px;margin:16px 0;}\n");
        sb.append(".card{background:#111827;padding:16px;border-radius:12px;border:1px solid #374151;} .card .v{font-size:28px;font-weight:700;} .muted{color:#94a3b8;font-size:12px;}\n");
        sb.append("table{width:100%;border-collapse:collapse;margin-top:12px;} th,td{padding:10px;border-bottom:1px solid #334155;} th{background:#1f2937;text-align:left;} tr:hover{background:#0b1220;}\n");
        sb.append(".badge{display:inline-block;padding:2px 8px;border-radius:999px;font-size:12px;} .warn{background:#f59e0b1a;color:#f59e0b;} .ok{background:#10b9811a;color:#10b981;} .riskH{background:#ef44441a;color:#ef4444;} .riskM{background:#f59e0b1a;color:#f59e0b;}\n");
        sb.append("</style></head><body><div class='container'>");

        sb.append("<div class='header'><div><div class='muted'>Dependency Health Score</div><div class='score'>").append(score).append("<span class='grade'>").append(grade).append("</span></div></div>");
        sb.append("<div class='muted'>Generated: ").append(java.time.Instant.now().toString()).append("</div></div>");

        sb.append("<div class='cards'>");
        sb.append("<div class='card'><div class='muted'>Total Dependencies</div><div class='v'>").append(total).append("</div></div>");
        sb.append("<div class='card'><div class='muted'>Unused</div><div class='v'>").append(unused).append("</div></div>");
        sb.append("<div class='card'><div class='muted'>Undeclared</div><div class='v'>").append(undeclared).append("</div></div>");
        sb.append("<div class='card'><div class='muted'>Version Conflicts</div><div class='v'>").append(conflicts).append("</div></div>");
        sb.append("</div>");

        // Unused table
        if (out.getRawResults() != null && out.getRawResults().getUnused() != null && !out.getRawResults().getUnused().isEmpty()) {
            sb.append("<h3>Unused Dependencies ("+out.getRawResults().getUnused().size()+")</h3>");
            sb.append("<table><thead><tr><th>Artifact</th><th>Scope</th><th>Size</th><th>Status</th><th>Added By</th></tr></thead><tbody>");
            for (AnalyzedDependency d : out.getRawResults().getUnused()) {
                String ga = (d.getGroupId()==null?"":d.getGroupId())+":"+(d.getArtifactId()==null?"":d.getArtifactId());
                String size = (d.getMetadata()!=null && d.getMetadata().getSizeKB()!=null)?(String.format("%.0f KB", d.getMetadata().getSizeKB())):"";
                String status = Boolean.TRUE.equals(d.getSuspectedFalsePositive()) ? "<span class='badge ok'>FALSE POSITIVE</span>" : "<span class='badge warn'>UNUSED</span>";
                String who = d.getGit()!=null ? (d.getGit().getAuthorEmail()+" ("+d.getGit().getDaysAgo()+"d)") : "";
                sb.append("<tr><td><strong>").append(ga).append(":").append(d.getVersion()==null?"":d.getVersion()).append("</strong></td>")
                  .append("<td>").append(d.getScope()==null?"":d.getScope()).append("</td>")
                  .append("<td>").append(size).append("</td>")
                  .append("<td>").append(status).append("</td>")
                  .append("<td>").append(who==null?"":who).append("</td></tr>");
            }
            sb.append("</tbody></table>");
        }

        // Conflicts table
        if (out.getVersionConflicts() != null && !out.getVersionConflicts().isEmpty()) {
            sb.append("<h3>Version Conflicts ("+out.getVersionConflicts().size()+")</h3>");
            sb.append("<table><thead><tr><th>Artifact</th><th>Selected</th><th>Versions</th><th>Risk</th></tr></thead><tbody>");
            for (io.github.tourem.maven.descriptor.model.analysis.VersionConflict vc : out.getVersionConflicts()) {
                String ga = vc.getGroupId()+":"+vc.getArtifactId();
                String risk = vc.getRiskLevel()==io.github.tourem.maven.descriptor.model.analysis.VersionConflict.RiskLevel.HIGH?"<span class='badge riskH'>HIGH</span>"
                        : (vc.getRiskLevel()==io.github.tourem.maven.descriptor.model.analysis.VersionConflict.RiskLevel.MEDIUM?"<span class='badge riskM'>MEDIUM</span>":"<span class='badge ok'>LOW</span>");
                sb.append("<tr><td><strong>").append(ga).append("</strong></td>")
                  .append("<td>").append(vc.getSelectedVersion()==null?"":vc.getSelectedVersion()).append("</td>")
                  .append("<td>").append(String.join(", ", vc.getVersions())).append("</td>")
                  .append("<td>").append(risk).append("</td></tr>");
            }
            sb.append("</tbody></table>");
        }

        // Recommendations quick list
        if (out.getRecommendations() != null && !out.getRecommendations().isEmpty()) {
            sb.append("<h3>Recommendations ("+out.getRecommendations().size()+")</h3><ul>");
            for (io.github.tourem.maven.descriptor.model.analysis.Recommendation r : out.getRecommendations()) {
                sb.append("<li>").append(r.getType()).append(": ")
                  .append(r.getGroupId()).append(":").append(r.getArtifactId())
                  .append(r.getVersion()==null?"":" ("+r.getVersion()+")")
                  .append("</li>");
            }
            sb.append("</ul>");
        }

        sb.append("</div></body></html>");
        Files.writeString(file.toPath(), sb.toString());
    }

    private String getHtmlOutputPath() {
        File dir = analysisOutputDir != null ? analysisOutputDir : new File(project.getBuild().getDirectory());
        String htmlName = analysisOutputFile != null && analysisOutputFile.endsWith(".json")
                ? analysisOutputFile.replace(".json", ".html")
                : (analysisOutputFile == null || analysisOutputFile.isBlank() ? "dependency-analysis.html" : analysisOutputFile + ".html");
        return new File(dir, htmlName).getAbsolutePath();
    }


    private String getOutputPath() {
        File dir = analysisOutputDir != null ? analysisOutputDir : new File(project.getBuild().getDirectory());
        return new File(dir, analysisOutputFile).getAbsolutePath();
    }
}

