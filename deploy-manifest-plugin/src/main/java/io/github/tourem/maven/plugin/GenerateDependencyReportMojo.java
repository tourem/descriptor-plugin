package io.github.tourem.maven.plugin;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import io.github.tourem.maven.descriptor.model.DependencyReport;
import io.github.tourem.maven.descriptor.model.DependencyTreeInfo;
import io.github.tourem.maven.descriptor.model.analysis.DependencyAnalysisResult;
import io.github.tourem.maven.descriptor.service.DependencyTreeCollector;
import io.github.tourem.maven.descriptor.model.DependencyTreeOptions;
import org.apache.maven.model.Plugin;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;
import org.apache.maven.execution.MavenSession;
import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.Yaml;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Maven goal to generate a comprehensive dependency and plugin report.
 *
 * @author tourem
 */
@Mojo(name = "dependency-report", defaultPhase = LifecyclePhase.VERIFY, threadSafe = true)
public class GenerateDependencyReportMojo extends AbstractMojo {

    @Parameter(defaultValue = "${project}", readonly = true, required = true)
    private MavenProject project;

    @Parameter(defaultValue = "${session}", readonly = true)
    private MavenSession session;

    @Parameter(property = "dependency.report.outputDir")
    private File outputDir;

    @Parameter(property = "dependency.report.outputFile", defaultValue = "dependency-report")
    private String outputFile;

    @Parameter(property = "dependency.report.formats", defaultValue = "json,html")
    private String formats;

    @Parameter(property = "dependency.report.includeAnalysis", defaultValue = "true")
    private boolean includeAnalysis;

    @Parameter(property = "dependency.report.includeDependencyTree", defaultValue = "true")
    private boolean includeDependencyTree;

    @Parameter(property = "dependency.report.includePlugins", defaultValue = "true")
    private boolean includePlugins;

    @Parameter(property = "dependency.report.lookupAvailableVersions", defaultValue = "true")
    private boolean lookupAvailableVersions;

    @Parameter(property = "dependency.report.maxAvailableVersions", defaultValue = "3")
    private int maxAvailableVersions;

    @Parameter(property = "dependency.report.versionLookupTimeoutMs", defaultValue = "5000")
    private int versionLookupTimeoutMs;

    @Override
    public void execute() throws MojoExecutionException {
        try {
            DependencyReport.DependencyReportBuilder builder = DependencyReport.builder()
                    .timestamp(Instant.now())
                    .project(buildProjectInfo());

            if (includeDependencyTree) {
                builder.dependencyTree(collectDependencyTree());
            }

            if (includeAnalysis) {
                builder.analysis(runDependencyAnalysis());
            }

            if (includePlugins) {
                builder.plugins(collectPluginInfo());
            }

            DependencyReport report = builder.build();

            String[] formatArray = formats.split(",");
            for (String format : formatArray) {
                format = format.trim().toLowerCase();
                switch (format) {
                    case "json":
                        writeJson(report);
                        break;
                    case "yaml":
                    case "yml":
                        writeYaml(report);
                        break;
                    case "html":
                        writeHtml(report);
                        break;
                    default:
                        getLog().warn("Unknown format: " + format);
                }
            }

            getLog().info("Dependency report generated: " + getOutputPath("json"));
        } catch (Exception e) {
            throw new MojoExecutionException("Failed to generate dependency report", e);
        }
    }

    private DependencyReport.ProjectInfo buildProjectInfo() {
        return DependencyReport.ProjectInfo.builder()
                .groupId(project.getGroupId())
                .artifactId(project.getArtifactId())
                .version(project.getVersion())
                .packaging(project.getPackaging())
                .name(project.getName())
                .description(project.getDescription())
                .build();
    }

    private DependencyTreeInfo collectDependencyTree() {
        try {
            DependencyTreeCollector collector = new DependencyTreeCollector();
            DependencyTreeOptions options = DependencyTreeOptions.builder()
                    .include(true)
                    .depth(-1)
                    .format(io.github.tourem.maven.descriptor.model.DependencyTreeFormat.BOTH)
                    .build();
            return collector.collect(project.getModel(), project.getBasedir().toPath(), options);
        } catch (Exception e) {
            getLog().warn("Failed to collect dependency tree: " + e.getMessage());
            return null;
        }
    }

    private DependencyAnalysisResult runDependencyAnalysis() {
        File analysisFile = new File(getTargetDir(), "dependency-analysis.json");
        DependencyAnalysisResult result = null;

        if (analysisFile.exists()) {
            try {
                ObjectMapper mapper = new ObjectMapper();
                mapper.registerModule(new JavaTimeModule());
                result = mapper.readValue(analysisFile, DependencyAnalysisResult.class);
            } catch (Exception e) {
                getLog().warn("Failed to read existing analysis: " + e.getMessage());
            }
        }

        // Enrich ALL dependencies with available versions if requested
        if (lookupAvailableVersions && result != null) {
            enrichAllDependenciesWithVersions(result);
        }

        return result;
    }

    /**
     * Enrich ALL dependencies (unused + undeclared) with available versions.
     * This ensures the "Available Updates" tab shows all dependencies, not just problematic ones.
     */
    private void enrichAllDependenciesWithVersions(DependencyAnalysisResult result) {
        if (result == null || result.getRawResults() == null) {
            return;
        }

        try {
            io.github.tourem.maven.descriptor.service.DependencyVersionLookup versionLookup =
                new io.github.tourem.maven.descriptor.service.DependencyVersionLookup(
                    project.getModel(),
                    versionLookupTimeoutMs
                );

            // Enrich unused dependencies
            if (result.getRawResults().getUnused() != null) {
                for (io.github.tourem.maven.descriptor.model.analysis.AnalyzedDependency dep : result.getRawResults().getUnused()) {
                    if (dep.getAvailableVersions() == null || dep.getAvailableVersions().isEmpty()) {
                        enrichDependencyWithVersions(dep, versionLookup);
                    }
                }
            }

            // Enrich undeclared dependencies
            if (result.getRawResults().getUndeclared() != null) {
                for (io.github.tourem.maven.descriptor.model.analysis.AnalyzedDependency dep : result.getRawResults().getUndeclared()) {
                    if (dep.getAvailableVersions() == null || dep.getAvailableVersions().isEmpty()) {
                        enrichDependencyWithVersions(dep, versionLookup);
                    }
                }
            }
        } catch (Exception e) {
            getLog().warn("Failed to enrich dependencies with versions: " + e.getMessage());
        }
    }

    private void enrichDependencyWithVersions(
            io.github.tourem.maven.descriptor.model.analysis.AnalyzedDependency dep,
            io.github.tourem.maven.descriptor.service.DependencyVersionLookup versionLookup) {
        try {
            List<String> availableVersions = versionLookup.lookupAvailableVersions(
                dep.getGroupId(),
                dep.getArtifactId(),
                dep.getVersion(),
                maxAvailableVersions
            );

            if (availableVersions != null && !availableVersions.isEmpty()) {
                dep.setAvailableVersions(availableVersions);
            }
        } catch (Exception e) {
            getLog().debug("Failed to lookup versions for " + dep.getGroupId() + ":" +
                          dep.getArtifactId() + ": " + e.getMessage());
        }
    }




    private DependencyReport.PluginReport collectPluginInfo() {
        List<DependencyReport.PluginInfo> buildPlugins = new ArrayList<>();
        List<DependencyReport.PluginInfo> managementPlugins = new ArrayList<>();

        if (project.getBuild() != null && project.getBuild().getPlugins() != null) {
            for (Plugin plugin : project.getBuild().getPlugins()) {
                buildPlugins.add(mapPlugin(plugin));
            }
        }

        if (project.getPluginManagement() != null &&
            project.getPluginManagement().getPlugins() != null) {
            for (Plugin plugin : project.getPluginManagement().getPlugins()) {
                managementPlugins.add(mapPlugin(plugin));
            }
        }

        DependencyReport.PluginSummary summary = DependencyReport.PluginSummary.builder()
                .totalPlugins(buildPlugins.size() + managementPlugins.size())
                .buildPlugins(buildPlugins.size())
                .managementPlugins(managementPlugins.size())
                .updatesAvailable(0)
                .build();

        return DependencyReport.PluginReport.builder()
                .build(buildPlugins)
                .management(managementPlugins)
                .summary(summary)
                .build();
    }

    private DependencyReport.PluginInfo mapPlugin(Plugin plugin) {
        return DependencyReport.PluginInfo.builder()
                .groupId(plugin.getGroupId())
                .artifactId(plugin.getArtifactId())
                .version(plugin.getVersion())
                .currentVersion(plugin.getVersion())
                .build();
    }

    private void writeJson(DependencyReport report) throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());
        mapper.enable(SerializationFeature.INDENT_OUTPUT);
        mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

        File outputFile = new File(getOutputPath("json"));
        outputFile.getParentFile().mkdirs();
        mapper.writeValue(outputFile, report);
        getLog().info("JSON report written to: " + outputFile.getAbsolutePath());
    }

    private void writeYaml(DependencyReport report) throws Exception {
        DumperOptions options = new DumperOptions();
        options.setDefaultFlowStyle(DumperOptions.FlowStyle.BLOCK);
        options.setPrettyFlow(true);
        Yaml yaml = new Yaml(options);

        File outputFile = new File(getOutputPath("yaml"));
        outputFile.getParentFile().mkdirs();

        try (OutputStreamWriter writer = new OutputStreamWriter(
                new FileOutputStream(outputFile), StandardCharsets.UTF_8)) {
            yaml.dump(report, writer);
        }
        getLog().info("YAML report written to: " + outputFile.getAbsolutePath());
    }

    private void writeHtml(DependencyReport report) throws Exception {
        File outputFile = new File(getOutputPath("html"));
        outputFile.getParentFile().mkdirs();

        StringBuilder html = new StringBuilder();
        html.append("<!DOCTYPE html>\n<html lang=\"en\">\n<head>\n");
        html.append("<meta charset=\"UTF-8\">\n");
        html.append("<meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\">\n");
        html.append("<title>Dependency & Plugin Report - ").append(escapeHtml(report.getProject().getArtifactId())).append("</title>\n");
        html.append("<style>\n");

        // Modern CSS
        html.append("* { margin: 0; padding: 0; box-sizing: border-box; }\n");
        html.append("body { font-family: 'Segoe UI', Tahoma, Geneva, Verdana, sans-serif; background: linear-gradient(135deg, #667eea 0%, #764ba2 100%); min-height: 100vh; padding: 20px; }\n");
        html.append(".container { max-width: 1400px; margin: 0 auto; background: white; border-radius: 20px; box-shadow: 0 20px 60px rgba(0,0,0,0.3); overflow: hidden; }\n");

        // Header
        html.append(".header { background: linear-gradient(135deg, #667eea 0%, #764ba2 100%); color: white; padding: 40px; }\n");
        html.append(".header h1 { font-size: 2.5em; margin-bottom: 10px; }\n");
        html.append(".header .subtitle { font-size: 1.1em; opacity: 0.9; }\n");
        html.append(".header .timestamp { margin-top: 15px; font-size: 0.9em; opacity: 0.8; }\n");

        // Stats Cards
        html.append(".stats { display: grid; grid-template-columns: repeat(auto-fit, minmax(200px, 1fr)); gap: 20px; padding: 30px; background: #f8f9fa; }\n");
        html.append(".stat-card { background: white; padding: 25px; border-radius: 15px; text-align: center; box-shadow: 0 4px 15px rgba(0,0,0,0.1); transition: transform 0.3s; }\n");
        html.append(".stat-card:hover { transform: translateY(-5px); }\n");
        html.append(".stat-card .number { font-size: 2.5em; font-weight: bold; background: linear-gradient(135deg, #667eea 0%, #764ba2 100%); -webkit-background-clip: text; -webkit-text-fill-color: transparent; }\n");
        html.append(".stat-card .label { color: #666; margin-top: 10px; font-size: 0.9em; text-transform: uppercase; letter-spacing: 1px; }\n");

        // Tabs
        html.append(".tabs { display: flex; background: #f8f9fa; border-bottom: 2px solid #e0e0e0; padding: 0 30px; overflow-x: auto; }\n");
        html.append(".tab { padding: 20px 30px; cursor: pointer; border: none; background: none; font-size: 1em; font-weight: 600; color: #666; position: relative; transition: color 0.3s; }\n");
        html.append(".tab:hover { color: #667eea; }\n");
        html.append(".tab.active { color: #667eea; }\n");
        html.append(".tab.active::after { content: ''; position: absolute; bottom: -2px; left: 0; right: 0; height: 3px; background: linear-gradient(135deg, #667eea 0%, #764ba2 100%); }\n");

        // Tab Content
        html.append(".tab-content { display: none; padding: 40px; }\n");
        html.append(".tab-content.active { display: block; }\n");

        // Section Headers
        html.append(".section-header { font-size: 1.4em; font-weight: bold; color: #333; margin: 30px 0 20px 0; padding-bottom: 10px; border-bottom: 2px solid #e0e0e0; }\n");

        // Tables
        html.append(".table-container { overflow-x: auto; margin: 20px 0; border-radius: 10px; box-shadow: 0 2px 10px rgba(0,0,0,0.05); }\n");
        html.append("table { width: 100%; border-collapse: collapse; background: white; }\n");
        html.append("th { background: linear-gradient(135deg, #667eea 0%, #764ba2 100%); color: white; padding: 15px; text-align: left; font-weight: 600; text-transform: uppercase; font-size: 0.85em; }\n");
        html.append("td { padding: 15px; border-bottom: 1px solid #e0e0e0; color: #333; }\n");
        html.append("tr:last-child td { border-bottom: none; }\n");
        html.append("tr:hover { background: #f8f9fa; }\n");

        // Badges
        html.append(".badge { display: inline-block; padding: 6px 14px; border-radius: 20px; font-size: 0.75em; font-weight: bold; text-transform: uppercase; }\n");
        html.append(".badge-ok { background: #10b9811a; color: #10b981; }\n");
        html.append(".badge-warn { background: #f59e0b1a; color: #f59e0b; }\n");
        html.append(".badge-error { background: #ef44441a; color: #ef4444; }\n");
        html.append(".badge-info { background: #3b82f61a; color: #3b82f6; }\n");
        html.append(".badge-success { background: #10b9811a; color: #10b981; }\n");
        html.append(".badge-scope { background: #6366f11a; color: #6366f1; }\n");
        html.append(".badge-optional { background: #f59e0b1a; color: #f59e0b; }\n");

        // Code
        html.append("code { background: #2d2d2d; color: #f8f8f2; padding: 4px 8px; border-radius: 5px; font-family: 'Courier New', monospace; font-size: 0.9em; }\n");

        // Version highlighting
        html.append(".version-current { background: #3b82f61a; color: #3b82f6; padding: 4px 8px; border-radius: 5px; font-weight: bold; }\n");
        html.append(".version-available { background: #10b9811a; color: #10b981; padding: 4px 8px; border-radius: 5px; }\n");
        html.append(".version-latest { background: #8b5cf61a; color: #8b5cf6; padding: 4px 8px; border-radius: 5px; font-weight: bold; }\n");
        html.append(".version-outdated { background: #ef44441a; color: #ef4444; padding: 4px 8px; border-radius: 5px; font-weight: bold; }\n");
        html.append(".update-alert { background: #fef3c7; border-left: 4px solid #f59e0b; padding: 10px; margin: 10px 0; border-radius: 5px; }\n");
        html.append(".update-critical { background: #fee2e2; border-left: 4px solid #ef4444; padding: 10px; margin: 10px 0; border-radius: 5px; }\n");

        // Dependency Tree
        html.append(".tree-container { margin: 20px 0; }\n");
        html.append(".dependency-tree { background: #f8f9fa; padding: 20px; border-radius: 10px; margin-top: 15px; font-family: 'Courier New', monospace; }\n");
        html.append(".tree-node { margin: 5px 0; padding: 5px; border-radius: 5px; transition: background 0.2s; }\n");
        html.append(".tree-node:hover { background: #e5e7eb; }\n");
        html.append(".tree-toggle { cursor: pointer; display: inline-block; width: 20px; color: #667eea; font-weight: bold; user-select: none; }\n");
        html.append(".tree-toggle:hover { color: #764ba2; }\n");
        html.append(".tree-leaf { display: inline-block; width: 20px; color: #999; }\n");
        html.append(".tree-artifact { margin-left: 5px; }\n");
        html.append(".tree-children { margin-left: 20px; }\n");

        // False Positive Row
        html.append(".false-positive-row { background: #fef3c7 !important; }\n");
        html.append(".false-positive-row:hover { background: #fde68a !important; }\n");

        // Buttons
        html.append(".btn { padding: 8px 16px; margin: 5px; border: none; border-radius: 5px; cursor: pointer; font-weight: bold; transition: all 0.3s; }\n");
        html.append(".btn-secondary { background: #6366f1; color: white; }\n");
        html.append(".btn-secondary:hover { background: #4f46e5; transform: translateY(-2px); }\n");

        html.append("</style>\n</head>\n<body>\n");
        html.append("<div class=\"container\">\n");

        // Header
        html.append("<div class=\"header\">\n");
        html.append("<h1>📦 Dependency & Plugin Report</h1>\n");
        html.append("<div class=\"subtitle\">").append(escapeHtml(report.getProject().getGroupId())).append(":").append(escapeHtml(report.getProject().getArtifactId())).append("</div>\n");
        html.append("<div class=\"timestamp\">📅 Generated: ").append(report.getTimestamp()).append("</div>\n");
        html.append("</div>\n");

        // Stats Cards
        html.append("<div class=\"stats\">\n");
        html.append("<div class=\"stat-card\"><div class=\"number\">").append(escapeHtml(report.getProject().getVersion())).append("</div><div class=\"label\">Version</div></div>\n");

        if (report.getDependencyTree() != null && report.getDependencyTree().getSummary() != null) {
            html.append("<div class=\"stat-card\"><div class=\"number\">").append(report.getDependencyTree().getSummary().getTotal()).append("</div><div class=\"label\">Dependencies</div></div>\n");
        }

        if (report.getAnalysis() != null && report.getAnalysis().getHealthScore() != null) {
            html.append("<div class=\"stat-card\"><div class=\"number\">").append(report.getAnalysis().getHealthScore().getOverall()).append("/100</div><div class=\"label\">Health Score</div></div>\n");
        }

        if (report.getPlugins() != null && report.getPlugins().getSummary() != null) {
            html.append("<div class=\"stat-card\"><div class=\"number\">").append(report.getPlugins().getSummary().getTotalPlugins()).append("</div><div class=\"label\">Plugins</div></div>\n");
        }
        html.append("</div>\n");

        // Tabs
        html.append("<div class=\"tabs\">\n");
        html.append("<button class=\"tab active\" onclick=\"showTab(this, 'overview')\">📊 Overview</button>\n");
        if (report.getDependencyTree() != null) {
            html.append("<button class=\"tab\" onclick=\"showTab(this, 'dependencies')\">📦 Dependency Tree</button>\n");
        }
        if (report.getAnalysis() != null) {
            html.append("<button class=\"tab\" onclick=\"showTab(this, 'analysis')\">🔍 Analysis</button>\n");
            html.append("<button class=\"tab\" onclick=\"showTab(this, 'updates')\">🔄 Available Updates</button>\n");
        }
        if (report.getPlugins() != null) {
            html.append("<button class=\"tab\" onclick=\"showTab(this, 'plugins')\">🔌 Plugins</button>\n");
        }
        html.append("</div>\n");

        // Tab 1: Overview
        html.append("<div id=\"overview\" class=\"tab-content active\">\n");
        html.append("<div class=\"section-header\">📋 Project Information</div>\n");
        html.append("<table>\n");
        html.append("<tr><th>Property</th><th>Value</th></tr>\n");
        html.append("<tr><td>Group ID</td><td><code>").append(escapeHtml(report.getProject().getGroupId())).append("</code></td></tr>\n");
        html.append("<tr><td>Artifact ID</td><td><code>").append(escapeHtml(report.getProject().getArtifactId())).append("</code></td></tr>\n");
        html.append("<tr><td>Version</td><td><code>").append(escapeHtml(report.getProject().getVersion())).append("</code></td></tr>\n");
        html.append("<tr><td>Packaging</td><td><code>").append(escapeHtml(report.getProject().getPackaging())).append("</code></td></tr>\n");
        if (report.getProject().getName() != null) {
            html.append("<tr><td>Name</td><td>").append(escapeHtml(report.getProject().getName())).append("</td></tr>\n");
        }
        if (report.getProject().getDescription() != null) {
            html.append("<tr><td>Description</td><td>").append(escapeHtml(report.getProject().getDescription())).append("</td></tr>\n");
        }
        html.append("</table>\n");
        html.append("</div>\n");

        // Continue with other tabs...
        writeHtmlDependenciesTab(html, report);
        writeHtmlAnalysisTab(html, report);
        writeHtmlAvailableUpdatesTab(html, report);
        writeHtmlPluginsTab(html, report);

        // JavaScript
        html.append("<script>\n");
        html.append("function showTab(btn, tabName) {\n");
        html.append("  document.querySelectorAll('.tab-content').forEach(c => c.classList.remove('active'));\n");
        html.append("  document.querySelectorAll('.tab').forEach(t => t.classList.remove('active'));\n");
        html.append("  document.getElementById(tabName).classList.add('active');\n");
        html.append("  btn.classList.add('active');\n");
        html.append("}\n");
        html.append("function toggleNode(nodeId) {\n");
        html.append("  var node = document.getElementById(nodeId);\n");
        html.append("  var toggle = node.previousElementSibling.querySelector('.tree-toggle');\n");
        html.append("  if (node.style.display === 'none') {\n");
        html.append("    node.style.display = 'block';\n");
        html.append("    toggle.textContent = '▼';\n");
        html.append("  } else {\n");
        html.append("    node.style.display = 'none';\n");
        html.append("    toggle.textContent = '▶';\n");
        html.append("  }\n");
        html.append("}\n");
        html.append("function expandAll() {\n");
        html.append("  document.querySelectorAll('.tree-children').forEach(n => n.style.display = 'block');\n");
        html.append("  document.querySelectorAll('.tree-toggle').forEach(t => t.textContent = '▼');\n");
        html.append("}\n");
        html.append("function collapseAll() {\n");
        html.append("  document.querySelectorAll('.tree-children').forEach(n => n.style.display = 'none');\n");
        html.append("  document.querySelectorAll('.tree-toggle').forEach(t => t.textContent = '▶');\n");
        html.append("}\n");
        html.append("</script>\n");

        html.append("</div>\n</body>\n</html>");

        try (FileOutputStream fos = new FileOutputStream(outputFile)) {
            fos.write(html.toString().getBytes(StandardCharsets.UTF_8));
        }
        getLog().info("HTML report written to: " + outputFile.getAbsolutePath());
    }

    private void writeHtmlDependenciesTab(StringBuilder html, DependencyReport report) {
        if (report.getDependencyTree() == null) return;

        html.append("<div id=\"dependencies\" class=\"tab-content\">\n");
        html.append("<div class=\"section-header\">📦 Dependency Tree Summary</div>\n");

        if (report.getDependencyTree().getSummary() != null) {
            var summary = report.getDependencyTree().getSummary();
            html.append("<table>\n");
            html.append("<tr><th>Metric</th><th>Value</th></tr>\n");
            html.append("<tr><td>Total Dependencies</td><td><strong>").append(summary.getTotal()).append("</strong></td></tr>\n");
            html.append("<tr><td>Direct Dependencies</td><td><strong>").append(summary.getDirect()).append("</strong></td></tr>\n");
            html.append("<tr><td>Transitive Dependencies</td><td><strong>").append(summary.getTransitive()).append("</strong></td></tr>\n");

            if (summary.getScopes() != null && !summary.getScopes().isEmpty()) {
                html.append("<tr><td colspan=\"2\"><strong>By Scope:</strong></td></tr>\n");
                summary.getScopes().forEach((scope, count) -> {
                    html.append("<tr><td>&nbsp;&nbsp;").append(escapeHtml(scope)).append("</td><td>").append(count).append("</td></tr>\n");
                });
            }
            html.append("</table>\n");
        }

        // Add dependency tree visualization
        if (report.getDependencyTree().getTree() != null && !report.getDependencyTree().getTree().isEmpty()) {
            html.append("<div class=\"section-header\">🌳 Dependency Tree</div>\n");
            html.append("<div class=\"tree-container\">\n");
            html.append("<button class=\"btn btn-secondary\" onclick=\"expandAll()\">Expand All</button>\n");
            html.append("<button class=\"btn btn-secondary\" onclick=\"collapseAll()\">Collapse All</button>\n");
            html.append("<div class=\"dependency-tree\">\n");

            for (var node : report.getDependencyTree().getTree()) {
                writeDependencyTreeNode(html, node, 0);
            }

            html.append("</div>\n");
            html.append("</div>\n");
        }

        html.append("</div>\n");
    }

    private void writeDependencyTreeNode(StringBuilder html, io.github.tourem.maven.descriptor.model.DependencyNode node, int depth) {
        String indent = "&nbsp;".repeat(depth * 4);
        String nodeId = "node-" + node.getGroupId().replace(".", "-") + "-" + node.getArtifactId() + "-" + depth;

        html.append("<div class=\"tree-node\" style=\"margin-left: ").append(depth * 20).append("px;\">\n");

        if (node.getChildren() != null && !node.getChildren().isEmpty()) {
            html.append("<span class=\"tree-toggle\" onclick=\"toggleNode('").append(nodeId).append("')\">▶</span>\n");
        } else {
            html.append("<span class=\"tree-leaf\">•</span>\n");
        }

        html.append("<span class=\"tree-artifact\">");
        html.append("<strong>").append(escapeHtml(node.getArtifactId())).append("</strong>");
        html.append(":<code>").append(escapeHtml(node.getVersion())).append("</code>");

        if (node.getScope() != null && !node.getScope().equals("compile")) {
            html.append(" <span class=\"badge badge-scope\">").append(escapeHtml(node.getScope())).append("</span>");
        }

        if (node.isOptional()) {
            html.append(" <span class=\"badge badge-optional\">optional</span>");
        }

        html.append("<br><small style=\"color: #666;\">").append(escapeHtml(node.getGroupId())).append("</small>");
        html.append("</span>\n");
        html.append("</div>\n");

        if (node.getChildren() != null && !node.getChildren().isEmpty()) {
            html.append("<div id=\"").append(nodeId).append("\" class=\"tree-children\" style=\"display: none;\">\n");
            for (var child : node.getChildren()) {
                writeDependencyTreeNode(html, child, depth + 1);
            }
            html.append("</div>\n");
        }
    }

    private void writeHtmlAnalysisTab(StringBuilder html, DependencyReport report) {
        if (report.getAnalysis() == null) return;

        html.append("<div id=\"analysis\" class=\"tab-content\">\n");
        html.append("<div class=\"section-header\">🔍 Dependency Analysis</div>\n");

        if (report.getAnalysis().getHealthScore() != null) {
            var healthScore = report.getAnalysis().getHealthScore();
            html.append("<table>\n");
            html.append("<tr><th>Metric</th><th>Score</th></tr>\n");
            html.append("<tr><td>Overall Health Score</td><td><strong>").append(healthScore.getOverall()).append("/100</strong> (").append(escapeHtml(healthScore.getGrade())).append(")</td></tr>\n");

            if (healthScore.getBreakdown() != null) {
                var breakdown = healthScore.getBreakdown();
                html.append("<tr><td colspan=\"2\"><strong>Breakdown:</strong></td></tr>\n");

                if (breakdown.getCleanliness() != null) {
                    html.append("<tr><td>&nbsp;&nbsp;Cleanliness</td><td>").append(breakdown.getCleanliness().getScore()).append("/100</td></tr>\n");
                }
                if (breakdown.getSecurity() != null) {
                    html.append("<tr><td>&nbsp;&nbsp;Security</td><td>").append(breakdown.getSecurity().getScore()).append("/100</td></tr>\n");
                }
                if (breakdown.getMaintainability() != null) {
                    html.append("<tr><td>&nbsp;&nbsp;Maintainability</td><td>").append(breakdown.getMaintainability().getScore()).append("/100</td></tr>\n");
                }
                if (breakdown.getLicenses() != null) {
                    html.append("<tr><td>&nbsp;&nbsp;Licenses</td><td>").append(breakdown.getLicenses().getScore()).append("/100</td></tr>\n");
                }
            }
            html.append("</table>\n");
        }

        if (report.getAnalysis().getSummary() != null && report.getAnalysis().getSummary().getIssues() != null) {
            html.append("<div class=\"section-header\">⚠️ Issues Summary</div>\n");
            var issues = report.getAnalysis().getSummary().getIssues();
            html.append("<table>\n");
            html.append("<tr><th>Issue Type</th><th>Count</th></tr>\n");
            html.append("<tr><td>Unused Dependencies</td><td>").append(issues.getUnused()).append("</td></tr>\n");
            html.append("<tr><td>Undeclared Dependencies</td><td>").append(issues.getUndeclared()).append("</td></tr>\n");
            html.append("</table>\n");
        }

        // Add detailed unused dependencies list
        if (report.getAnalysis().getRawResults() != null && report.getAnalysis().getRawResults().getUnused() != null
            && !report.getAnalysis().getRawResults().getUnused().isEmpty()) {
            html.append("<div class=\"section-header\">🗑️ Unused Dependencies (").append(report.getAnalysis().getRawResults().getUnused().size()).append(")</div>\n");
            html.append("<div class=\"table-container\"><table>\n");
            html.append("<tr><th>Artifact</th><th>Version</th><th>Scope</th><th>Size</th><th>Confidence</th><th>Git Context</th></tr>\n");

            for (var dep : report.getAnalysis().getRawResults().getUnused()) {
                html.append("<tr");
                if (dep.getSuspectedFalsePositive() != null && dep.getSuspectedFalsePositive()) {
                    html.append(" class=\"false-positive-row\"");
                }
                html.append(">\n");

                // Artifact
                html.append("<td><strong>").append(escapeHtml(dep.getArtifactId())).append("</strong>");
                html.append("<br><small style=\"color: #666;\">").append(escapeHtml(dep.getGroupId())).append("</small>");
                if (dep.getSuspectedFalsePositive() != null && dep.getSuspectedFalsePositive()) {
                    html.append("<br><span class=\"badge badge-warn\">⚠️ Suspected False Positive</span>");
                    if (dep.getFalsePositiveReasons() != null && !dep.getFalsePositiveReasons().isEmpty()) {
                        html.append("<br><small>").append(escapeHtml(String.join(", ", dep.getFalsePositiveReasons()))).append("</small>");
                    }
                }
                html.append("</td>\n");

                // Version
                html.append("<td><code>").append(escapeHtml(dep.getVersion())).append("</code></td>\n");

                // Scope
                html.append("<td><span class=\"badge badge-scope\">").append(escapeHtml(dep.getScope())).append("</span></td>\n");

                // Size
                if (dep.getMetadata() != null && dep.getMetadata().getSizeKB() != null) {
                    html.append("<td>").append(String.format("%.2f", dep.getMetadata().getSizeKB())).append(" KB</td>\n");
                } else {
                    html.append("<td>-</td>\n");
                }

                // Confidence
                if (dep.getConfidence() != null) {
                    String confidenceClass = dep.getConfidence() > 0.7 ? "badge-error" : (dep.getConfidence() > 0.4 ? "badge-warn" : "badge-success");
                    String confidenceLabel = dep.getConfidence() > 0.7 ? "HIGH" : (dep.getConfidence() > 0.4 ? "MEDIUM" : "LOW");
                    html.append("<td><span class=\"badge ").append(confidenceClass).append("\">").append(confidenceLabel).append("</span></td>\n");
                } else {
                    html.append("<td>-</td>\n");
                }

                // Git Context
                if (dep.getGit() != null) {
                    html.append("<td><small>");
                    html.append("👤 ").append(escapeHtml(dep.getGit().getAuthorName()));
                    html.append("<br>📅 ").append(dep.getGit().getDaysAgo()).append(" days ago");
                    if (dep.getGit().getCommitMessage() != null && !dep.getGit().getCommitMessage().isEmpty()) {
                        String shortMsg = dep.getGit().getCommitMessage().length() > 50
                            ? dep.getGit().getCommitMessage().substring(0, 50) + "..."
                            : dep.getGit().getCommitMessage();
                        html.append("<br>💬 ").append(escapeHtml(shortMsg.trim()));
                    }
                    html.append("</small></td>\n");
                } else {
                    html.append("<td>-</td>\n");
                }

                html.append("</tr>\n");
            }

            html.append("</table></div>\n");
        }

        // Add undeclared dependencies list
        if (report.getAnalysis().getRawResults() != null && report.getAnalysis().getRawResults().getUndeclared() != null
            && !report.getAnalysis().getRawResults().getUndeclared().isEmpty()) {
            html.append("<div class=\"section-header\">📦 Undeclared Dependencies (").append(report.getAnalysis().getRawResults().getUndeclared().size()).append(")</div>\n");
            html.append("<div class=\"table-container\"><table>\n");
            html.append("<tr><th>Artifact</th><th>Version</th><th>Scope</th><th>Recommendation</th></tr>\n");

            for (var dep : report.getAnalysis().getRawResults().getUndeclared()) {
                html.append("<tr>\n");
                html.append("<td><strong>").append(escapeHtml(dep.getArtifactId())).append("</strong>");
                html.append("<br><small style=\"color: #666;\">").append(escapeHtml(dep.getGroupId())).append("</small></td>\n");
                html.append("<td><code>").append(escapeHtml(dep.getVersion())).append("</code></td>\n");
                html.append("<td><span class=\"badge badge-scope\">").append(escapeHtml(dep.getScope())).append("</span></td>\n");
                html.append("<td><small>Add to pom.xml dependencies section</small></td>\n");
                html.append("</tr>\n");
            }

            html.append("</table></div>\n");
        }

        // Add version conflicts if available
        if (report.getAnalysis().getVersionConflicts() != null && !report.getAnalysis().getVersionConflicts().isEmpty()) {
            html.append("<div class=\"section-header\">⚠️ Version Conflicts (").append(report.getAnalysis().getVersionConflicts().size()).append(")</div>\n");
            html.append("<div class=\"table-container\"><table>\n");
            html.append("<tr><th>Artifact</th><th>Versions</th><th>Risk</th><th>Recommendation</th></tr>\n");

            for (var conflict : report.getAnalysis().getVersionConflicts()) {
                html.append("<tr>\n");
                html.append("<td><strong>").append(escapeHtml(conflict.getArtifactId())).append("</strong>");
                html.append("<br><small style=\"color: #666;\">").append(escapeHtml(conflict.getGroupId())).append("</small></td>\n");
                html.append("<td><small>");
                if (conflict.getVersions() != null) {
                    html.append(escapeHtml(String.join(", ", conflict.getVersions())));
                }
                html.append("</small></td>\n");
                String riskClass = "warn";
                String riskLabel = "MEDIUM";
                if (conflict.getRiskLevel() != null) {
                    riskClass = conflict.getRiskLevel().toString().toLowerCase();
                    riskLabel = conflict.getRiskLevel().toString();
                }
                html.append("<td><span class=\"badge badge-").append(riskClass).append("\">")
                    .append(escapeHtml(riskLabel)).append("</span></td>\n");
                html.append("<td><small>Align versions using dependencyManagement</small></td>\n");
                html.append("</tr>\n");
            }

            html.append("</table></div>\n");
        }

        html.append("</div>\n");
    }

    private void writeHtmlAvailableUpdatesTab(StringBuilder html, DependencyReport report) {
        if (report.getAnalysis() == null || report.getAnalysis().getRawResults() == null) return;

        html.append("<div id=\"updates\" class=\"tab-content\">\n");
        html.append("<div class=\"section-header\">🔄 Available Dependency Updates</div>\n");

        // Collect all dependencies with available versions
        var allDependencies = new java.util.ArrayList<io.github.tourem.maven.descriptor.model.analysis.AnalyzedDependency>();

        if (report.getAnalysis().getRawResults().getUnused() != null) {
            allDependencies.addAll(report.getAnalysis().getRawResults().getUnused());
        }
        if (report.getAnalysis().getRawResults().getUndeclared() != null) {
            allDependencies.addAll(report.getAnalysis().getRawResults().getUndeclared());
        }

        // Filter dependencies with available versions
        var depsWithUpdates = allDependencies.stream()
            .filter(dep -> dep.getAvailableVersions() != null && !dep.getAvailableVersions().isEmpty())
            .sorted((a, b) -> {
                String keyA = a.getGroupId() + ":" + a.getArtifactId();
                String keyB = b.getGroupId() + ":" + b.getArtifactId();
                return keyA.compareTo(keyB);
            })
            .collect(java.util.stream.Collectors.toList());

        if (depsWithUpdates.isEmpty()) {
            html.append("<div class=\"update-alert\">ℹ️ No version information available. Run with <code>-Ddescriptor.lookupAvailableVersions=true</code> to fetch available versions.</div>\n");
        } else {
            html.append("<p>📋 <strong>").append(depsWithUpdates.size()).append("</strong> dependencies with available updates</p>\n");

            html.append("<div class=\"table-container\"><table>\n");
            html.append("<tr><th>Dependency</th><th>Current Version</th><th>Available Versions</th><th>Latest Version</th><th>Status</th></tr>\n");

            for (var dep : depsWithUpdates) {
                String currentVersion = dep.getVersion();
                var availableVersions = dep.getAvailableVersions();
                String latestVersion = availableVersions.isEmpty() ? currentVersion : availableVersions.get(availableVersions.size() - 1);

                // Determine if update is critical (major version difference)
                boolean isCritical = isMajorVersionDifference(currentVersion, latestVersion);

                html.append("<tr>");

                // Dependency name
                html.append("<td><strong>").append(escapeHtml(dep.getArtifactId())).append("</strong><br>");
                html.append("<small style=\"color: #666;\">").append(escapeHtml(dep.getGroupId())).append("</small></td>");

                // Current version
                html.append("<td><span class=\"").append(isCritical ? "version-outdated" : "version-current").append("\">")
                    .append(escapeHtml(currentVersion)).append("</span></td>");

                // Available versions (max 3)
                html.append("<td>");
                int count = 0;
                for (String version : availableVersions) {
                    if (count >= 3) break;
                    if (count > 0) html.append("<br>");
                    html.append("<span class=\"version-available\">").append(escapeHtml(version)).append("</span>");
                    count++;
                }
                if (availableVersions.size() > 3) {
                    html.append("<br><small style=\"color: #666;\">... and ").append(availableVersions.size() - 3).append(" more</small>");
                }
                html.append("</td>");

                // Latest version
                html.append("<td><span class=\"version-latest\">").append(escapeHtml(latestVersion)).append("</span></td>");

                // Status
                html.append("<td>");
                if (isCritical) {
                    html.append("<span class=\"badge badge-error\">⚠️ Major Update</span>");
                } else if (!currentVersion.equals(latestVersion)) {
                    html.append("<span class=\"badge badge-warn\">Update Available</span>");
                } else {
                    html.append("<span class=\"badge badge-ok\">Up to Date</span>");
                }
                html.append("</td>");

                html.append("</tr>\n");
            }

            html.append("</table></div>\n");

            // Summary
            long criticalUpdates = depsWithUpdates.stream()
                .filter(dep -> isMajorVersionDifference(dep.getVersion(),
                    dep.getAvailableVersions().isEmpty() ? dep.getVersion() :
                    dep.getAvailableVersions().get(dep.getAvailableVersions().size() - 1)))
                .count();

            if (criticalUpdates > 0) {
                html.append("<div class=\"update-critical\">");
                html.append("⚠️ <strong>").append(criticalUpdates).append("</strong> dependencies have major version updates available. ");
                html.append("Review these updates carefully as they may contain breaking changes.");
                html.append("</div>\n");
            }
        }

        html.append("</div>\n");
    }

    private boolean isMajorVersionDifference(String currentVersion, String latestVersion) {
        if (currentVersion == null || latestVersion == null) return false;
        if (currentVersion.equals(latestVersion)) return false;

        try {
            // Extract major version numbers
            String currentMajor = currentVersion.split("[.-]")[0];
            String latestMajor = latestVersion.split("[.-]")[0];

            int current = Integer.parseInt(currentMajor);
            int latest = Integer.parseInt(latestMajor);

            // Consider it critical if major version differs by 1 or more
            return (latest - current) >= 1;
        } catch (Exception e) {
            // If we can't parse versions, assume it's not critical
            return false;
        }
    }

    private void writeHtmlPluginsTab(StringBuilder html, DependencyReport report) {
        if (report.getPlugins() == null) return;

        html.append("<div id=\"plugins\" class=\"tab-content\">\n");
        html.append("<div class=\"section-header\">🔌 Maven Plugins</div>\n");

        if (report.getPlugins().getSummary() != null) {
            var summary = report.getPlugins().getSummary();
            html.append("<table>\n");
            html.append("<tr><th>Metric</th><th>Value</th></tr>\n");
            html.append("<tr><td>Total Plugins</td><td><strong>").append(summary.getTotalPlugins()).append("</strong></td></tr>\n");
            html.append("<tr><td>Build Plugins</td><td><strong>").append(summary.getBuildPlugins()).append("</strong></td></tr>\n");
            html.append("<tr><td>Plugin Management</td><td><strong>").append(summary.getManagementPlugins()).append("</strong></td></tr>\n");
            html.append("</table>\n");
        }

        if (report.getPlugins().getBuild() != null && !report.getPlugins().getBuild().isEmpty()) {
            html.append("<div class=\"section-header\">🔨 Build Plugins</div>\n");
            html.append("<div class=\"table-container\"><table>\n");
            html.append("<tr><th>Group ID</th><th>Artifact ID</th><th>Version</th></tr>\n");
            report.getPlugins().getBuild().forEach(plugin -> {
                html.append("<tr>");
                html.append("<td>").append(escapeHtml(plugin.getGroupId())).append("</td>");
                html.append("<td><strong>").append(escapeHtml(plugin.getArtifactId())).append("</strong></td>");
                html.append("<td><code>").append(escapeHtml(plugin.getVersion())).append("</code></td>");
                html.append("</tr>\n");
            });
            html.append("</table></div>\n");
        }

        html.append("</div>\n");
    }

    private String escapeHtml(String text) {
        if (text == null) return "";
        return text.replace("&", "&amp;")
                   .replace("<", "&lt;")
                   .replace(">", "&gt;")
                   .replace("\"", "&quot;")
                   .replace("'", "&#39;");
    }

    private String getOutputPath(String extension) {
        File dir = outputDir != null ? outputDir : getTargetDir();
        return new File(dir, outputFile + "." + extension).getAbsolutePath();
    }

    private File getTargetDir() {
        return new File(project.getBuild().getDirectory());
    }
}
