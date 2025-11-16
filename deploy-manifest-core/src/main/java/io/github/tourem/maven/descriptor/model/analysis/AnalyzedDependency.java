package io.github.tourem.maven.descriptor.model.analysis;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class AnalyzedDependency {
    private String groupId;
    private String artifactId;
    private String version;
    private String scope;

    private GitInfo git; // present when dependency is declared in pom and git blame is enabled

    private Boolean suspectedFalsePositive;
    private java.util.List<String> falsePositiveReasons;
    private Double confidence; // 0..1 confidence the issue is real

    @Builder.Default
    private Metadata metadata = null;

    // Available versions (max 3 newer versions after current version)
    private java.util.List<String> availableVersions;

    // Repository health information (maintenance status, contributors, etc.)
    private RepositoryHealth repositoryHealth;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class Metadata {
        private Long sizeBytes;
        private Double sizeKB;
        private Double sizeMB;
        private String fileLocation;
        private String sha256;
        private String packaging;
    }
}

