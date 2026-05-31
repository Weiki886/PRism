package com.weiki.prismbackend.controller;

import com.weiki.prismbackend.common.Result;
import com.weiki.prismbackend.model.dto.PageResult;
import com.weiki.prismbackend.model.dto.RepoBrowsingDTO;
import com.weiki.prismbackend.model.dto.RepoInfo;
import com.weiki.prismbackend.model.dto.RepoPullRequest;
import com.weiki.prismbackend.security.SecurityUserPrincipal;
import com.weiki.prismbackend.service.GitHubService;
import com.weiki.prismbackend.service.RepoBrowsingService;
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
    private final RepoBrowsingService repoBrowsingService;

    public RepoController(GitHubService gitHubService, RepoBrowsingService repoBrowsingService) {
        this.gitHubService = gitHubService;
        this.repoBrowsingService = repoBrowsingService;
    }

    @Operation(summary = "获取仓库基本信息",
            description = "输入 GitHub 仓库链接，返回仓库概览信息（名称、描述、Star、Fork、语言等）。"
                    + "请求成功时会自动登记到当前用户的最近浏览仓库列表。")
    @GetMapping("/info")
    public Result<RepoInfo> getRepoInfo(
            @Parameter(description = "仓库链接，如 https://github.com/owner/repo", required = true)
            @RequestParam String repoUrl,
            @AuthenticationPrincipal SecurityUserPrincipal principal) {

        RepoInfo info = gitHubService.getRepoInfo(repoUrl, principal.getUserId());
        try {
            repoBrowsingService.recordVisit(principal.getUserId(), repoUrl, info);
        } catch (Exception ignored) {
            // 登记最近浏览失败不影响主流程：用户仍能拿到仓库信息
        }
        return Result.success(info);
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

    @Operation(summary = "获取我最近浏览过的仓库",
            description = "按浏览时间倒序返回，最多 limit 条（默认 20，最大 50）。")
    @GetMapping("/history")
    public Result<List<RepoBrowsingDTO>> listHistory(
            @Parameter(description = "返回条数上限，默认 20，最大 50")
            @RequestParam(defaultValue = "20") int limit,
            @AuthenticationPrincipal SecurityUserPrincipal principal) {

        return Result.success(repoBrowsingService.listRecent(principal.getUserId(), limit));
    }

    @Operation(summary = "删除一条仓库浏览记录")
    @DeleteMapping("/history/{id}")
    public Result<Void> deleteHistory(
            @Parameter(description = "浏览记录 ID") @PathVariable Long id,
            @AuthenticationPrincipal SecurityUserPrincipal principal) {

        repoBrowsingService.deleteById(principal.getUserId(), id);
        return Result.success();
    }

    @Operation(summary = "清空我的所有仓库浏览记录")
    @DeleteMapping("/history")
    public Result<Void> clearHistory(@AuthenticationPrincipal SecurityUserPrincipal principal) {
        repoBrowsingService.clearAll(principal.getUserId());
        return Result.success();
    }
}
