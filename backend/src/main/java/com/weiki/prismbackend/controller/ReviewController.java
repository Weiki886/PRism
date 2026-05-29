package com.weiki.prismbackend.controller;

import com.weiki.prismbackend.common.Result;
import com.weiki.prismbackend.model.ReviewRequest;
import com.weiki.prismbackend.model.ReviewResponse;
import com.weiki.prismbackend.model.entity.Review;
import com.weiki.prismbackend.security.SecurityUserPrincipal;
import com.weiki.prismbackend.service.AiReviewService;
import com.weiki.prismbackend.service.GitHubService;
import com.weiki.prismbackend.service.ReviewService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@Tag(name = "PR Review", description = "AI PR 代码审查接口")
@RestController
@RequestMapping("/api")
public class ReviewController {

    private final GitHubService gitHubService;
    private final AiReviewService aiReviewService;
    private final ReviewService reviewService;

    public ReviewController(
            GitHubService gitHubService,
            AiReviewService aiReviewService,
            ReviewService reviewService) {
        this.gitHubService = gitHubService;
        this.aiReviewService = aiReviewService;
        this.reviewService = reviewService;
    }

    @Operation(summary = "触发 PR 分析", description = "传入 GitHub PR 链接，调用 AI 进行代码审查，返回分析结果（同步，需 5~15 秒）")
    @PostMapping("/review")
    public ResponseEntity<Result<ReviewResponse>> createReview(
            @RequestBody ReviewRequest request,
            @AuthenticationPrincipal SecurityUserPrincipal principal) {

        Map<String, Object> prInfo = gitHubService.getPrInfo(request.getPrUrl());

        ReviewResponse aiResult = aiReviewService.analyze(
                (String) prInfo.get("title"),
                (String) prInfo.get("body"),
                (String) prInfo.get("diff"),
                (String) prInfo.get("author"),
                (String) prInfo.get("commitMessages"),
                (String) prInfo.get("reviewComments"),
                (String) prInfo.get("fileContexts")
        );

        Review review = Review.builder()
                .id(aiResult.getId())
                .prUrl(request.getPrUrl())
                .prTitle(aiResult.getPrTitle())
                .author(aiResult.getAuthor())
                .userId(principal.getUserId())
                .summary(aiResult.getSummary())
                .risksJson(reviewService.risksToJson(aiResult.getRisks()))
                .suggestionsJson(reviewService.suggestionsToJson(aiResult.getSuggestions()))
                .status(aiResult.getStatus())
                .ghRepo((String) prInfo.get("repo"))
                .ghPrNumber((String) prInfo.get("prNumber"))
                .build();

        reviewService.saveReview(review);
        return ResponseEntity.ok(Result.success(aiResult));
    }

    @Operation(summary = "查询分析结果", description = "根据 review id 查询已有的分析结果")
    @GetMapping("/review/{id}")
    public ResponseEntity<Result<ReviewResponse>> getReview(
            @Parameter(description = "review id") @PathVariable String id) {
        return reviewService.findById(id)
                .map(r -> {
                    ReviewResponse resp = ReviewResponse.builder()
                            .id(r.getId())
                            .prTitle(r.getPrTitle())
                            .author(r.getAuthor())
                            .summary(r.getSummary())
                            .risks(reviewService.parseRisks(r.getRisksJson()))
                            .suggestions(reviewService.parseSuggestions(r.getSuggestionsJson()))
                            .status(r.getStatus())
                            .build();
                    return ResponseEntity.ok(Result.success(resp));
                })
                .orElse(ResponseEntity.notFound().build());
    }

    @Operation(summary = "获取我的评审历史", description = "分页查询当前用户的评审记录")
    @GetMapping("/review/history")
    public ResponseEntity<Result<List<ReviewResponse>>> getHistory(
            @AuthenticationPrincipal SecurityUserPrincipal principal,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size) {

        List<Review> reviews = reviewService.findByUserId(principal.getUserId(), page, size);
        List<ReviewResponse> list = reviews.stream()
                .map(r -> ReviewResponse.builder()
                        .id(r.getId())
                        .prTitle(r.getPrTitle())
                        .author(r.getAuthor())
                        .summary(r.getSummary())
                        .risks(reviewService.parseRisks(r.getRisksJson()))
                        .suggestions(reviewService.parseSuggestions(r.getSuggestionsJson()))
                        .status(r.getStatus())
                        .build())
                .toList();
        return ResponseEntity.ok(Result.success(list));
    }
}
