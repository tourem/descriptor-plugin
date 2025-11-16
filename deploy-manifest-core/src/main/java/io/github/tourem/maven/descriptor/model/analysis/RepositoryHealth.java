package io.github.tourem.maven.descriptor.model.analysis;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.List;

/**
 * Health information about a dependency's repository (GitHub, GitLab, etc.).
 * Helps assess if a dependency is actively maintained and safe to use.
 * 
 * @author tourem
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class RepositoryHealth {
    
    public enum HealthLevel {
        HEALTHY,    // Green: actively maintained, many contributors
        WARNING,    // Yellow: some concerns (old release, few contributors)
        DANGER,     // Red: serious concerns (very old, abandoned, 1-2 contributors)
        UNKNOWN     // Gray: unable to determine
    }
    
    // Overall health assessment
    private HealthLevel level;
    private List<String> concerns;  // List of issues found
    private List<String> positives; // List of good signs
    
    // Repository information
    private String repositoryUrl;
    private String repositoryType; // github, gitlab, bitbucket, etc.
    
    // Release information
    private Instant lastReleaseDate;
    private String lastReleaseVersion;
    private Long daysSinceLastRelease;
    
    // Activity metrics
    private Integer contributorCount;
    private Integer starCount;
    private Integer forkCount;
    private Integer openIssueCount;
    
    // Commit activity
    private Instant lastCommitDate;
    private Long daysSinceLastCommit;
    
    // Maintenance indicators
    private Boolean archived;
    private Boolean deprecated;
    private String license;
    
    // Maven Central specific
    private Integer totalVersions;
    private String latestVersion;
    
    /**
     * Helper to determine if this dependency should raise concerns.
     */
    public boolean hasWarnings() {
        return level == HealthLevel.WARNING || level == HealthLevel.DANGER;
    }
    
    /**
     * Helper to determine if this is a critical issue.
     */
    public boolean isDangerous() {
        return level == HealthLevel.DANGER;
    }
}

