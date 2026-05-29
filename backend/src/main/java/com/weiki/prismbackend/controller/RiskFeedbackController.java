package com.weiki.prismbackend.controller;

import com.weiki.prismbackend.common.Result;
import com.weiki.prismbackend.model.dto.RiskFeedbackRequest;
import com.weiki.prismbackend.model.dto.RiskFeedbackStat;
import com.weiki.prismbackend.security.SecurityUserPrincipal;
import com.weiki.prismbackend.service.RiskFeedbackService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "风险反馈", description = "对 AI 识别的风险进行误报/确认反馈")
@RestController
@RequestMapping("/api/review")
@SecurityRequirement(name = "bearerAuth")
public class RiskFeedbackController {

    private final RiskFeedbackService feedbackService;

    public RiskFeedbackController(RiskFeedbackService feedbackService) {
        this.feedbackService = feedbackService;
    }

    @Operation(summary = "提交风险反馈", description = "标记某个风险为误报或确认，同一用户重复提交会覆盖")
    @PostMapping("/{reviewId}/feedback")
    public Result<Void> submitFeedback(
            @Parameter(description = "review id") @PathVariable String reviewId,
            @AuthenticationPrincipal SecurityUserPrincipal principal,
            @Valid @RequestBody RiskFeedbackRequest req) {
        feedbackService.submitFeedback(reviewId, principal.getUserId(), req);
        return Result.success();
    }

    @Operation(summary = "查询风险反馈统计", description = "返回该审查记录下各风险的误报/确认次数及当前用户的反馈")
    @GetMapping("/{reviewId}/feedback")
    public Result<List<RiskFeedbackStat>> getFeedbackStats(
            @Parameter(description = "review id") @PathVariable String reviewId,
            @AuthenticationPrincipal SecurityUserPrincipal principal) {
        return Result.success(feedbackService.getStats(reviewId, principal.getUserId()));
    }
}
