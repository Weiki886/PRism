package com.weiki.prismbackend.service;

import com.weiki.prismbackend.mapper.UserMapper;
import com.weiki.prismbackend.model.dto.RepoInfo;
import com.weiki.prismbackend.model.dto.RepoPullRequest;
import com.weiki.prismbackend.model.entity.User;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
public class GitHubService {

    private static final int MAX_DIFF_TOKENS = 8000;
    private static final int MAX_CONTEXT_FILES = 5;
    private static final int MAX_FILE_TOKENS = 3000;

    private final WebClient defaultGithubWebClient;
    private final UserMapper userMapper;

    public GitHubService(@Qualifier("githubWebClient") WebClient githubWebClient,
                         UserMapper userMapper) {
        this.defaultGithubWebClient = githubWebClient;
        this.userMapper = userMapper;
    }

    /**
     * 根据 userId 获取对应的 WebClient。
     * 如果用户绑定了 GitHub（有 githubToken），使用用户自己的 token；
     * 否则 fallback 到全局 token（仅能访问公开仓库）。
     */
    private WebClient getWebClient(Long userId) {
        if (userId != null) {
            User user = userMapper.selectById(userId);
            if (user != null && user.getGithubToken() != null && !user.getGithubToken().isBlank()) {
                return WebClient.builder()
                        .baseUrl("https://api.github.com")
                        .defaultHeader("Authorization", "Bearer " + user.getGithubToken())
                        .defaultHeader("Accept", "application/vnd.github.v3+json")
                        .defaultHeader("User-Agent", "PRism/1.0")
                        .build();
            }
        }
        return defaultGithubWebClient;
    }

    @SuppressWarnings("unchecked")
    public Map<String, Object> getPrInfo(String prUrl, Long userId) {
        String[] parts = parsePrUrl(prUrl);
        String owner = parts[0], repo = parts[1], prNumber = parts[2];
        WebClient client = getWebClient(userId);

        Map<String, Object> prDetails = client.get()
                .uri("/repos/{owner}/{repo}/pulls/{number}", owner, repo, prNumber)
                .retrieve().bodyToMono(Map.class)
                .map(m -> (Map<String, Object>) m).block();

        List<Map<String, Object>> files = client.get()
                .uri("/repos/{owner}/{repo}/pulls/{number}/files?per_page=100", owner, repo, prNumber)
                .retrieve().bodyToFlux(Map.class)
                .map(m -> (Map<String, Object>) m).collectList().block();

        String headSha = (String) ((Map<String, Object>) prDetails.get("head")).get("sha");

        String diff = buildDiff(files);
        String commitMessages = fetchCommitMessages(client, owner, repo, prNumber);
        String reviewComments = fetchReviewComments(client, owner, repo, prNumber);
        String fileContexts = fetchFileContents(client, owner, repo, headSha, files);

        Map<String, Object> result = new HashMap<>();
        result.put("owner", owner);
        result.put("repo", repo);
        result.put("prNumber", prNumber);
        result.put("title", prDetails.get("title"));
        result.put("author", ((Map<String, Object>) prDetails.get("user")).get("login"));
        result.put("body", prDetails.getOrDefault("body", ""));
        result.put("diff", diff);
        result.put("commitMessages", commitMessages);
        result.put("reviewComments", reviewComments);
        result.put("fileContexts", fileContexts);

        // 上下文统计信息，用于向使用者透明化展示本次分析的上下文获取情况
        result.put("changedFiles", files != null ? files.size() : 0);
        result.put("diffTokens", diff.length() / 4);
        result.put("hasCommitMessages", !commitMessages.isBlank());
        result.put("hasReviewComments", !reviewComments.isBlank());
        result.put("hasFileContexts", !fileContexts.isBlank());
        return result;
    }

    /** 获取所有 commit message，理解开发者意图 */
    @SuppressWarnings("unchecked")
    private String fetchCommitMessages(WebClient client, String owner, String repo, String prNumber) {
        try {
            List<Map<String, Object>> commits = client.get()
                    .uri("/repos/{owner}/{repo}/pulls/{number}/commits?per_page=100", owner, repo, prNumber)
                    .retrieve().bodyToFlux(Map.class)
                    .map(m -> (Map<String, Object>) m).collectList().block();
            if (commits == null || commits.isEmpty()) return "";
            return commits.stream().map(c -> {
                Map<String, Object> commit = (Map<String, Object>) c.get("commit");
                return "- " + commit.get("message");
            }).collect(Collectors.joining("\n"));
        } catch (Exception e) {
            log.warn("获取 commit messages 失败: {}", e.getMessage());
            return "";
        }
    }

    /** 获取 PR 评论和 review 讨论 */
    @SuppressWarnings("unchecked")
    private String fetchReviewComments(WebClient client, String owner, String repo, String prNumber) {
        try {
            List<Map<String, Object>> comments = client.get()
                    .uri("/repos/{owner}/{repo}/pulls/{number}/comments?per_page=30", owner, repo, prNumber)
                    .retrieve().bodyToFlux(Map.class)
                    .map(m -> (Map<String, Object>) m).collectList().block();
            if (comments == null || comments.isEmpty()) return "";
            StringBuilder sb = new StringBuilder();
            for (Map<String, Object> c : comments) {
                String user = (String) ((Map<String, Object>) c.get("user")).get("login");
                String body = (String) c.get("body");
                String path = (String) c.get("path");
                sb.append("- @").append(user).append(" [").append(path).append("]: ");
                sb.append(body.length() > 200 ? body.substring(0, 200) + "..." : body).append("\n");
            }
            return sb.toString();
        } catch (Exception e) {
            log.warn("获取 review comments 失败: {}", e.getMessage());
            return "";
        }
    }

    /** 获取修改文件的完整内容，提供代码上下文 */
    @SuppressWarnings("unchecked")
    private String fetchFileContents(WebClient client, String owner, String repo, String headSha,
                                     List<Map<String, Object>> files) {
        StringBuilder sb = new StringBuilder();
        int count = 0;
        int tokenCount = 0;

        for (Map<String, Object> file : files) {
            if (count >= MAX_CONTEXT_FILES || tokenCount >= MAX_FILE_TOKENS) break;
            String filename = (String) file.get("filename");
            if (isNonCodeFile(filename)) continue;

            try {
                Map<String, Object> content = client.get()
                        .uri("/repos/{owner}/{repo}/contents/{path}?ref={ref}",
                                owner, repo, filename, headSha)
                        .retrieve().bodyToMono(Map.class)
                        .map(m -> (Map<String, Object>) m).block();

                if (content == null || !"file".equals(content.get("type"))) continue;
                String encoded = (String) content.get("content");
                if (encoded == null) continue;

                String decoded = new String(
                        Base64.getMimeDecoder().decode(encoded), StandardCharsets.UTF_8);
                int estimated = decoded.length() / 4;
                if (tokenCount + estimated > MAX_FILE_TOKENS) {
                    decoded = decoded.substring(0, Math.min(decoded.length(), MAX_FILE_TOKENS * 4 - tokenCount * 4));
                }

                sb.append("### ").append(filename).append(" (完整文件)\n");
                sb.append(decoded).append("\n\n");
                tokenCount += decoded.length() / 4;
                count++;
            } catch (Exception e) {
                log.debug("获取文件内容失败 {}: {}", filename, e.getMessage());
            }
        }
        return sb.toString();
    }

    private boolean isNonCodeFile(String filename) {
        String lower = filename.toLowerCase();
        return lower.endsWith(".md") || lower.endsWith(".txt") || lower.endsWith(".json")
                || lower.endsWith(".xml") || lower.endsWith(".yml") || lower.endsWith(".yaml")
                || lower.endsWith(".lock") || lower.endsWith(".png") || lower.endsWith(".jpg")
                || lower.endsWith(".svg") || lower.endsWith(".ico") || lower.contains("package-lock");
    }

    private String buildDiff(List<Map<String, Object>> files) {
        StringBuilder sb = new StringBuilder();
        int tokenCount = 0;
        for (Map<String, Object> file : files) {
            String filename = (String) file.get("filename");
            String patch = (String) file.get("patch");
            if (patch == null) continue;
            int estimated = patch.length() / 4;
            if (tokenCount + estimated > MAX_DIFF_TOKENS) break;
            sb.append("### ").append(filename).append("\n");
            sb.append(patch).append("\n\n");
            tokenCount += estimated;
        }
        return sb.toString();
    }

    private String[] parsePrUrl(String prUrl) {
        String path = prUrl.replaceFirst("https?://github\\.com/", "");
        String[] parts = path.split("/");
        if (parts.length < 4) {
            throw new IllegalArgumentException("无效的 GitHub PR 链接: " + prUrl);
        }
        return new String[]{parts[0], parts[1], parts[3]};
    }

    /**
     * 获取仓库基本信息。
     */
    @SuppressWarnings("unchecked")
    public RepoInfo getRepoInfo(String repoUrl, Long userId) {
        String[] parts = parseRepoUrl(repoUrl);
        String owner = parts[0], repo = parts[1];
        WebClient client = getWebClient(userId);

        Map<String, Object> data = client.get()
                .uri("/repos/{owner}/{repo}", owner, repo)
                .retrieve()
                .bodyToMono(Map.class)
                .map(m -> (Map<String, Object>) m)
                .block();

        if (data == null) {
            throw new IllegalArgumentException("无法获取仓库信息: " + repoUrl);
        }

        Map<String, Object> ownerMap = (Map<String, Object>) data.get("owner");
        List<String> topics = data.get("topics") != null ? (List<String>) data.get("topics") : List.of();

        return RepoInfo.builder()
                .fullName((String) data.get("full_name"))
                .description((String) data.get("description"))
                .language((String) data.get("language"))
                .starCount(toInt(data.get("stargazers_count")))
                .forkCount(toInt(data.get("forks_count")))
                .openIssuesCount(toInt(data.get("open_issues_count")))
                .ownerAvatarUrl(ownerMap != null ? (String) ownerMap.get("avatar_url") : "")
                .ownerName(ownerMap != null ? (String) ownerMap.get("login") : "")
                .htmlUrl((String) data.get("html_url"))
                .defaultBranch((String) data.get("default_branch"))
                .isPrivate(Boolean.TRUE.equals(data.get("private")))
                .topics(topics)
                .pushedAt((String) data.get("pushed_at"))
                .build();
    }

    private int toInt(Object obj) {
        if (obj instanceof Integer) return (Integer) obj;
        if (obj instanceof Long) return ((Long) obj).intValue();
        return 0;
    }

    /**
     * 解析仓库 URL，返回 [owner, repo]。
     * 支持格式：https://github.com/owner/repo 或 https://github.com/owner/repo/...
     */
    private String[] parseRepoUrl(String repoUrl) {
        String path = repoUrl.replaceFirst("https?://github\\.com/", "");
        // 去掉末尾斜杠
        if (path.endsWith("/")) path = path.substring(0, path.length() - 1);
        String[] parts = path.split("/");
        if (parts.length < 2) {
            throw new IllegalArgumentException("无效的 GitHub 仓库链接: " + repoUrl);
        }
        return new String[]{parts[0], parts[1]};
    }

    /**
     * 获取仓库的 PR 列表。
     *
     * @param repoUrl 仓库链接，如 https://github.com/owner/repo
     * @param userId  当前用户 ID（用于获取 token）
     * @param page    页码（从 1 开始）
     * @param size    每页数量（最大 100）
     * @param state   PR 状态过滤：open / closed / all
     * @return PR 列表
     */
    @SuppressWarnings("unchecked")
    public List<RepoPullRequest> listPullRequests(String repoUrl, Long userId, int page, int size, String state) {
        String[] parts = parseRepoUrl(repoUrl);
        String owner = parts[0], repo = parts[1];
        WebClient client = getWebClient(userId);

        final int finalSize = Math.min(size, 100);
        final String finalState = (state == null || state.isBlank()) ? "all" : state;

        List<Map<String, Object>> prs = client.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/repos/{owner}/{repo}/pulls")
                        .queryParam("state", finalState)
                        .queryParam("sort", "updated")
                        .queryParam("direction", "desc")
                        .queryParam("per_page", finalSize)
                        .queryParam("page", page)
                        .build(owner, repo))
                .retrieve()
                .bodyToFlux(Map.class)
                .map(m -> (Map<String, Object>) m)
                .collectList()
                .block();

        if (prs == null) return List.of();

        return prs.stream().map(pr -> {
            Map<String, Object> user = (Map<String, Object>) pr.get("user");
            List<Map<String, Object>> labelList = (List<Map<String, Object>>) pr.get("labels");
            List<String> labels = labelList == null ? List.of()
                    : labelList.stream().map(l -> (String) l.get("name")).toList();

            boolean merged = pr.get("merged_at") != null;
            String prState = merged ? "merged" : (String) pr.get("state");

            return RepoPullRequest.builder()
                    .number((Integer) pr.get("number"))
                    .title((String) pr.get("title"))
                    .author(user != null ? (String) user.get("login") : "")
                    .avatarUrl(user != null ? (String) user.get("avatar_url") : "")
                    .state(prState)
                    .createdAt((String) pr.get("created_at"))
                    .updatedAt((String) pr.get("updated_at"))
                    .labels(labels)
                    .htmlUrl((String) pr.get("html_url"))
                    .merged(merged)
                    .build();
        }).toList();
    }

    /**
     * 获取仓库的 PR 总数（通过 GitHub Search API）。
     */
    @SuppressWarnings("unchecked")
    public int countPullRequests(String repoUrl, Long userId, String state) {
        String[] parts = parseRepoUrl(repoUrl);
        String owner = parts[0], repo = parts[1];
        WebClient client = getWebClient(userId);

        // GitHub Search API: is:pr repo:owner/repo state:open/closed
        String query = "repo:" + owner + "/" + repo + " is:pr";
        if ("open".equals(state)) {
            query += " is:open";
        } else if ("closed".equals(state)) {
            query += " is:closed";
        }

        String finalQuery = query;
        Map<String, Object> result = client.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/search/issues")
                        .queryParam("q", finalQuery)
                        .queryParam("per_page", 1)
                        .build())
                .retrieve()
                .bodyToMono(Map.class)
                .map(m -> (Map<String, Object>) m)
                .block();

        if (result == null) return 0;
        Object totalCount = result.get("total_count");
        return totalCount instanceof Integer ? (Integer) totalCount : 0;
    }
}
