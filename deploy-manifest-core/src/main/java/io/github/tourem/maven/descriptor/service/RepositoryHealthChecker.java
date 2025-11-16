package io.github.tourem.maven.descriptor.service;

import io.github.tourem.maven.descriptor.model.analysis.RepositoryHealth;
import lombok.extern.slf4j.Slf4j;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.ByteArrayInputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

/**
 * Service to check the health of dependency repositories.
 * Analyzes Maven Central metadata and GitHub repository information.
 *
 * @author tourem
 */
@Slf4j
public class RepositoryHealthChecker {

    private static final int DEFAULT_TIMEOUT_MS = 5000;
    private static final String MAVEN_CENTRAL = "https://repo1.maven.org/maven2";
    private static final String GITHUB_API = "https://api.github.com";

    // Thresholds for health assessment
    private static final long DAYS_WARNING_THRESHOLD = 730;  // 2 years
    private static final long DAYS_DANGER_THRESHOLD = 1095;  // 3 years
    private static final int CONTRIBUTORS_WARNING_THRESHOLD = 3;
    private static final int CONTRIBUTORS_DANGER_THRESHOLD = 2;

    private final int timeoutMs;
    private final String githubToken; // Optional GitHub token for higher rate limits

    public RepositoryHealthChecker(int timeoutMs, String githubToken) {
        this.timeoutMs = timeoutMs > 0 ? timeoutMs : DEFAULT_TIMEOUT_MS;
        this.githubToken = githubToken;
    }

    public RepositoryHealthChecker(int timeoutMs) {
        this(timeoutMs, null);
    }

    /**
     * Check the health of a dependency's repository.
     *
     * @param groupId Maven groupId
     * @param artifactId Maven artifactId
     * @param currentVersion Current version
     * @return RepositoryHealth information, or null if unable to determine
     */
    public RepositoryHealth checkHealth(String groupId, String artifactId, String currentVersion) {
        if (groupId == null || artifactId == null) {
            return null;
        }

        try {
            RepositoryHealth.RepositoryHealthBuilder builder = RepositoryHealth.builder();
            List<String> concerns = new ArrayList<>();
            List<String> positives = new ArrayList<>();

            // Step 1: Get Maven Central metadata
            MavenMetadata mavenMeta = fetchMavenMetadata(groupId, artifactId);
            if (mavenMeta != null) {
                builder.totalVersions(mavenMeta.totalVersions);
                builder.latestVersion(mavenMeta.latestVersion);
                builder.lastReleaseDate(mavenMeta.lastReleaseDate);

                if (mavenMeta.lastReleaseDate != null) {
                    long daysSince = ChronoUnit.DAYS.between(mavenMeta.lastReleaseDate, Instant.now());
                    builder.daysSinceLastRelease(daysSince);

                    if (daysSince > DAYS_DANGER_THRESHOLD) {
                        concerns.add(String.format("Last release was %d days ago (>3 years)", daysSince));
                    } else if (daysSince > DAYS_WARNING_THRESHOLD) {
                        concerns.add(String.format("Last release was %d days ago (>2 years)", daysSince));
                    } else if (daysSince < 180) {
                        positives.add(String.format("Recently updated (%d days ago)", daysSince));
                    }
                }

                // Try to get POM to find SCM URL
                String scmUrl = fetchScmUrl(groupId, artifactId, mavenMeta.latestVersion);
                if (scmUrl != null) {
                    // Clean up URL before storing
                    String cleanedUrl = scmUrl.replaceAll("github\\.com:", "github.com/");
                    builder.repositoryUrl(cleanedUrl);

                    // If it's a GitHub repository, fetch additional info
                    if (scmUrl.contains("github.com")) {
                        builder.repositoryType("github");
                        GitHubInfo githubInfo = fetchGitHubInfo(scmUrl);
                        if (githubInfo != null) {
                            enrichWithGitHubInfo(builder, githubInfo, concerns, positives);
                        }
                    }
                }
            }

            // Determine overall health level
            RepositoryHealth.HealthLevel level = determineHealthLevel(concerns, positives, mavenMeta);
            builder.level(level);
            builder.concerns(concerns.isEmpty() ? null : concerns);
            builder.positives(positives.isEmpty() ? null : positives);

            return builder.build();

        } catch (Exception e) {
            log.debug("Failed to check repository health for {}:{}: {}", groupId, artifactId, e.getMessage());
            return RepositoryHealth.builder()
                    .level(RepositoryHealth.HealthLevel.UNKNOWN)
                    .build();
        }
    }

    private void enrichWithGitHubInfo(RepositoryHealth.RepositoryHealthBuilder builder,
                                       GitHubInfo githubInfo,
                                       List<String> concerns,
                                       List<String> positives) {
        builder.contributorCount(githubInfo.contributorCount);
        builder.starCount(githubInfo.starCount);
        builder.forkCount(githubInfo.forkCount);
        builder.openIssueCount(githubInfo.openIssueCount);
        builder.archived(githubInfo.archived);
        builder.license(githubInfo.license);
        builder.lastCommitDate(githubInfo.lastCommitDate);

        if (githubInfo.lastCommitDate != null) {
            long daysSince = ChronoUnit.DAYS.between(githubInfo.lastCommitDate, Instant.now());
            builder.daysSinceLastCommit(daysSince);
        }

        // Assess contributors
        if (githubInfo.contributorCount != null) {
            if (githubInfo.contributorCount <= CONTRIBUTORS_DANGER_THRESHOLD) {
                concerns.add(String.format("Only %d contributor(s) - high bus factor risk", githubInfo.contributorCount));
            } else if (githubInfo.contributorCount <= CONTRIBUTORS_WARNING_THRESHOLD) {
                concerns.add(String.format("Only %d contributors - limited maintenance team", githubInfo.contributorCount));
            } else if (githubInfo.contributorCount >= 10) {
                positives.add(String.format("%d contributors - healthy community", githubInfo.contributorCount));
            }
        }

        // Check if archived
        if (Boolean.TRUE.equals(githubInfo.archived)) {
            concerns.add("Repository is archived - no longer maintained");
        }

        // Check stars/forks as popularity indicators
        if (githubInfo.starCount != null && githubInfo.starCount > 1000) {
            positives.add(String.format("Popular project (%d stars)", githubInfo.starCount));
        }
    }

    private RepositoryHealth.HealthLevel determineHealthLevel(List<String> concerns,
                                                                List<String> positives,
                                                                MavenMetadata mavenMeta) {
        // DANGER conditions
        if (concerns.stream().anyMatch(c -> c.contains("archived"))) {
            return RepositoryHealth.HealthLevel.DANGER;
        }
        if (concerns.stream().anyMatch(c -> c.contains(">3 years"))) {
            return RepositoryHealth.HealthLevel.DANGER;
        }
        if (concerns.stream().anyMatch(c -> c.contains("bus factor risk"))) {
            return RepositoryHealth.HealthLevel.DANGER;
        }

        // WARNING conditions
        if (concerns.stream().anyMatch(c -> c.contains(">2 years"))) {
            return RepositoryHealth.HealthLevel.WARNING;
        }
        if (concerns.stream().anyMatch(c -> c.contains("limited maintenance"))) {
            return RepositoryHealth.HealthLevel.WARNING;
        }

        // HEALTHY if we have positives and no concerns
        if (!concerns.isEmpty()) {
            return RepositoryHealth.HealthLevel.WARNING;
        }

        if (!positives.isEmpty()) {
            return RepositoryHealth.HealthLevel.HEALTHY;
        }

        // Default to UNKNOWN if we couldn't determine
        return RepositoryHealth.HealthLevel.UNKNOWN;
    }

    private MavenMetadata fetchMavenMetadata(String groupId, String artifactId) {
        try {
            String path = groupId.replace('.', '/') + "/" + artifactId + "/maven-metadata.xml";
            String url = MAVEN_CENTRAL + "/" + path;

            HttpClient client = HttpClient.newBuilder()
                    .connectTimeout(Duration.ofMillis(timeoutMs))
                    .build();

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofMillis(timeoutMs))
                    .header("User-Agent", "deploy-manifest-plugin/2.5.0")
                    .GET()
                    .build();

            HttpResponse<byte[]> response = client.send(request, HttpResponse.BodyHandlers.ofByteArray());

            if (response.statusCode() >= 200 && response.statusCode() < 300) {
                return parseMavenMetadata(response.body());
            }
        } catch (Exception e) {
            log.debug("Failed to fetch Maven metadata for {}:{}: {}", groupId, artifactId, e.getMessage());
        }
        return null;
    }

    private MavenMetadata parseMavenMetadata(byte[] xmlBytes) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
        factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);

        DocumentBuilder docBuilder = factory.newDocumentBuilder();
        Document doc = docBuilder.parse(new ByteArrayInputStream(xmlBytes));

        MavenMetadata meta = new MavenMetadata();

        // Get latest version
        NodeList latestNodes = doc.getElementsByTagName("latest");
        if (latestNodes.getLength() > 0) {
            meta.latestVersion = latestNodes.item(0).getTextContent().trim();
        } else {
            // Fallback to release
            NodeList releaseNodes = doc.getElementsByTagName("release");
            if (releaseNodes.getLength() > 0) {
                meta.latestVersion = releaseNodes.item(0).getTextContent().trim();
            }
        }

        // Get all versions
        NodeList versionNodes = doc.getElementsByTagName("version");
        meta.totalVersions = versionNodes.getLength();

        // Get lastUpdated timestamp
        NodeList lastUpdatedNodes = doc.getElementsByTagName("lastUpdated");
        if (lastUpdatedNodes.getLength() > 0) {
            String lastUpdated = lastUpdatedNodes.item(0).getTextContent().trim();
            meta.lastReleaseDate = parseLastUpdated(lastUpdated);
        }

        return meta;
    }

    private Instant parseLastUpdated(String lastUpdated) {
        try {
            // Format: yyyyMMddHHmmss
            if (lastUpdated.length() >= 14) {
                DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");
                LocalDateTime ldt = LocalDateTime.parse(lastUpdated.substring(0, 14), formatter);
                return ldt.atZone(ZoneId.of("UTC")).toInstant();
            }
        } catch (Exception e) {
            log.debug("Failed to parse lastUpdated: {}", lastUpdated);
        }
        return null;
    }


    private String fetchScmUrl(String groupId, String artifactId, String version) {
        if (version == null) {
            return null;
        }

        try {
            String path = groupId.replace('.', '/') + "/" + artifactId + "/" + version + "/" + artifactId + "-" + version + ".pom";
            String url = MAVEN_CENTRAL + "/" + path;

            HttpClient client = HttpClient.newBuilder()
                    .connectTimeout(Duration.ofMillis(timeoutMs))
                    .build();

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofMillis(timeoutMs))
                    .header("User-Agent", "deploy-manifest-plugin/2.5.0")
                    .GET()
                    .build();

            HttpResponse<byte[]> response = client.send(request, HttpResponse.BodyHandlers.ofByteArray());

            if (response.statusCode() >= 200 && response.statusCode() < 300) {
                return parseScmUrl(response.body());
            }
        } catch (Exception e) {
            log.debug("Failed to fetch POM for {}:{}:{}: {}", groupId, artifactId, version, e.getMessage());
        }
        return null;
    }

    private String parseScmUrl(byte[] pomBytes) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
        factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);

        DocumentBuilder docBuilder = factory.newDocumentBuilder();
        Document doc = docBuilder.parse(new ByteArrayInputStream(pomBytes));

        // Try <scm><url>
        NodeList scmNodes = doc.getElementsByTagName("scm");
        if (scmNodes.getLength() > 0) {
            Element scmElement = (Element) scmNodes.item(0);
            NodeList urlNodes = scmElement.getElementsByTagName("url");
            if (urlNodes.getLength() > 0) {
                String url = urlNodes.item(0).getTextContent().trim();
                // Clean up URL (remove .git, scm:git:, etc.)
                url = url.replaceAll("^scm:git:", "")
                         .replaceAll("^git:", "")
                         .replaceAll("\\.git$", "");
                return url;
            }
        }

        return null;
    }

    private GitHubInfo fetchGitHubInfo(String repoUrl) {
        try {
            // Extract owner/repo from URL
            // Examples: https://github.com/owner/repo or git@github.com:owner/repo
            String ownerRepo = extractGitHubOwnerRepo(repoUrl);
            if (ownerRepo == null) {
                return null;
            }

            String apiUrl = GITHUB_API + "/repos/" + ownerRepo;

            HttpClient client = HttpClient.newBuilder()
                    .connectTimeout(Duration.ofMillis(timeoutMs))
                    .build();

            HttpRequest.Builder requestBuilder = HttpRequest.newBuilder()
                    .uri(URI.create(apiUrl))
                    .timeout(Duration.ofMillis(timeoutMs))
                    .header("User-Agent", "deploy-manifest-plugin/2.5.0")
                    .header("Accept", "application/vnd.github.v3+json");

            if (githubToken != null && !githubToken.isEmpty()) {
                requestBuilder.header("Authorization", "token " + githubToken);
            }

            HttpRequest request = requestBuilder.GET().build();
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() >= 200 && response.statusCode() < 300) {
                return parseGitHubResponse(response.body(), ownerRepo);
            } else {
                log.debug("GitHub API returned {}: {}", response.statusCode(), response.body());
            }
        } catch (Exception e) {
            log.debug("Failed to fetch GitHub info for {}: {}", repoUrl, e.getMessage());
        }
        return null;
    }

    private String extractGitHubOwnerRepo(String url) {
        // Handle various GitHub URL formats
        url = url.replaceAll("^git@github\\.com:", "https://github.com/")
                 .replaceAll("\\.git$", "")
                 .replaceAll("github\\.com:", "github.com/");  // Fix malformed URLs with : instead of /

        if (url.contains("github.com/")) {
            String[] parts = url.split("github\\.com/");
            if (parts.length > 1) {
                String ownerRepo = parts[1];
                // Remove trailing slashes and paths
                ownerRepo = ownerRepo.split("/tree/")[0];
                ownerRepo = ownerRepo.split("/blob/")[0];
                ownerRepo = ownerRepo.replaceAll("/$", "");

                // Should be owner/repo
                String[] segments = ownerRepo.split("/");
                if (segments.length >= 2) {
                    return segments[0] + "/" + segments[1];
                }
            }
        }
        return null;
    }

    private GitHubInfo parseGitHubResponse(String json, String ownerRepo) {
        GitHubInfo info = new GitHubInfo();

        try {
            // Simple JSON parsing (avoiding Jackson dependency in service layer)
            info.starCount = extractJsonInt(json, "stargazers_count");
            info.forkCount = extractJsonInt(json, "forks_count");
            info.openIssueCount = extractJsonInt(json, "open_issues_count");
            info.archived = extractJsonBoolean(json, "archived");

            String license = extractJsonString(json, "license", "spdx_id");
            if (license != null && !license.equals("null")) {
                info.license = license;
            }

            // Fetch contributors count (separate API call)
            info.contributorCount = fetchContributorCount(ownerRepo);

            // Fetch last commit date (separate API call)
            info.lastCommitDate = fetchLastCommitDate(ownerRepo);

        } catch (Exception e) {
            log.debug("Failed to parse GitHub response: {}", e.getMessage());
        }

        return info;
    }

    private Integer fetchContributorCount(String ownerRepo) {
        try {
            String apiUrl = GITHUB_API + "/repos/" + ownerRepo + "/contributors?per_page=1&anon=true";

            HttpClient client = HttpClient.newBuilder()
                    .connectTimeout(Duration.ofMillis(timeoutMs))
                    .build();

            HttpRequest.Builder requestBuilder = HttpRequest.newBuilder()
                    .uri(URI.create(apiUrl))
                    .timeout(Duration.ofMillis(timeoutMs))
                    .header("User-Agent", "deploy-manifest-plugin/2.5.0")
                    .header("Accept", "application/vnd.github.v3+json");

            if (githubToken != null && !githubToken.isEmpty()) {
                requestBuilder.header("Authorization", "token " + githubToken);
            }

            HttpRequest request = requestBuilder.GET().build();
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() >= 200 && response.statusCode() < 300) {
                // GitHub returns Link header with total count
                String linkHeader = response.headers().firstValue("Link").orElse(null);
                if (linkHeader != null && linkHeader.contains("page=")) {
                    // Extract last page number
                    String[] parts = linkHeader.split("page=");
                    for (String part : parts) {
                        if (part.contains("rel=\"last\"")) {
                            String pageNum = part.substring(0, part.indexOf(">")).split("&")[0];
                            return Integer.parseInt(pageNum);
                        }
                    }
                }
                // If no pagination, count array elements
                int count = response.body().split("\\{").length - 1;
                return count > 0 ? count : null;
            }
        } catch (Exception e) {
            log.debug("Failed to fetch contributor count for {}: {}", ownerRepo, e.getMessage());
        }
        return null;
    }

    private Instant fetchLastCommitDate(String ownerRepo) {
        try {
            String apiUrl = GITHUB_API + "/repos/" + ownerRepo + "/commits?per_page=1";

            HttpClient client = HttpClient.newBuilder()
                    .connectTimeout(Duration.ofMillis(timeoutMs))
                    .build();

            HttpRequest.Builder requestBuilder = HttpRequest.newBuilder()
                    .uri(URI.create(apiUrl))
                    .timeout(Duration.ofMillis(timeoutMs))
                    .header("User-Agent", "deploy-manifest-plugin/2.5.0")
                    .header("Accept", "application/vnd.github.v3+json");

            if (githubToken != null && !githubToken.isEmpty()) {
                requestBuilder.header("Authorization", "token " + githubToken);
            }

            HttpRequest request = requestBuilder.GET().build();
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() >= 200 && response.statusCode() < 300) {
                String dateStr = extractJsonString(response.body(), "commit", "committer", "date");
                if (dateStr != null) {
                    return Instant.parse(dateStr);
                }
            }
        } catch (Exception e) {
            log.debug("Failed to fetch last commit date for {}: {}", ownerRepo, e.getMessage());
        }
        return null;
    }

    // Simple JSON extraction helpers (to avoid Jackson dependency)
    private Integer extractJsonInt(String json, String key) {
        try {
            String pattern = "\"" + key + "\"\\s*:\\s*(\\d+)";
            java.util.regex.Pattern p = java.util.regex.Pattern.compile(pattern);
            java.util.regex.Matcher m = p.matcher(json);
            if (m.find()) {
                return Integer.parseInt(m.group(1));
            }
        } catch (Exception e) {
            // Ignore
        }
        return null;
    }

    private Boolean extractJsonBoolean(String json, String key) {
        try {
            String pattern = "\"" + key + "\"\\s*:\\s*(true|false)";
            java.util.regex.Pattern p = java.util.regex.Pattern.compile(pattern);
            java.util.regex.Matcher m = p.matcher(json);
            if (m.find()) {
                return Boolean.parseBoolean(m.group(1));
            }
        } catch (Exception e) {
            // Ignore
        }
        return null;
    }

    private String extractJsonString(String json, String... keys) {
        try {
            String current = json;
            for (int i = 0; i < keys.length - 1; i++) {
                String pattern = "\"" + keys[i] + "\"\\s*:\\s*\\{([^}]+)\\}";
                java.util.regex.Pattern p = java.util.regex.Pattern.compile(pattern);
                java.util.regex.Matcher m = p.matcher(current);
                if (m.find()) {
                    current = m.group(1);
                } else {
                    return null;
                }
            }

            String lastKey = keys[keys.length - 1];
            String pattern = "\"" + lastKey + "\"\\s*:\\s*\"([^\"]+)\"";
            java.util.regex.Pattern p = java.util.regex.Pattern.compile(pattern);
            java.util.regex.Matcher m = p.matcher(current);
            if (m.find()) {
                return m.group(1);
            }
        } catch (Exception e) {
            // Ignore
        }
        return null;
    }

    // Inner classes for data transfer
    private static class MavenMetadata {
        String latestVersion;
        Integer totalVersions;
        Instant lastReleaseDate;
    }

    private static class GitHubInfo {
        Integer contributorCount;
        Integer starCount;
        Integer forkCount;
        Integer openIssueCount;
        Boolean archived;
        String license;
        Instant lastCommitDate;
    }
}

