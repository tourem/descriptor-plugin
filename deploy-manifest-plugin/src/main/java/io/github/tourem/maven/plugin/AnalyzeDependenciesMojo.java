package io.github.tourem.maven.plugin;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import io.github.tourem.maven.descriptor.model.analysis.AnalyzedDependency;
import io.github.tourem.maven.descriptor.model.analysis.DependencyAnalysisResult;
import io.github.tourem.maven.descriptor.model.analysis.RepositoryHealth;
import io.github.tourem.maven.descriptor.service.DependencyVersionLookup;
import io.github.tourem.maven.descriptor.service.RepositoryHealthChecker;
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

    // Phase 1.5: Version lookup
    @Parameter(property = "descriptor.lookupAvailableVersions", defaultValue = "true")
    private boolean lookupAvailableVersions;

    @Parameter(property = "descriptor.maxAvailableVersions", defaultValue = "3")
    private int maxAvailableVersions;

    @Parameter(property = "descriptor.versionLookupTimeoutMs", defaultValue = "5000")
    private int versionLookupTimeoutMs;

    // Repository health check
    @Parameter(property = "descriptor.checkRepositoryHealth", defaultValue = "true")
    private boolean checkRepositoryHealth;

    @Parameter(property = "descriptor.repositoryHealthTimeoutMs", defaultValue = "5000")
    private int repositoryHealthTimeoutMs;

    @Parameter(property = "descriptor.githubToken")
    private String githubToken;

    // Plugin analysis
    @Parameter(property = "descriptor.includePlugins", defaultValue = "true")
    private boolean includePlugins;

    // Dependency tree
    @Parameter(property = "descriptor.includeDependencyTree", defaultValue = "false")
    private boolean includeDependencyTree;

    @Parameter(property = "descriptor.dependencyTreeFormat", defaultValue = "tree")
    private String dependencyTreeFormat;

    @Parameter(property = "descriptor.dependencyTreeDepth", defaultValue = "-1")
    private int dependencyTreeDepth;

    @Parameter(property = "descriptor.dependencyTreeScope")
    private String dependencyTreeScope;

    // Export format
    @Parameter(property = "descriptor.exportFormat", defaultValue = "json")
    private String exportFormat;

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

            // Calculate dependency counts
            int directCount = safeCount(project.getDependencies());

            // Count all resolved dependencies (direct + transitive) using dependency graph
            int totalCount = 0;
            try {
                DefaultProjectBuildingRequest buildingRequest = new DefaultProjectBuildingRequest(session.getProjectBuildingRequest());
                buildingRequest.setProject(project);
                DependencyNode rootNode = dependencyGraphBuilder.buildDependencyGraph(buildingRequest, null);

                // Count all nodes in the tree (excluding root)
                Set<String> uniqueDeps = new java.util.HashSet<>();
                countDependencies(rootNode, uniqueDeps);
                totalCount = uniqueDeps.size();
            } catch (Exception e) {
                getLog().debug("Failed to build dependency graph for counting: " + e.getMessage());
                // Fallback: count from analysis result (only direct dependencies)
                Set<Artifact> allResolvedArtifacts = new java.util.HashSet<>();
                if (result.getUsedDeclaredArtifacts() != null) {
                    allResolvedArtifacts.addAll(result.getUsedDeclaredArtifacts());
                }
                if (result.getUnusedDeclaredArtifacts() != null) {
                    allResolvedArtifacts.addAll(result.getUnusedDeclaredArtifacts());
                }
                if (result.getUsedUndeclaredArtifacts() != null) {
                    allResolvedArtifacts.addAll(result.getUsedUndeclaredArtifacts());
                }
                totalCount = allResolvedArtifacts.size();
            }

            int transitiveCount = Math.max(0, totalCount - directCount);

            DependencyAnalysisResult.Summary summary = DependencyAnalysisResult.Summary.builder()
                    .totalDependencies(totalCount)
                    .directDependencies(directCount)
                    .transitiveDependencies(transitiveCount)
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
            if (lookupAvailableVersions) {
                enrichWithAvailableVersions(unused);
                enrichWithAvailableVersions(undeclared);
            }
            if (checkRepositoryHealth) {
                enrichWithRepositoryHealth(unused);
                enrichWithRepositoryHealth(undeclared);
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

            // Collect plugin information
            if (includePlugins) {
                io.github.tourem.maven.descriptor.model.PluginInfo pluginInfo = collectPlugins();
                builder.plugins(pluginInfo);
            }

            // Collect dependency tree
            if (includeDependencyTree) {
                io.github.tourem.maven.descriptor.model.DependencyTreeInfo treeInfo = collectDependencyTree();
                builder.dependencyTree(treeInfo);
            }

            // Phase 3: Health score
            io.github.tourem.maven.descriptor.model.analysis.HealthScore health = calculateHealthScore(unused, undeclared, conflicts);
            builder.healthScore(health);

            DependencyAnalysisResult out = builder.build();

            // Write outputs based on exportFormat
            writeOutputs(out);

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

    private void countDependencies(DependencyNode node, Set<String> uniqueDeps) {
        if (node == null) return;

        // Add current node (skip root which is the project itself)
        if (node.getArtifact() != null && node.getParent() != null) {
            String key = node.getArtifact().getGroupId() + ":" +
                        node.getArtifact().getArtifactId() + ":" +
                        node.getArtifact().getVersion();
            uniqueDeps.add(key);
        }

        // Recursively count children
        if (node.getChildren() != null) {
            for (DependencyNode child : node.getChildren()) {
                countDependencies(child, uniqueDeps);
            }
        }
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
     * Enrich dependencies with available versions from configured repositories.
     */
    private void enrichWithAvailableVersions(List<AnalyzedDependency> dependencies) {
        if (dependencies == null || dependencies.isEmpty()) {
            return;
        }

        try {
            // Create version lookup service using project's Maven model
            DependencyVersionLookup versionLookup = new DependencyVersionLookup(
                    project.getModel(),
                    versionLookupTimeoutMs
            );

            for (AnalyzedDependency dep : dependencies) {
                try {
                    List<String> availableVersions = versionLookup.lookupAvailableVersions(
                            dep.getGroupId(),
                            dep.getArtifactId(),
                            dep.getVersion(),
                            maxAvailableVersions
                    );

                    if (availableVersions != null && !availableVersions.isEmpty()) {
                        dep.setAvailableVersions(availableVersions);
                        getLog().debug(String.format("Found %d available versions for %s:%s:%s",
                                availableVersions.size(),
                                dep.getGroupId(),
                                dep.getArtifactId(),
                                dep.getVersion()));
                    }
                } catch (Exception e) {
                    getLog().debug("Failed to lookup versions for " + dep.getGroupId() + ":" +
                                   dep.getArtifactId() + ": " + e.getMessage());
                }
            }
        } catch (Exception e) {
            getLog().warn("Failed to initialize version lookup: " + e.getMessage());
        }
    }

    /**
     * Enrich dependencies with repository health information.
     * Checks Maven Central metadata and GitHub repository status.
     */
    private void enrichWithRepositoryHealth(List<AnalyzedDependency> dependencies) {
        if (dependencies == null || dependencies.isEmpty()) {
            return;
        }

        try {
            // Create repository health checker
            RepositoryHealthChecker healthChecker = new RepositoryHealthChecker(
                    repositoryHealthTimeoutMs,
                    githubToken
            );

            int healthyCount = 0;
            int warningCount = 0;
            int dangerCount = 0;

            for (AnalyzedDependency dep : dependencies) {
                try {
                    RepositoryHealth health = healthChecker.checkHealth(
                            dep.getGroupId(),
                            dep.getArtifactId(),
                            dep.getVersion()
                    );

                    if (health != null) {
                        dep.setRepositoryHealth(health);

                        // Log warnings and dangers
                        if (health.getLevel() == RepositoryHealth.HealthLevel.DANGER) {
                            dangerCount++;
                            getLog().warn(String.format("⚠️  DANGER: %s:%s - %s",
                                    dep.getGroupId(),
                                    dep.getArtifactId(),
                                    health.getConcerns() != null ? String.join(", ", health.getConcerns()) : "Unknown issues"));
                        } else if (health.getLevel() == RepositoryHealth.HealthLevel.WARNING) {
                            warningCount++;
                            getLog().warn(String.format("⚠️  WARNING: %s:%s - %s",
                                    dep.getGroupId(),
                                    dep.getArtifactId(),
                                    health.getConcerns() != null ? String.join(", ", health.getConcerns()) : "Some concerns"));
                        } else if (health.getLevel() == RepositoryHealth.HealthLevel.HEALTHY) {
                            healthyCount++;
                        }
                    }
                } catch (Exception e) {
                    getLog().debug("Failed to check repository health for " + dep.getGroupId() + ":" +
                                   dep.getArtifactId() + ": " + e.getMessage());
                }
            }

            if (dangerCount > 0 || warningCount > 0) {
                getLog().info(String.format("Repository Health Summary: %d healthy, %d warnings, %d dangers",
                        healthyCount, warningCount, dangerCount));
            }
        } catch (Exception e) {
            getLog().warn("Failed to initialize repository health checker: " + e.getMessage());
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

        StringBuilder sb = new StringBuilder(16384);
        int total = out.getSummary() != null && out.getSummary().getTotalDependencies() != null ? out.getSummary().getTotalDependencies() : 0;
        int unused = out.getRawResults() != null && out.getRawResults().getUnused() != null ? out.getRawResults().getUnused().size() : 0;
        int undeclared = out.getRawResults() != null && out.getRawResults().getUndeclared() != null ? out.getRawResults().getUndeclared().size() : 0;
        int conflicts = out.getVersionConflicts() != null ? out.getVersionConflicts().size() : 0;
        int score = out.getHealthScore() != null && out.getHealthScore().getOverall() != null ? out.getHealthScore().getOverall() : 0;
        String grade = out.getHealthScore() != null ? out.getHealthScore().getGrade() : "";

        // HTML Header with modern design matching descriptor
        sb.append("<!DOCTYPE html>\n<html lang='en'>\n<head>\n");
        sb.append("<meta charset='UTF-8'>\n<meta name='viewport' content='width=device-width, initial-scale=1.0'>\n");
        sb.append("<title>").append(project.getName()).append(" - Dependency Analysis</title>\n");
        sb.append("<style>\n");

        // Base styles
        sb.append("* { margin: 0; padding: 0; box-sizing: border-box; }\n");
        sb.append("body { font-family: 'Segoe UI', Tahoma, Geneva, Verdana, sans-serif; background: linear-gradient(135deg, #667eea 0%, #764ba2 100%); min-height: 100vh; padding: 20px; }\n");
        sb.append(".container { max-width: 1400px; margin: 0 auto; background: white; border-radius: 20px; box-shadow: 0 20px 60px rgba(0,0,0,0.3); overflow: hidden; }\n");

        // Header with gradient and animation
        sb.append(".header { background: linear-gradient(135deg, #667eea 0%, #764ba2 100%); color: white; padding: 40px; display: flex; justify-content: space-between; align-items: center; position: relative; overflow: hidden; }\n");
        sb.append(".header::before { content: ''; position: absolute; top: -50%; right: -50%; width: 200%; height: 200%; background: radial-gradient(circle, rgba(255,255,255,0.1) 0%, transparent 70%); animation: pulse 15s ease-in-out infinite; }\n");
        sb.append("@keyframes pulse { 0%, 100% { transform: scale(1); } 50% { transform: scale(1.1); } }\n");
        sb.append(".header h1 { font-size: 2.5em; margin-bottom: 10px; position: relative; z-index: 1; text-shadow: 2px 2px 4px rgba(0,0,0,0.2); }\n");
        sb.append(".header .subtitle { font-size: 1.1em; opacity: 0.9; position: relative; z-index: 1; }\n");
        sb.append(".header .timestamp { margin-top: 15px; font-size: 0.9em; opacity: 0.8; position: relative; z-index: 1; }\n");

        // Theme toggle button
        sb.append(".theme-toggle { background: rgba(255,255,255,0.2); border: 2px solid rgba(255,255,255,0.3); color: white; padding: 12px 16px; border-radius: 50%; cursor: pointer; font-size: 1.5em; transition: all 0.3s; position: relative; z-index: 1; }\n");
        sb.append(".theme-toggle:hover { background: rgba(255,255,255,0.3); transform: rotate(20deg) scale(1.1); }\n");

        // Score display
        sb.append(".score-display { position: relative; z-index: 1; }\n");
        sb.append(".score-label { font-size: 0.9em; opacity: 0.8; margin-bottom: 5px; }\n");
        sb.append(".score { font-size: 3.5em; font-weight: 800; line-height: 1; }\n");
        sb.append(".grade { font-size: 1.2em; opacity: 0.85; margin-left: 8px; }\n");

        // Content area
        sb.append(".content { padding: 40px; }\n");
        sb.append("h2, h3 { color: #333; margin: 30px 0 15px 0; }\n");

        // Cards
        sb.append(".cards { display: grid; grid-template-columns: repeat(auto-fit, minmax(200px, 1fr)); gap: 16px; margin: 20px 0; }\n");
        sb.append(".card { background: #f8f9fa; padding: 20px; border-radius: 12px; border: 1px solid #e0e0e0; transition: transform 0.2s, box-shadow 0.2s; }\n");
        sb.append(".card:hover { transform: translateY(-2px); box-shadow: 0 4px 12px rgba(0,0,0,0.1); }\n");
        sb.append(".card .label { color: #666; font-size: 0.85em; margin-bottom: 8px; }\n");
        sb.append(".card .value { font-size: 2em; font-weight: 700; color: #333; }\n");

        // Tables
        sb.append("table { width: 100%; border-collapse: collapse; margin-top: 16px; background: white; border-radius: 8px; overflow: hidden; }\n");
        sb.append("th, td { padding: 12px; text-align: left; border-bottom: 1px solid #e0e0e0; }\n");
        sb.append("th { background: #f8f9fa; font-weight: 600; color: #333; }\n");
        sb.append("tr:hover { background: #f8f9fa; }\n");

        // Badges
        sb.append(".badge { display: inline-block; padding: 4px 10px; border-radius: 12px; font-size: 0.85em; font-weight: 600; }\n");
        sb.append(".badge.ok { background: #d4edda; color: #155724; }\n");
        sb.append(".badge.warn { background: #fff3cd; color: #856404; }\n");
        sb.append(".badge.error { background: #f8d7da; color: #721c24; }\n");
        sb.append(".badge.riskH { background: #f8d7da; color: #721c24; }\n");
        sb.append(".badge.riskM { background: #fff3cd; color: #856404; }\n");

        // Dark mode styles
        sb.append("body.dark-mode { background: linear-gradient(135deg, #1a1a2e 0%, #16213e 100%); }\n");
        sb.append("body.dark-mode .container { background: #0f3460; }\n");
        sb.append("body.dark-mode .header { background: linear-gradient(135deg, #16213e 0%, #0f3460 100%); }\n");
        sb.append("body.dark-mode .content { color: #e0e0e0; }\n");
        sb.append("body.dark-mode h2, body.dark-mode h3 { color: #e0e0e0; }\n");
        sb.append("body.dark-mode .card { background: #1a1a2e; border-color: #2a2a3e; }\n");
        sb.append("body.dark-mode .card .label { color: #a0a0a0; }\n");
        sb.append("body.dark-mode .card .value { color: #e0e0e0; }\n");
        sb.append("body.dark-mode table { background: #1a1a2e; }\n");
        sb.append("body.dark-mode th { background: #16213e; color: #e0e0e0; }\n");
        sb.append("body.dark-mode td { color: #e0e0e0; border-bottom-color: #2a2a3e; }\n");
        sb.append("body.dark-mode tr:hover { background: #16213e; }\n");

        sb.append("</style>\n</head>\n<body>\n");
        sb.append("<div class='container'>\n");

        // Header
        sb.append("<div class='header'>\n");
        sb.append("<div class='score-display'>\n");
        sb.append("<div class='score-label'>Dependency Health Score</div>\n");
        sb.append("<div class='score'>").append(score).append("<span class='grade'>").append(grade).append("</span></div>\n");
        sb.append("</div>\n");
        sb.append("<div>\n");
        sb.append("<h1>").append(escapeHtml(project.getName())).append("</h1>\n");
        sb.append("<div class='subtitle'>Dependency Analysis Report</div>\n");
        sb.append("<div class='timestamp'>📅 Generated: ").append(java.time.Instant.now().toString()).append("</div>\n");
        sb.append("</div>\n");
        sb.append("<button class='theme-toggle' onclick='toggleTheme()' title='Toggle Dark/Light Mode'>\n");
        sb.append("<span class='theme-icon'>🌙</span>\n");
        sb.append("</button>\n");
        sb.append("</div>\n");

        sb.append("<div class='content'>\n");

        // Summary cards
        sb.append("<h2>📊 Summary</h2>\n");
        sb.append("<div class='cards'>\n");
        sb.append("<div class='card'><div class='label'>Total Dependencies</div><div class='value'>").append(total).append("</div></div>\n");
        sb.append("<div class='card'><div class='label'>Unused</div><div class='value'>").append(unused).append("</div></div>\n");
        sb.append("<div class='card'><div class='label'>Undeclared</div><div class='value'>").append(undeclared).append("</div></div>\n");
        sb.append("<div class='card'><div class='label'>Version Conflicts</div><div class='value'>").append(conflicts).append("</div></div>\n");
        sb.append("</div>\n");

        // Health Score Breakdown
        if (out.getHealthScore() != null && out.getHealthScore().getBreakdown() != null) {
            sb.append("<h2>🏥 Health Score Breakdown</h2>\n");
            sb.append("<div class='cards'>\n");
            var breakdown = out.getHealthScore().getBreakdown();
            if (breakdown.getCleanliness() != null) {
                sb.append("<div class='card'><div class='label'>Cleanliness (40%)</div><div class='value'>")
                  .append(breakdown.getCleanliness().getScore()).append("/100</div>\n")
                  .append("<div class='label' style='margin-top:8px;font-size:0.8em;'>").append(escapeHtml(breakdown.getCleanliness().getDetails())).append("</div></div>\n");
            }
            if (breakdown.getSecurity() != null) {
                sb.append("<div class='card'><div class='label'>Security (30%)</div><div class='value'>")
                  .append(breakdown.getSecurity().getScore()).append("/100</div>\n")
                  .append("<div class='label' style='margin-top:8px;font-size:0.8em;'>").append(escapeHtml(breakdown.getSecurity().getDetails())).append("</div></div>\n");
            }
            if (breakdown.getMaintainability() != null) {
                sb.append("<div class='card'><div class='label'>Maintainability (20%)</div><div class='value'>")
                  .append(breakdown.getMaintainability().getScore()).append("/100</div>\n")
                  .append("<div class='label' style='margin-top:8px;font-size:0.8em;'>").append(escapeHtml(breakdown.getMaintainability().getDetails())).append("</div></div>\n");
            }
            if (breakdown.getLicenses() != null) {
                sb.append("<div class='card'><div class='label'>Licenses (10%)</div><div class='value'>")
                  .append(breakdown.getLicenses().getScore()).append("/100</div>\n")
                  .append("<div class='label' style='margin-top:8px;font-size:0.8em;'>").append(escapeHtml(breakdown.getLicenses().getDetails())).append("</div></div>\n");
            }
            sb.append("</div>\n");
        }

        // Actionable Improvements
        if (out.getHealthScore() != null && out.getHealthScore().getActionableImprovements() != null && !out.getHealthScore().getActionableImprovements().isEmpty()) {
            sb.append("<h2>💡 Actionable Improvements</h2>\n");
            sb.append("<table>\n<thead>\n<tr><th>Action</th><th>Score Impact</th><th>Effort</th><th>Priority</th></tr>\n</thead>\n<tbody>\n");
            for (var improvement : out.getHealthScore().getActionableImprovements()) {
                String effortBadge = improvement.getEffort().equals("LOW") ? "<span class='badge ok'>LOW</span>" :
                                    (improvement.getEffort().equals("MEDIUM") ? "<span class='badge warn'>MEDIUM</span>" : "<span class='badge error'>HIGH</span>");
                sb.append("<tr><td>").append(escapeHtml(improvement.getAction())).append("</td>")
                  .append("<td>+").append(improvement.getScoreImpact()).append("</td>")
                  .append("<td>").append(effortBadge).append("</td>")
                  .append("<td>").append(improvement.getPriority()).append("</td></tr>\n");
            }
            sb.append("</tbody>\n</table>\n");
        }

        // Unused table
        if (out.getRawResults() != null && out.getRawResults().getUnused() != null && !out.getRawResults().getUnused().isEmpty()) {
            sb.append("<h2>🗑️ Unused Dependencies (").append(out.getRawResults().getUnused().size()).append(")</h2>\n");
            sb.append("<table>\n<thead>\n<tr><th>Artifact</th><th>Current</th><th>Scope</th><th>Size</th><th>Status</th><th>Repo Health</th><th>Available Versions</th><th>Latest</th><th>Added By</th></tr>\n</thead>\n<tbody>\n");
            for (AnalyzedDependency d : out.getRawResults().getUnused()) {
                String ga = (d.getGroupId()==null?"":escapeHtml(d.getGroupId()))+":"+(d.getArtifactId()==null?"":escapeHtml(d.getArtifactId()));
                String currentVersion = d.getVersion()==null?"":escapeHtml(d.getVersion());
                String size = (d.getMetadata()!=null && d.getMetadata().getSizeKB()!=null)?(String.format("%.0f KB", d.getMetadata().getSizeKB())):"";
                String status = Boolean.TRUE.equals(d.getSuspectedFalsePositive()) ? "<span class='badge ok'>FALSE POSITIVE</span>" : "<span class='badge warn'>UNUSED</span>";
                String who = d.getGit()!=null ? (escapeHtml(d.getGit().getAuthorEmail())+" ("+d.getGit().getDaysAgo()+"d)") : "";

                // Repository health badge
                String healthBadge = "";
                if (d.getRepositoryHealth() != null) {
                    RepositoryHealth health = d.getRepositoryHealth();
                    switch (health.getLevel()) {
                        case HEALTHY:
                            healthBadge = "<span class='badge ok' title='" +
                                escapeHtml(health.getPositives() != null ? String.join(", ", health.getPositives()) : "Healthy") +
                                "'>✓ HEALTHY</span>";
                            break;
                        case WARNING:
                            healthBadge = "<span class='badge warn' title='" +
                                escapeHtml(health.getConcerns() != null ? String.join(", ", health.getConcerns()) : "Some concerns") +
                                "'>⚠ WARNING</span>";
                            break;
                        case DANGER:
                            healthBadge = "<span class='badge error' title='" +
                                escapeHtml(health.getConcerns() != null ? String.join(", ", health.getConcerns()) : "Serious concerns") +
                                "'>⛔ DANGER</span>";
                            break;
                        case UNKNOWN:
                            healthBadge = "<span class='badge' style='background:#e0e0e0;color:#666;'>? UNKNOWN</span>";
                            break;
                    }
                }

                // Available versions - display as list
                String availableVersions = "";
                if (d.getAvailableVersions() != null && !d.getAvailableVersions().isEmpty()) {
                    StringBuilder versionList = new StringBuilder();
                    for (int i = 0; i < d.getAvailableVersions().size(); i++) {
                        if (i > 0) versionList.append("<br>");
                        versionList.append("<span style='font-size:0.85em;color:#667eea;'>")
                                  .append(escapeHtml(d.getAvailableVersions().get(i)))
                                  .append("</span>");
                    }
                    availableVersions = versionList.toString();
                } else {
                    availableVersions = "<span style='font-size:0.85em;color:#999;'>-</span>";
                }

                // Latest version from repository health
                String latestVersion = "";
                if (d.getRepositoryHealth() != null && d.getRepositoryHealth().getLatestVersion() != null) {
                    latestVersion = "<span style='font-size:0.9em;font-weight:bold;color:#764ba2;'>📦 " +
                        escapeHtml(d.getRepositoryHealth().getLatestVersion()) + "</span>";
                } else {
                    latestVersion = "<span style='font-size:0.85em;color:#999;'>-</span>";
                }

                sb.append("<tr>\n<td><strong>").append(ga).append("</strong></td>\n")
                  .append("<td>").append(currentVersion).append("</td>\n")
                  .append("<td>").append(d.getScope()==null?"":escapeHtml(d.getScope())).append("</td>\n")
                  .append("<td>").append(size).append("</td>\n")
                  .append("<td>").append(status).append("</td>\n")
                  .append("<td>").append(healthBadge).append("</td>\n")
                  .append("<td>").append(availableVersions).append("</td>\n")
                  .append("<td>").append(latestVersion).append("</td>\n")
                  .append("<td>").append(who==null?"":who).append("</td>\n</tr>\n");
            }
            sb.append("</tbody>\n</table>\n");
        }

        // Undeclared table
        if (out.getRawResults() != null && out.getRawResults().getUndeclared() != null && !out.getRawResults().getUndeclared().isEmpty()) {
            sb.append("<h2>📦 Undeclared Dependencies (").append(out.getRawResults().getUndeclared().size()).append(")</h2>\n");
            sb.append("<table>\n<thead>\n<tr><th>Artifact</th><th>Current</th><th>Scope</th><th>Size</th><th>Repo Health</th><th>Available Versions</th><th>Latest</th><th>Recommendation</th></tr>\n</thead>\n<tbody>\n");
            for (AnalyzedDependency d : out.getRawResults().getUndeclared()) {
                String ga = (d.getGroupId()==null?"":escapeHtml(d.getGroupId()))+":"+(d.getArtifactId()==null?"":escapeHtml(d.getArtifactId()));
                String currentVersion = d.getVersion()==null?"":escapeHtml(d.getVersion());
                String size = (d.getMetadata()!=null && d.getMetadata().getSizeKB()!=null)?(String.format("%.0f KB", d.getMetadata().getSizeKB())):"";

                // Repository health badge
                String healthBadge = "";
                if (d.getRepositoryHealth() != null) {
                    RepositoryHealth health = d.getRepositoryHealth();
                    switch (health.getLevel()) {
                        case HEALTHY:
                            healthBadge = "<span class='badge ok' title='" +
                                escapeHtml(health.getPositives() != null ? String.join(", ", health.getPositives()) : "Healthy") +
                                "'>✓ HEALTHY</span>";
                            break;
                        case WARNING:
                            healthBadge = "<span class='badge warn' title='" +
                                escapeHtml(health.getConcerns() != null ? String.join(", ", health.getConcerns()) : "Some concerns") +
                                "'>⚠ WARNING</span>";
                            break;
                        case DANGER:
                            healthBadge = "<span class='badge error' title='" +
                                escapeHtml(health.getConcerns() != null ? String.join(", ", health.getConcerns()) : "Serious concerns") +
                                "'>⛔ DANGER</span>";
                            break;
                        case UNKNOWN:
                            healthBadge = "<span class='badge' style='background:#e0e0e0;color:#666;'>? UNKNOWN</span>";
                            break;
                    }
                }

                // Available versions - display as list
                String availableVersions = "";
                if (d.getAvailableVersions() != null && !d.getAvailableVersions().isEmpty()) {
                    StringBuilder versionList = new StringBuilder();
                    for (int i = 0; i < d.getAvailableVersions().size(); i++) {
                        if (i > 0) versionList.append("<br>");
                        versionList.append("<span style='font-size:0.85em;color:#667eea;'>")
                                  .append(escapeHtml(d.getAvailableVersions().get(i)))
                                  .append("</span>");
                    }
                    availableVersions = versionList.toString();
                } else {
                    availableVersions = "<span style='font-size:0.85em;color:#999;'>-</span>";
                }

                // Latest version from repository health
                String latestVersion = "";
                if (d.getRepositoryHealth() != null && d.getRepositoryHealth().getLatestVersion() != null) {
                    latestVersion = "<span style='font-size:0.9em;font-weight:bold;color:#764ba2;'>📦 " +
                        escapeHtml(d.getRepositoryHealth().getLatestVersion()) + "</span>";
                } else {
                    latestVersion = "<span style='font-size:0.85em;color:#999;'>-</span>";
                }

                sb.append("<tr>\n<td><strong>").append(ga).append("</strong></td>\n")
                  .append("<td>").append(currentVersion).append("</td>\n")
                  .append("<td>").append(d.getScope()==null?"":escapeHtml(d.getScope())).append("</td>\n")
                  .append("<td>").append(size).append("</td>\n")
                  .append("<td>").append(healthBadge).append("</td>\n")
                  .append("<td>").append(availableVersions).append("</td>\n")
                  .append("<td>").append(latestVersion).append("</td>\n")
                  .append("<td>Add to pom.xml</td>\n</tr>\n");
            }
            sb.append("</tbody>\n</table>\n");
        }

        // Conflicts table
        if (out.getVersionConflicts() != null && !out.getVersionConflicts().isEmpty()) {
            sb.append("<h2>⚠️ Version Conflicts (").append(out.getVersionConflicts().size()).append(")</h2>\n");
            sb.append("<table>\n<thead>\n<tr><th>Artifact</th><th>Selected</th><th>Versions</th><th>Risk</th></tr>\n</thead>\n<tbody>\n");
            for (io.github.tourem.maven.descriptor.model.analysis.VersionConflict vc : out.getVersionConflicts()) {
                String ga = escapeHtml(vc.getGroupId())+":"+escapeHtml(vc.getArtifactId());
                String risk = vc.getRiskLevel()==io.github.tourem.maven.descriptor.model.analysis.VersionConflict.RiskLevel.HIGH?"<span class='badge riskH'>HIGH</span>"
                        : (vc.getRiskLevel()==io.github.tourem.maven.descriptor.model.analysis.VersionConflict.RiskLevel.MEDIUM?"<span class='badge riskM'>MEDIUM</span>":"<span class='badge ok'>LOW</span>");
                sb.append("<tr>\n<td><strong>").append(ga).append("</strong></td>\n")
                  .append("<td>").append(vc.getSelectedVersion()==null?"":escapeHtml(vc.getSelectedVersion())).append("</td>\n")
                  .append("<td>").append(escapeHtml(String.join(", ", vc.getVersions()))).append("</td>\n")
                  .append("<td>").append(risk).append("</td>\n</tr>\n");
            }
            sb.append("</tbody>\n</table>\n");
        }

        // Recommendations quick list
        if (out.getRecommendations() != null && !out.getRecommendations().isEmpty()) {
            sb.append("<h2>💡 Recommendations (").append(out.getRecommendations().size()).append(")</h2>\n<ul style='line-height:1.8;'>\n");
            for (io.github.tourem.maven.descriptor.model.analysis.Recommendation r : out.getRecommendations()) {
                sb.append("<li>").append(r.getType() != null ? escapeHtml(r.getType().toString()) : "").append(": ")
                  .append(escapeHtml(r.getGroupId())).append(":").append(escapeHtml(r.getArtifactId()))
                  .append(r.getVersion()==null?"":" ("+escapeHtml(r.getVersion())+")")
                  .append("</li>\n");
            }
            sb.append("</ul>\n");
        }

        // Maven Plugins section
        if (out.getPlugins() != null && out.getPlugins().getSummary() != null) {
            var pluginSummary = out.getPlugins().getSummary();
            sb.append("<h2>🔌 Maven Plugins</h2>\n");
            sb.append("<div class='cards'>\n");
            sb.append("<div class='card'><div class='label'>Total Plugins</div><div class='value'>")
              .append(pluginSummary.getTotal() != null ? pluginSummary.getTotal() : 0).append("</div></div>\n");
            sb.append("<div class='card'><div class='label'>With Configuration</div><div class='value'>")
              .append(pluginSummary.getWithConfiguration() != null ? pluginSummary.getWithConfiguration() : 0).append("</div></div>\n");
            sb.append("<div class='card'><div class='label'>From Management</div><div class='value'>")
              .append(pluginSummary.getFromManagement() != null ? pluginSummary.getFromManagement() : 0).append("</div></div>\n");
            sb.append("<div class='card'><div class='label'>Outdated</div><div class='value'>")
              .append(pluginSummary.getOutdated() != null ? pluginSummary.getOutdated() : 0).append("</div></div>\n");
            sb.append("</div>\n");

            // Plugin list table
            if (out.getPlugins().getList() != null && !out.getPlugins().getList().isEmpty()) {
                sb.append("<h3>Build Plugins (").append(out.getPlugins().getList().size()).append(")</h3>\n");
                sb.append("<table>\n<thead>\n<tr><th>Plugin</th><th>Version</th><th>Phase</th><th>Goals</th><th>Status</th></tr>\n</thead>\n<tbody>\n");
                for (io.github.tourem.maven.descriptor.model.PluginDetail plugin : out.getPlugins().getList()) {
                    String ga = (plugin.getGroupId() == null ? "" : escapeHtml(plugin.getGroupId())) + ":" + escapeHtml(plugin.getArtifactId());
                    String version = plugin.getVersion() != null ? escapeHtml(plugin.getVersion()) : "inherited";
                    String phase = plugin.getPhase() != null ? escapeHtml(plugin.getPhase()) : "-";
                    String goals = plugin.getGoals() != null ? escapeHtml(String.join(", ", plugin.getGoals())) : "-";

                    String statusBadge = "";
                    if (plugin.getOutdated() != null && plugin.getOutdated().getLatest() != null) {
                        statusBadge = "<span class='badge warn' title='Update available: " + escapeHtml(plugin.getOutdated().getLatest()) + "'>⚠ OUTDATED</span>";
                    } else {
                        statusBadge = "<span class='badge ok'>✓ UP-TO-DATE</span>";
                    }

                    sb.append("<tr>\n<td><strong>").append(ga).append("</strong></td>\n")
                      .append("<td>").append(version).append("</td>\n")
                      .append("<td>").append(phase).append("</td>\n")
                      .append("<td>").append(goals).append("</td>\n")
                      .append("<td>").append(statusBadge).append("</td>\n</tr>\n");
                }
                sb.append("</tbody>\n</table>\n");
            }
        }

        // Close content div
        sb.append("</div>\n");

        // Close container div
        sb.append("</div>\n");

        // JavaScript for theme toggle
        sb.append("<script>\n");
        sb.append("function toggleTheme() {\n");
        sb.append("  const body = document.body;\n");
        sb.append("  const themeIcon = document.querySelector('.theme-icon');\n");
        sb.append("  body.classList.toggle('dark-mode');\n");
        sb.append("  if (body.classList.contains('dark-mode')) {\n");
        sb.append("    themeIcon.textContent = '☀️';\n");
        sb.append("    localStorage.setItem('theme', 'dark');\n");
        sb.append("  } else {\n");
        sb.append("    themeIcon.textContent = '🌙';\n");
        sb.append("    localStorage.setItem('theme', 'light');\n");
        sb.append("  }\n");
        sb.append("}\n");
        sb.append("document.addEventListener('DOMContentLoaded', function() {\n");
        sb.append("  const savedTheme = localStorage.getItem('theme');\n");
        sb.append("  const themeIcon = document.querySelector('.theme-icon');\n");
        sb.append("  if (savedTheme === 'dark') {\n");
        sb.append("    document.body.classList.add('dark-mode');\n");
        sb.append("    themeIcon.textContent = '☀️';\n");
        sb.append("  }\n");
        sb.append("});\n");
        sb.append("</script>\n");

        sb.append("</body>\n</html>");
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

    private io.github.tourem.maven.descriptor.model.PluginInfo collectPlugins() {
        try {
            // Parse the POM file
            File pomFile = project.getFile();
            if (pomFile == null || !pomFile.exists()) {
                return null;
            }

            org.apache.maven.model.io.xpp3.MavenXpp3Reader reader = new org.apache.maven.model.io.xpp3.MavenXpp3Reader();
            org.apache.maven.model.Model model;
            try (java.io.FileReader fileReader = new java.io.FileReader(pomFile)) {
                model = reader.read(fileReader);
            }

            // Use PluginCollector to collect plugin information
            io.github.tourem.maven.descriptor.service.PluginCollector pluginCollector =
                new io.github.tourem.maven.descriptor.service.PluginCollector();

            io.github.tourem.maven.descriptor.model.PluginOptions options =
                io.github.tourem.maven.descriptor.model.PluginOptions.builder()
                    .include(true)
                    .includePluginManagement(true)
                    .includePluginConfiguration(true)
                    .checkPluginUpdates(true)
                    .build();

            return pluginCollector.collect(model, pomFile.toPath().getParent(), options);
        } catch (Exception e) {
            getLog().debug("Failed to collect plugin information: " + e.getMessage());
            return null;
        }
    }

    /**
     * Collect dependency tree information.
     */
    private io.github.tourem.maven.descriptor.model.DependencyTreeInfo collectDependencyTree() {
        try {
            File pomFile = project.getFile();
            if (pomFile == null || !pomFile.exists()) {
                return null;
            }

            org.apache.maven.model.io.xpp3.MavenXpp3Reader reader = new org.apache.maven.model.io.xpp3.MavenXpp3Reader();
            org.apache.maven.model.Model model;
            try (java.io.FileReader fileReader = new java.io.FileReader(pomFile)) {
                model = reader.read(fileReader);
            }

            io.github.tourem.maven.descriptor.service.DependencyTreeCollector collector =
                new io.github.tourem.maven.descriptor.service.DependencyTreeCollector();

            // Parse format
            io.github.tourem.maven.descriptor.model.DependencyTreeFormat format;
            if ("both".equalsIgnoreCase(dependencyTreeFormat)) {
                format = io.github.tourem.maven.descriptor.model.DependencyTreeFormat.BOTH;
            } else if ("flat".equalsIgnoreCase(dependencyTreeFormat)) {
                format = io.github.tourem.maven.descriptor.model.DependencyTreeFormat.FLAT;
            } else {
                format = io.github.tourem.maven.descriptor.model.DependencyTreeFormat.TREE;
            }

            io.github.tourem.maven.descriptor.model.DependencyTreeOptions.DependencyTreeOptionsBuilder optionsBuilder =
                io.github.tourem.maven.descriptor.model.DependencyTreeOptions.builder()
                    .include(true)
                    .depth(dependencyTreeDepth)
                    .format(format);

            // Add scopes if specified
            if (dependencyTreeScope != null && !dependencyTreeScope.isBlank()) {
                java.util.Set<String> scopes = new java.util.HashSet<>();
                for (String scope : dependencyTreeScope.split(",")) {
                    scopes.add(scope.trim());
                }
                optionsBuilder.scopes(scopes);
            }

            io.github.tourem.maven.descriptor.model.DependencyTreeOptions options = optionsBuilder.build();

            return collector.collect(model, pomFile.toPath().getParent(), options);
        } catch (Exception e) {
            getLog().debug("Failed to collect dependency tree: " + e.getMessage());
            return null;
        }
    }

    /**
     * Write outputs based on exportFormat parameter.
     */
    private void writeOutputs(DependencyAnalysisResult out) throws IOException {
        String[] formats = exportFormat.split(",");
        for (String format : formats) {
            format = format.trim().toLowerCase();
            switch (format) {
                case "json":
                    writeJson(out);
                    break;
                case "yaml":
                case "yml":
                    writeYaml(out);
                    break;
                case "both":
                    writeJson(out);
                    writeYaml(out);
                    break;
                default:
                    getLog().warn("Unknown export format: " + format + ". Using JSON.");
                    writeJson(out);
            }
        }
    }

    /**
     * Write YAML output.
     */
    private void writeYaml(DependencyAnalysisResult out) throws IOException {
        File dir = analysisOutputDir != null ? analysisOutputDir : new File(project.getBuild().getDirectory());
        String yamlName = analysisOutputFile != null && analysisOutputFile.endsWith(".json")
                ? analysisOutputFile.replace(".json", ".yaml")
                : (analysisOutputFile == null || analysisOutputFile.isBlank() ? "dependency-analysis.yaml" : analysisOutputFile + ".yaml");
        File yamlFile = new File(dir, yamlName);

        org.yaml.snakeyaml.DumperOptions options = new org.yaml.snakeyaml.DumperOptions();
        options.setDefaultFlowStyle(org.yaml.snakeyaml.DumperOptions.FlowStyle.BLOCK);
        options.setPrettyFlow(true);
        org.yaml.snakeyaml.Yaml yaml = new org.yaml.snakeyaml.Yaml(options);

        try (java.io.OutputStreamWriter writer = new java.io.OutputStreamWriter(
                new java.io.FileOutputStream(yamlFile), java.nio.charset.StandardCharsets.UTF_8)) {
            yaml.dump(convertToMap(out), writer);
        }
        getLog().info("Dependency analysis YAML generated: " + yamlFile.getAbsolutePath());
    }

    /**
     * Convert DependencyAnalysisResult to Map for YAML serialization.
     */
    private java.util.Map<String, Object> convertToMap(DependencyAnalysisResult out) throws com.fasterxml.jackson.core.JsonProcessingException {
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new com.fasterxml.jackson.datatype.jsr310.JavaTimeModule());
        mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        String json = mapper.writeValueAsString(out);
        return mapper.readValue(json, new com.fasterxml.jackson.core.type.TypeReference<java.util.Map<String, Object>>() {});
    }

    /**
     * Escape HTML special characters to prevent XSS and rendering issues.
     */
    private String escapeHtml(String text) {
        if (text == null) return "";
        return text.replace("&", "&amp;")
                   .replace("<", "&lt;")
                   .replace(">", "&gt;")
                   .replace("\"", "&quot;")
                   .replace("'", "&#39;");
    }
}

