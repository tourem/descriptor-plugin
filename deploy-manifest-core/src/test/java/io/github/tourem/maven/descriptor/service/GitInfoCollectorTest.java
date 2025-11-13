package io.github.tourem.maven.descriptor.service;

import io.github.tourem.maven.descriptor.model.BuildInfo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for GitInfoCollector.
 */
class GitInfoCollectorTest {

    private GitInfoCollector gitInfoCollector;

    @BeforeEach
    void setUp() {
        gitInfoCollector = new GitInfoCollector();
    }

    @Test
    void shouldCollectBuildInfoWithoutGitRepository(@TempDir Path tempDir) {
        // Given: A directory that is not a Git repository
        
        // When: Collecting build info
        BuildInfo buildInfo = gitInfoCollector.collectBuildInfo(tempDir);
        
        // Then: Should return build info with null Git fields but valid build metadata
        assertThat(buildInfo).isNotNull();
        assertThat(buildInfo.gitCommitSha()).isNull();
        assertThat(buildInfo.gitCommitShortSha()).isNull();
        assertThat(buildInfo.gitBranch()).isNull();
        assertThat(buildInfo.gitTag()).isNull();
        assertThat(buildInfo.gitDirty()).isNull();
        assertThat(buildInfo.gitRemoteUrl()).isNull();
        assertThat(buildInfo.gitCommitMessage()).isNull();
        assertThat(buildInfo.gitCommitAuthor()).isNull();
        assertThat(buildInfo.gitCommitTime()).isNull();
        
        // Build metadata should still be present
        assertThat(buildInfo.buildTimestamp()).isNotNull();
        assertThat(buildInfo.buildHost()).isNotNull();
        assertThat(buildInfo.buildUser()).isNotNull();
    }

    @Test
    void shouldCollectBuildMetadata(@TempDir Path tempDir) {
        // When: Collecting build info
        BuildInfo buildInfo = gitInfoCollector.collectBuildInfo(tempDir);
        
        // Then: Build metadata should be present
        assertThat(buildInfo.buildTimestamp()).isNotNull();
        assertThat(buildInfo.buildHost()).isNotNull().isNotEmpty();
        assertThat(buildInfo.buildUser()).isNotNull().isNotEmpty();
    }

    @Test
    void shouldDetectCIEnvironmentIfPresent() {
        // When: Collecting build info
        BuildInfo buildInfo = gitInfoCollector.collectBuildInfo(Path.of("."));

        // Then: CI provider may or may not be detected depending on actual environment
        // This test just verifies the method doesn't crash and returns valid data
        assertThat(buildInfo).isNotNull();

        // CI provider could be null (local environment) or a valid provider name (CI environment)
        if (buildInfo.ciProvider() != null) {
            assertThat(buildInfo.ciProvider()).isNotEmpty();
            // If CI provider is detected, at least some CI fields should be present
            assertThat(buildInfo.ciBuildId()).isNotNull();
        } else {
            // If not in CI, all CI fields should be null
            assertThat(buildInfo.ciBuildId()).isNull();
            assertThat(buildInfo.ciBuildNumber()).isNull();
            assertThat(buildInfo.ciBuildUrl()).isNull();
            assertThat(buildInfo.ciJobName()).isNull();
            assertThat(buildInfo.ciActor()).isNull();
            assertThat(buildInfo.ciEventName()).isNull();
        }
    }
}

