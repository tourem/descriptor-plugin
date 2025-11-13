package io.github.tourem.maven.descriptor.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;

import java.time.LocalDateTime;

/**
 * Build and Git metadata for traceability and reproducibility.
 *
 * @param gitCommitSha Full Git commit SHA
 * @param gitCommitShortSha Short Git commit SHA (7 chars)
 * @param gitBranch Current Git branch name
 * @param gitTag Git tag if on a tagged commit
 * @param gitDirty Whether the working directory has uncommitted changes
 * @param gitRemoteUrl Git remote origin URL
 * @param gitCommitMessage Last commit message
 * @param gitCommitAuthor Last commit author
 * @param gitCommitTime Last commit timestamp
 * @param ciProvider CI/CD provider (GitHub Actions, GitLab CI, Jenkins, etc.)
 * @param ciBuildId Build ID from CI system
 * @param ciBuildNumber Build number from CI system
 * @param ciBuildUrl URL to the CI build
 * @param ciJobName CI job/workflow name
 * @param ciActor User who triggered the CI build
 * @param ciEventName Event that triggered the build (push, pull_request, etc.)
 * @param buildTimestamp When the descriptor was built
 * @param buildHost Hostname where the build was executed
 * @param buildUser User who executed the build
 * @author tourem

 */
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public record BuildInfo(
    String gitCommitSha,
    String gitCommitShortSha,
    String gitBranch,
    String gitTag,
    Boolean gitDirty,
    String gitRemoteUrl,
    String gitCommitMessage,
    String gitCommitAuthor,
    LocalDateTime gitCommitTime,
    String ciProvider,
    String ciBuildId,
    String ciBuildNumber,
    String ciBuildUrl,
    String ciJobName,
    String ciActor,
    String ciEventName,
    LocalDateTime buildTimestamp,
    String buildHost,
    String buildUser
) {}

