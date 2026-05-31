package com.weiki.prismbackend.controller;

import com.weiki.prismbackend.common.Result;
import com.weiki.prismbackend.exception.BusinessException;
import com.weiki.prismbackend.model.ReviewResponse;
import com.weiki.prismbackend.model.RiskItem;
import com.weiki.prismbackend.model.dto.ReviewStats;
import com.weiki.prismbackend.model.entity.Review;
import com.weiki.prismbackend.security.SecurityUserPrincipal;
import com.weiki.prismbackend.service.ReviewExportService;
import com.weiki.prismbackend.service.ReviewProcessor;
import com.weiki.prismbackend.service.ReviewService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * ReviewController 单元测试。
 * 不启动 Spring 容器，直接调用 Controller 方法验证逻辑。
 * 验证响应格式、状态码映射、异常抛出。
 */
class ReviewControllerTest {

    private final ReviewService reviewService = mock(ReviewService.class);
    private final ReviewProcessor reviewProcessor = mock(ReviewProcessor.class);
    private final ReviewExportService reviewExportService = mock(ReviewExportService.class);
    private final ReviewController controller = new ReviewController(reviewService, reviewProcessor, reviewExportService);

    private SecurityUserPrincipal principal() {
        return new SecurityUserPrincipal(1L, "testuser");
    }

    // ========== 查询接口测试 ==========

    @Test
    @DisplayName("getReview 记录不存在应抛出 BusinessException")
    void getReview_notFound_shouldThrow() {
        when(reviewService.findById("nonexist")).thenReturn(Optional.empty());

        assertThrows(BusinessException.class, () -> controller.getReview("nonexist"));
    }

    @Test
    @DisplayName("getReview 记录存在应返回完整数据")
    void getReview_found_shouldReturnData() {
        Review review = Review.builder()
                .id("r1")
                .prTitle("fix bug")
                .author("dev")
                .summary("Fixed a bug")
                .status("completed")
                .risksJson("[]")
                .suggestionsJson("[]")
                .build();
        when(reviewService.findById("r1")).thenReturn(Optional.of(review));
        when(reviewService.parseRisks("[]")).thenReturn(List.of());
        when(reviewService.parseSuggestions("[]")).thenReturn(List.of());

        Result<ReviewResponse> result = controller.getReview("r1");

        assertEquals(200, result.getCode());
        assertEquals("r1", result.getData().getId());
        assertEquals("completed", result.getData().getStatus());
        assertEquals("fix bug", result.getData().getPrTitle());
    }

    @Test
    @DisplayName("getReview completed 状态应包含 healthScore 和 mergeAdvice")
    void getReview_completed_shouldHaveHealthScore() {
        List<RiskItem> risks = List.of(
                RiskItem.builder().level("HIGH").build()
        );
        Review review = Review.builder()
                .id("r2")
                .prTitle("add feature")
                .author("dev")
                .summary("Added feature")
                .status("completed")
                .risksJson("[{\"level\":\"HIGH\"}]")
                .suggestionsJson("[]")
                .build();
        when(reviewService.findById("r2")).thenReturn(Optional.of(review));
        when(reviewService.parseRisks(anyString())).thenReturn(risks);
        when(reviewService.parseSuggestions(anyString())).thenReturn(List.of());

        Result<ReviewResponse> result = controller.getReview("r2");

        assertNotNull(result.getData().getHealthScore());
        assertNotNull(result.getData().getMergeAdvice());
        assertEquals(85, result.getData().getHealthScore()); // 100 - 15 (HIGH)
        assertEquals("RECOMMEND", result.getData().getMergeAdvice());
    }

    @Test
    @DisplayName("getReview pending 状态不应有 healthScore")
    void getReview_pending_shouldNotHaveHealthScore() {
        Review review = Review.builder()
                .id("r3")
                .status("pending")
                .risksJson(null)
                .suggestionsJson(null)
                .build();
        when(reviewService.findById("r3")).thenReturn(Optional.of(review));
        when(reviewService.parseRisks(null)).thenReturn(List.of());
        when(reviewService.parseSuggestions(null)).thenReturn(List.of());

        Result<ReviewResponse> result = controller.getReview("r3");

        assertNull(result.getData().getHealthScore());
        assertNull(result.getData().getMergeAdvice());
    }

    // ========== 统计接口测试 ==========

    @Test
    @DisplayName("getStats 应返回统计数据")
    void getStats_shouldReturnStats() {
        Map<String, Long> dist = new LinkedHashMap<>();
        dist.put("CRITICAL", 0L);
        dist.put("HIGH", 5L);
        dist.put("MEDIUM", 10L);
        dist.put("LOW", 10L);

        ReviewStats stats = ReviewStats.builder()
                .totalReviews(10)
                .completedReviews(8)
                .errorReviews(1)
                .processingReviews(1)
                .totalRisks(25)
                .riskLevelDistribution(dist)
                .build();
        when(reviewService.statsByUserId(1L)).thenReturn(stats);

        Result<ReviewStats> result = controller.getStats(principal());

        assertEquals(200, result.getCode());
        assertEquals(10, result.getData().getTotalReviews());
        assertEquals(25, result.getData().getTotalRisks());
        assertEquals(5L, result.getData().getRiskLevelDistribution().get("HIGH"));
    }

    // ========== 删除接口测试 ==========

    @Test
    @DisplayName("deleteReview 成功删除应返回 200")
    void deleteReview_success() {
        when(reviewService.deleteByIdAndUser("r1", 1L)).thenReturn(true);

        Result<Void> result = controller.deleteReview("r1", principal());

        assertEquals(200, result.getCode());
    }

    @Test
    @DisplayName("deleteReview 记录不存在应抛出 BusinessException")
    void deleteReview_notFound_shouldThrow() {
        when(reviewService.deleteByIdAndUser("nonexist", 1L)).thenReturn(false);

        assertThrows(BusinessException.class, () -> controller.deleteReview("nonexist", principal()));
    }
}
