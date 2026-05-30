package com.weiki.prismbackend.controller;

import com.weiki.prismbackend.common.Result;
import com.weiki.prismbackend.common.ResultCode;
import com.weiki.prismbackend.exception.BusinessException;
import com.weiki.prismbackend.model.ReviewRequest;
import com.weiki.prismbackend.model.ReviewResponse;
import com.weiki.prismbackend.model.entity.Review;
import com.weiki.prismbackend.security.SecurityUserPrincipal;
import com.weiki.prismbackend.service.ReviewProcessor;
import com.weiki.prismbackend.service.ReviewService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@Tag(name = "PR Review", description = "AI PR 代码审查接口")
@RestController
@RequestMapping("/api")
@SecurityRequirement(name = "bearerAuth")
public class ReviewController {

    private final ReviewService reviewService;
    private final ReviewProcessor reviewProcessor;

    public ReviewController(ReviewService reviewService, ReviewProcessor reviewProcessor) {
        this.reviewService = reviewService;
        this.reviewProcessor = reviewProcessor;
    }

    @Operation(summary = "触发 PR 分析",
            description = "传入 GitHub PR 链接，立即返回 reviewId 并在后台异步分析。"
                    + "前端通过轮询 GET /api/review/{id} 获取进度，status 流转：pending → processing → completed / error")
    @PostMapping("/review")
    public Result<ReviewResponse> createReview(
            @Valid @RequestBody ReviewRequest request,
            @AuthenticationPrincipal SecurityUserPrincipal principal) {

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
            @Parameter(description = "review id") @PathVariable String id) {
        Review r = reviewService.findById(id)
                .orElseThrow(() -> new BusinessException(ResultCode.NOT_FOUND));
        return Result.success(toResponse(r));
    }

    @Operation(summary = "获取我的评审历史", description = "分页查询当前用户的评审记录")
    @GetMapping("/review/history")
    public Result<List<ReviewResponse>> getHistory(
            @AuthenticationPrincipal SecurityUserPrincipal principal,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size) {

        List<ReviewResponse> list = reviewService.findByUserId(principal.getUserId(), page, size)
                .stream()
                .map(this::toResponse)
                .toList();
        return Result.success(list);
    }

    private ReviewResponse toResponse(Review r) {
        return ReviewResponse.builder()
                .id(r.getId())
                .prTitle(r.getPrTitle())
                .author(r.getAuthor())
                .summary(r.getSummary())
                .risks(reviewService.parseRisks(r.getRisksJson()))
                .suggestions(reviewService.parseSuggestions(r.getSuggestionsJson()))
                .status(r.getStatus())
                .build();
    }
}
