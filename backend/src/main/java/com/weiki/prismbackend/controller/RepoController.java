package com.weiki.prismbackend.controller;

import com.weiki.prismbackend.common.Result;
import com.weiki.prismbackend.model.dto.PageResult;
import com.weiki.prismbackend.model.dto.RepoPullRequest;
import com.weiki.prismbackend.security.SecurityUserPrincipal;
import com.weiki.prismbackend.service.GitHubService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "Repository", description = "GitHub 仓库浏览接口")
@RestController
@RequestMapping("/api/repo")
@SecurityRequirement(name = "Authorization")
public class RepoController {

    private final GitHubService gitHubService;

    public RepoController(GitHubService gitHubService) {
        this.gitHubService = gitHubService;
    }

    @Operation(summary = "获取仓库 PR 列表",
            description = "输入 GitHub 仓库链接，分页返回该仓库的 Pull Request 列表。支持按状态过滤。")
    @GetMapping("/pulls")
    public Result<PageResult<RepoPullRequest>> listPulls(
            @Parameter(description = "仓库链接，如 https://github.com/owner/repo", required = true)
            @RequestParam String repoUrl,
            @Parameter(description = "页码，从 1 开始")
            @RequestParam(defaultValue = "1") int page,
            @Parameter(description = "每页数量，最大 100")
            @RequestParam(defaultValue = "20") int size,
            @Parameter(description = "PR 状态过滤：open / closed / all")
            @RequestParam(defaultValue = "all") String state,
            @AuthenticationPrincipal SecurityUserPrincipal principal) {

        List<RepoPullRequest> pulls = gitHubService.listPullRequests(repoUrl, principal.getUserId(), page, size, state);
        int total = gitHubService.countPullRequests(repoUrl, principal.getUserId(), state);

        return Result.success(PageResult.of(pulls, total, page, size));
    }
}
