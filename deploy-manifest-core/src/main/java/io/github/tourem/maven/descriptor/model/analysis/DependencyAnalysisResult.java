package io.github.tourem.maven.descriptor.model.analysis;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class DependencyAnalysisResult {

    @Builder.Default
    private String analyzer = "deploy-manifest-plugin";

    @Builder.Default
    private String baseAnalyzer = "maven-dependency-analyzer";

    @Builder.Default
    private Instant timestamp = Instant.now();

    private RawResults rawResults;
    private Summary summary;

    // Phase 3
    private HealthScore healthScore;

    // Phase 2 enrichments
    private java.util.List<Recommendation> recommendations;
    private java.util.List<VersionConflict> versionConflicts;
    private MultiModuleAnalysis multiModule;

    // Plugin information
    private io.github.tourem.maven.descriptor.model.PluginInfo plugins;

    // Dependency tree information
    private io.github.tourem.maven.descriptor.model.DependencyTreeInfo dependencyTree;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class RawResults {
        private List<AnalyzedDependency> unused;       // declared but not used
        private List<AnalyzedDependency> undeclared;   // used but not declared
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class Summary {
        private Integer totalDependencies;
        private Integer directDependencies;
        private Integer transitiveDependencies;
        private Issues issues;
        private PotentialSavings potentialSavings;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class Issues {
        private Integer unused;
        private Integer undeclared;
        private Integer totalIssues;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class PotentialSavings {
        private Long bytes;
        private Double kb;
        private Double mb;
        private Double percentage; // optional if unknown
    }
}

