package com.weiki.prismbackend.controller;

import com.weiki.prismbackend.common.Result;
import com.weiki.prismbackend.common.ResultCode;
import com.weiki.prismbackend.exception.BusinessException;
import com.weiki.prismbackend.model.ReviewRequest;
import com.weiki.prismbackend.model.ReviewResponse;
import com.weiki.prismbackend.model.RiskItem;
import com.weiki.prismbackend.model.dto.DuplicateReviewResponse;
import com.weiki.prismbackend.model.dto.PageResult;
import com.weiki.prismbackend.model.dto.ReviewStats;
import com.weiki.prismbackend.model.entity.Review;
import com.weiki.prismbackend.security.SecurityUserPrincipal;
import com.weiki.prismbackend.service.GitHubService;
import com.weiki.prismbackend.service.HealthScoreCalculator;
import com.weiki.prismbackend.service.ReviewCommentFormatter;
import com.weiki.prismbackend.service.ReviewExportService;
import com.weiki.prismbackend.service.ReviewProcessor;
import com.weiki.prismbackend.service.ReviewService;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Tag(name = "PR Review", description = "AI PR 代码审查接口")
@RestController
@RequestMapping("/api")
@SecurityRequirement(name = "Authorization")
public class ReviewController {

    private final ReviewService reviewService;
    private final ReviewProcessor reviewProcessor;
    private final ReviewExportService reviewExportService;
    private final GitHubService gitHubService;

    public ReviewController(ReviewService reviewService, ReviewProcessor reviewProcessor,
                            ReviewExportService reviewExportService,
                            GitHubService gitHubService) {
        this.reviewService = reviewService;
        this.reviewProcessor = reviewProcessor;
        this.reviewExportService = reviewExportService;
        this.gitHubService = gitHubService;
    }

    @Operation(summary = "触发 PR 分析",
            description = "传入 GitHub PR 链接，立即返回 reviewId 并在后台异步分析。"
                    + "若该用户已分析过同一 PR，返回 409 及已有记录信息，前端可引导用户查看或重新分析。"
                    + "前端通过轮询 GET /api/review/{id} 获取进度，status 流转：pending → processing → completed / error")
    @PostMapping("/review")
    public Result<?> createReview(
            @Valid @RequestBody ReviewRequest request,
            @AuthenticationPrincipal SecurityUserPrincipal principal) {

        // 去重检查：同一用户 + 同一 PR URL
        Optional<Review> existing = reviewService.findByUserIdAndPrUrl(
                principal.getUserId(), request.getPrUrl());
        if (existing.isPresent()) {
            Review r = existing.get();
            DuplicateReviewResponse dup = DuplicateReviewResponse.builder()
                    .existingReviewId(r.getId())
                    .status(r.getStatus())
                    .prTitle(r.getPrTitle())
                    .build();
            return Result.error(ResultCode.REVIEW_DUPLICATE, dup);
        }

        String reviewId = "review_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);

        // 先落库一条 pending 记录，再异步分析
        Review review = Review.builder()
                .id(reviewId)
                .prUrl(request.getPrUrl())
                .prTitle("")
                .author("")
                .userId(principal.getUserId())
                .status("pending")
                .ghRepo("")
                .ghPrNumber("")
                .build();
        reviewService.saveReview(review);

        reviewProcessor.process(reviewId, request.getPrUrl());

        ReviewResponse resp = ReviewResponse.builder()
                .id(reviewId)
                .status("pending")
                .build();
        return Result.success(resp);
    }

    @Operation(summary = "查询分析结果",
            description = "根据 review id 查询分析进度和结果，用于前端轮询")
    @GetMapping("/review/{id}")
    public Result<ReviewResponse> getReview(
            @Parameter(description = "review id") @PathVariable String id,
            @AuthenticationPrincipal SecurityUserPrincipal principal) {
        Review r = reviewService.findByIdAndUser(id, principal.getUserId())
                .orElseThrow(() -> new BusinessException(ResultCode.NOT_FOUND));
        return Result.success(toResponse(r));
    }

    @Operation(summary = "获取我的评审历史", description = "分页查询当前用户的评审记录，支持按关键词和状态搜索")
    @GetMapping("/review/history")
    public Result<PageResult<ReviewResponse>> getHistory(
            @AuthenticationPrincipal SecurityUserPrincipal principal,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String status) {

        Page<Review> result = reviewService.searchByUserId(principal.getUserId(), page, size, keyword, status);
        List<ReviewResponse> list = result.getRecords().stream()
                .map(this::toResponse)
                .toList();
        return Result.success(PageResult.of(list, result.getTotal(), page, size));
    }

    @Operation(summary = "重新分析",
            description = "对已有评审记录重新触发分析（常用于 error 状态的重试），复用原 PR 链接，"
                    + "在原记录上更新结果。正在分析中（pending/processing）的记录不可重试。")
    @PostMapping("/review/{id}/retry")
    public Result<ReviewResponse> retryReview(
            @Parameter(description = "review id") @PathVariable String id,
            @AuthenticationPrincipal SecurityUserPrincipal principal) {

        Review review = reviewService.findByIdAndUser(id, principal.getUserId())
                .orElseThrow(() -> new BusinessException(ResultCode.NOT_FOUND));

        // 正在分析中的记录不允许重复触发，避免并发重复分析
        if ("pending".equals(review.getStatus()) || "processing".equals(review.getStatus())) {
            throw new BusinessException(ResultCode.REVIEW_IN_PROGRESS);
        }

        // 重置为 pending 后重新异步分析
        review.setStatus("pending");
        reviewService.updateReview(review);

        reviewProcessor.process(id, review.getPrUrl());

        ReviewResponse resp = ReviewResponse.builder()
                .id(id)
                .status("pending")
                .build();
        return Result.success(resp);
    }

    @Operation(summary = "获取我的评审统计", description = "返回当前用户的评审概览：记录总数、状态分布、累计风险数及各等级分布")
    @GetMapping("/review/stats")
    public Result<ReviewStats> getStats(
            @AuthenticationPrincipal SecurityUserPrincipal principal) {
        return Result.success(reviewService.statsByUserId(principal.getUserId()));
    }

    @Operation(summary = "删除评审记录", description = "删除当前用户的某条评审记录，仅能删除属于自己的记录")
    @DeleteMapping("/review/{id}")
    public Result<Void> deleteReview(
            @Parameter(description = "review id") @PathVariable String id,
            @AuthenticationPrincipal SecurityUserPrincipal principal) {
        boolean deleted = reviewService.deleteByIdAndUser(id, principal.getUserId());
        if (!deleted) {
            throw new BusinessException(ResultCode.NOT_FOUND);
        }
        return Result.success();
    }

    @Operation(summary = "导出评审报告", description = "将评审结果导出为 Markdown 或 PDF 格式下载")
    @GetMapping("/review/{id}/export")
    public void exportReview(
            @Parameter(description = "review id") @PathVariable String id,
            @Parameter(description = "导出格式：md 或 pdf") @RequestParam(defaultValue = "md") String format,
            @AuthenticationPrincipal SecurityUserPrincipal principal,
            HttpServletResponse response) throws Exception {

        Review review = reviewService.findByIdAndUser(id, principal.getUserId())
                .orElseThrow(() -> new BusinessException(ResultCode.NOT_FOUND));

        if (!"completed".equals(review.getStatus())) {
            throw new BusinessException(ResultCode.BAD_REQUEST);
        }

        String filename = "review-" + id;

        if ("pdf".equalsIgnoreCase(format)) {
            byte[] pdfBytes = reviewExportService.exportPdf(review);
            response.setContentType("application/pdf");
            response.setHeader("Content-Disposition", "attachment; filename=\"" + filename + ".pdf\"");
            response.setContentLength(pdfBytes.length);
            response.getOutputStream().write(pdfBytes);
            response.getOutputStream().flush();
        } else {
            String markdown = reviewExportService.exportMarkdown(review);
            byte[] mdBytes = markdown.getBytes(java.nio.charset.StandardCharsets.UTF_8);
            response.setContentType("text/markdown; charset=UTF-8");
            response.setHeader("Content-Disposition", "attachment; filename=\"" + filename + ".md\"");
            response.setContentLength(mdBytes.length);
            response.getOutputStream().write(mdBytes);
            response.getOutputStream().flush();
        }
    }

    @Operation(summary = "获取回写评论预览",
            description = "根据分析结果生成 Markdown 格式的评论预览，供用户编辑确认后再发送")
    @GetMapping("/review/{id}/comment-preview")
    public Result<Map<String, String>> getCommentPreview(
            @Parameter(description = "review id") @PathVariable String id,
            @AuthenticationPrincipal SecurityUserPrincipal principal) {

        Review review = reviewService.findByIdAndUser(id, principal.getUserId())
                .orElseThrow(() -> new BusinessException(ResultCode.NOT_FOUND));

        if (!"completed".equals(review.getStatus())) {
            throw new BusinessException(ResultCode.BAD_REQUEST);
        }

        List<RiskItem> risks = reviewService.parseRisks(review.getRisksJson());
        List<String> suggestions = reviewService.parseSuggestions(review.getSuggestionsJson());
        int healthScore = HealthScoreCalculator.calculate(risks);
        String mergeAdvice = HealthScoreCalculator.mergeAdvice(healthScore);
        String body = ReviewCommentFormatter.format(review, risks, suggestions, healthScore, mergeAdvice);

        return Result.success(Map.of("body", body));
    }

    @Operation(summary = "回写评论到 GitHub PR",
            description = "将用户确认/编辑后的评论内容发送到对应的 GitHub PR")
    @PostMapping("/review/{id}/comment")
    public Result<Void> postComment(
            @Parameter(description = "review id") @PathVariable String id,
            @RequestBody Map<String, String> request,
            @AuthenticationPrincipal SecurityUserPrincipal principal) {

        Review review = reviewService.findByIdAndUser(id, principal.getUserId())
                .orElseThrow(() -> new BusinessException(ResultCode.NOT_FOUND));

        if (!"completed".equals(review.getStatus())) {
            throw new BusinessException(ResultCode.BAD_REQUEST);
        }

        String body = request.get("body");
        if (body == null || body.isBlank()) {
            throw new BusinessException(ResultCode.BAD_REQUEST);
        }

        gitHubService.postComment(review.getPrUrl(), principal.getUserId(), body);
        return Result.success();
    }

    private ReviewResponse toResponse(Review r) {
        List<RiskItem> risks = reviewService.parseRisks(r.getRisksJson());

        // 仅对已完成的分析计算健康分，其余状态保持为空
        Integer healthScore = null;
        String mergeAdvice = null;
        if ("completed".equals(r.getStatus())) {
            int score = HealthScoreCalculator.calculate(risks);
            healthScore = score;
            mergeAdvice = HealthScoreCalculator.mergeAdvice(score);
        }

        return ReviewResponse.builder()
                .id(r.getId())
                .prUrl(r.getPrUrl())
                .prTitle(r.getPrTitle())
                .author(r.getAuthor())
                .summary(r.getSummary())
                .risks(risks)
                .suggestions(reviewService.parseSuggestions(r.getSuggestionsJson()))
                .healthScore(healthScore)
                .mergeAdvice(mergeAdvice)
                .contextInfo(reviewService.parseContextInfo(r.getContextInfoJson()))
                .status(r.getStatus())
                .build();
    }
}
