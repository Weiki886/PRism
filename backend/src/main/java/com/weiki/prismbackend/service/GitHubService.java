package com.weiki.prismbackend.service;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class GitHubService {

    private static final int MAX_DIFF_TOKENS = 8000;

    private final WebClient githubWebClient;

    public GitHubService(@Qualifier("githubWebClient") WebClient githubWebClient) {
        this.githubWebClient = githubWebClient;
    }

    @SuppressWarnings("unchecked")
    public Map<String, Object> getPrInfo(String prUrl) {
        String[] parts = parsePrUrl(prUrl);
        String owner = parts[0], repo = parts[1], prNumber = parts[2];

        Map<String, Object> prDetails = githubWebClient.get()
                .uri("/repos/{owner}/{repo}/pulls/{number}", owner, repo, prNumber)
                .retrieve()
                .bodyToMono(Map.class)
                .map(m -> (Map<String, Object>) m)
                .block();

        List<Map<String, Object>> files = githubWebClient.get()
                .uri("/repos/{owner}/{repo}/pulls/{number}/files?per_page=100", owner, repo, prNumber)
                .retrieve()
                .bodyToFlux(Map.class)
                .map(m -> (Map<String, Object>) m)
                .collectList()
                .block();

        Map<String, Object> result = new HashMap<>();
        result.put("owner", owner);
        result.put("repo", repo);
        result.put("prNumber", prNumber);
        result.put("title", prDetails.get("title"));
        result.put("author", ((Map<String, Object>) prDetails.get("user")).get("login"));
        result.put("body", prDetails.getOrDefault("body", ""));
        result.put("diff", buildDiff(files));
        return result;
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
}
