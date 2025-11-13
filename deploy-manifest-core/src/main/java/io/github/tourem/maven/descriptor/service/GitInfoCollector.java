package io.github.tourem.maven.descriptor.service;

import io.github.tourem.maven.descriptor.model.BuildInfo;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.Status;
import org.eclipse.jgit.internal.storage.file.FileRepository;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.storage.file.FileRepositoryBuilder;

import java.io.File;
import java.net.InetAddress;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Map;

/**
 * Collects Git and CI/CD metadata for build traceability.
 * @author tourem

 */
@Slf4j
public class GitInfoCollector {



    /**
     * Collect all build information including Git and CI/CD metadata.
     *
     * @param projectPath Path to the project root
     * @return BuildInfo with all available metadata
     */
    public BuildInfo collectBuildInfo(Path projectPath) {
        BuildInfo.BuildInfoBuilder builder = BuildInfo.builder();

        // Build timestamp and host info
        builder.buildTimestamp(LocalDateTime.now());
        builder.buildHost(getHostname());
        builder.buildUser(System.getProperty("user.name"));

        // Git metadata
        collectGitInfo(projectPath, builder);

        // CI/CD metadata
        collectCiInfo(builder);

        return builder.build();
    }

    /**
     * Collect Git metadata using JGit.
     */
    private void collectGitInfo(Path projectPath, BuildInfo.BuildInfoBuilder builder) {
        try {
            File gitDir = findGitDirectory(projectPath.toFile());
            if (gitDir == null) {
                log.debug("No Git repository found at {}", projectPath);
                return;
            }

            FileRepositoryBuilder repositoryBuilder = new FileRepositoryBuilder();
            try (Repository repository = repositoryBuilder.setGitDir(gitDir)
                    .readEnvironment()
                    .findGitDir()
                    .build()) {

                // Get HEAD commit
                ObjectId head = repository.resolve(Constants.HEAD);
                if (head != null) {
                    String commitSha = head.getName();
                    builder.gitCommitSha(commitSha);
                    builder.gitCommitShortSha(commitSha.substring(0, 7));

                    // Get commit details
                    try (RevWalk revWalk = new RevWalk(repository)) {
                        RevCommit commit = revWalk.parseCommit(head);
                        builder.gitCommitMessage(commit.getShortMessage());
                        builder.gitCommitAuthor(commit.getAuthorIdent().getName());

                        Instant commitInstant = Instant.ofEpochSecond(commit.getCommitTime());
                        builder.gitCommitTime(LocalDateTime.ofInstant(commitInstant, ZoneId.systemDefault()));

                        revWalk.dispose();
                    }
                }

                // Get branch name
                String branch = repository.getBranch();
                builder.gitBranch(branch);

                // Get tag if on a tagged commit
                String tag = getTag(repository, head);
                if (tag != null) {
                    builder.gitTag(tag);
                }

                // Check if working directory is dirty
                try (Git git = new Git(repository)) {
                    Status status = git.status().call();
                    boolean isDirty = !status.isClean();
                    builder.gitDirty(isDirty);
                }

                // Get remote URL
                String remoteUrl = repository.getConfig().getString("remote", "origin", "url");
                builder.gitRemoteUrl(remoteUrl);
            }

        } catch (Exception e) {
            log.warn("Failed to collect Git metadata: {}", e.getMessage());
        }
    }

    /**
     * Find .git directory starting from project path.
     */
    private File findGitDirectory(File dir) {
        File current = dir;
        while (current != null) {
            File gitDir = new File(current, ".git");
            if (gitDir.exists() && gitDir.isDirectory()) {
                return gitDir;
            }
            current = current.getParentFile();
        }
        return null;
    }

    /**
     * Get tag name if HEAD is on a tagged commit.
     */
    private String getTag(Repository repository, ObjectId head) {
        try {
            Map<String, org.eclipse.jgit.lib.Ref> tags = repository.getTags();
            for (Map.Entry<String, org.eclipse.jgit.lib.Ref> entry : tags.entrySet()) {
                ObjectId tagId = repository.peel(entry.getValue()).getPeeledObjectId();
                if (tagId == null) {
                    tagId = entry.getValue().getObjectId();
                }
                if (tagId != null && tagId.equals(head)) {
                    return entry.getKey();
                }
            }
        } catch (Exception e) {
            log.debug("Failed to get tag: {}", e.getMessage());
        }
        return null;
    }

    /**
     * Collect CI/CD environment metadata.
     */
    private void collectCiInfo(BuildInfo.BuildInfoBuilder builder) {
        Map<String, String> env = System.getenv();

        // GitHub Actions
        if (env.containsKey("GITHUB_ACTIONS")) {
            builder.ciProvider("GitHub Actions");
            builder.ciBuildId(env.get("GITHUB_RUN_ID"));
            builder.ciBuildNumber(env.get("GITHUB_RUN_NUMBER"));
            builder.ciJobName(env.get("GITHUB_WORKFLOW"));
            builder.ciActor(env.get("GITHUB_ACTOR"));
            builder.ciEventName(env.get("GITHUB_EVENT_NAME"));

            String repo = env.get("GITHUB_REPOSITORY");
            String runId = env.get("GITHUB_RUN_ID");
            if (repo != null && runId != null) {
                builder.ciBuildUrl("https://github.com/" + repo + "/actions/runs/" + runId);
            }
        }
        // GitLab CI
        else if (env.containsKey("GITLAB_CI")) {
            builder.ciProvider("GitLab CI");
            builder.ciBuildId(env.get("CI_PIPELINE_ID"));
            builder.ciBuildNumber(env.get("CI_PIPELINE_IID"));
            builder.ciJobName(env.get("CI_JOB_NAME"));
            builder.ciActor(env.get("GITLAB_USER_LOGIN"));
            builder.ciBuildUrl(env.get("CI_PIPELINE_URL"));
        }
        // Jenkins
        else if (env.containsKey("JENKINS_HOME")) {
            builder.ciProvider("Jenkins");
            builder.ciBuildId(env.get("BUILD_ID"));
            builder.ciBuildNumber(env.get("BUILD_NUMBER"));
            builder.ciJobName(env.get("JOB_NAME"));
            builder.ciBuildUrl(env.get("BUILD_URL"));
        }
        // Travis CI
        else if (env.containsKey("TRAVIS")) {
            builder.ciProvider("Travis CI");
            builder.ciBuildId(env.get("TRAVIS_BUILD_ID"));
            builder.ciBuildNumber(env.get("TRAVIS_BUILD_NUMBER"));
            builder.ciJobName(env.get("TRAVIS_JOB_NAME"));
            builder.ciBuildUrl(env.get("TRAVIS_BUILD_WEB_URL"));
        }
        // CircleCI
        else if (env.containsKey("CIRCLECI")) {
            builder.ciProvider("CircleCI");
            builder.ciBuildId(env.get("CIRCLE_WORKFLOW_ID"));
            builder.ciBuildNumber(env.get("CIRCLE_BUILD_NUM"));
            builder.ciJobName(env.get("CIRCLE_JOB"));
            builder.ciActor(env.get("CIRCLE_USERNAME"));
            builder.ciBuildUrl(env.get("CIRCLE_BUILD_URL"));
        }
        // Azure Pipelines
        else if (env.containsKey("TF_BUILD")) {
            builder.ciProvider("Azure Pipelines");
            builder.ciBuildId(env.get("BUILD_BUILDID"));
            builder.ciBuildNumber(env.get("BUILD_BUILDNUMBER"));
            builder.ciJobName(env.get("BUILD_DEFINITIONNAME"));
            builder.ciActor(env.get("BUILD_REQUESTEDFOR"));
        }
    }

    /**
     * Get hostname safely.
     */
    private String getHostname() {
        try {
            return InetAddress.getLocalHost().getHostName();
        } catch (Exception e) {
            return "unknown";
        }
    }
}

