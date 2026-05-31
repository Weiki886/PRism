package com.weiki.prismbackend.service;

import com.weiki.prismbackend.common.ResultCode;
import com.weiki.prismbackend.exception.BusinessException;
import com.weiki.prismbackend.mapper.RiskFeedbackMapper;
import com.weiki.prismbackend.model.dto.FeedbackOverview;
import com.weiki.prismbackend.model.dto.RiskFeedbackRequest;
import com.weiki.prismbackend.model.dto.RiskFeedbackStat;
import com.weiki.prismbackend.model.entity.Review;
import com.weiki.prismbackend.model.entity.RiskFeedback;
import com.weiki.prismbackend.model.enums.FeedbackType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * RiskFeedbackService 单元测试。
 * 验证反馈去重、误报率计算逻辑。
 */
@ExtendWith(MockitoExtension.class)
class RiskFeedbackServiceTest {

    @Mock
    private RiskFeedbackMapper feedbackMapper;
    @Mock
    private ReviewService reviewService;

    private RiskFeedbackService riskFeedbackService;

    @BeforeEach
    void setUp() {
        riskFeedbackService = new RiskFeedbackService(feedbackMapper, reviewService);
    }

    // ========== submitFeedback 去重测试 ==========

    @Test
    @DisplayName("首次提交反馈应执行 insert")
    void submitFeedback_firstTime_shouldInsert() {
        when(reviewService.findById("r1")).thenReturn(Optional.of(new Review()));
        when(feedbackMapper.selectOne(any())).thenReturn(null);

        RiskFeedbackRequest req = new RiskFeedbackRequest();
        req.setRiskIndex(0);
        req.setFeedback(FeedbackType.CONFIRMED);
        req.setComment("looks correct");

        riskFeedbackService.submitFeedback("r1", 1L, req);

        ArgumentCaptor<RiskFeedback> captor = ArgumentCaptor.forClass(RiskFeedback.class);
        verify(feedbackMapper).insert(captor.capture());
        assertEquals("r1", captor.getValue().getReviewId());
        assertEquals(0, captor.getValue().getRiskIndex());
        assertEquals(FeedbackType.CONFIRMED, captor.getValue().getFeedback());
    }

    @Test
    @DisplayName("重复提交反馈应执行 update 而非 insert")
    void submitFeedback_duplicate_shouldUpdate() {
        when(reviewService.findById("r1")).thenReturn(Optional.of(new Review()));
        RiskFeedback existing = RiskFeedback.builder()
                .id(100L)
                .reviewId("r1")
                .riskIndex(0)
                .userId(1L)
                .feedback(FeedbackType.CONFIRMED)
                .comment("")
                .build();
        when(feedbackMapper.selectOne(any())).thenReturn(existing);

        RiskFeedbackRequest req = new RiskFeedbackRequest();
        req.setRiskIndex(0);
        req.setFeedback(FeedbackType.FALSE_POSITIVE);
        req.setComment("actually a false positive");

        riskFeedbackService.submitFeedback("r1", 1L, req);

        verify(feedbackMapper, never()).insert(any(RiskFeedback.class));
        verify(feedbackMapper).updateById(existing);
        assertEquals(FeedbackType.FALSE_POSITIVE, existing.getFeedback());
        assertEquals("actually a false positive", existing.getComment());
    }

    @Test
    @DisplayName("review 不存在时应抛出 BusinessException")
    void submitFeedback_reviewNotFound_shouldThrow() {
        when(reviewService.findById("nonexist")).thenReturn(Optional.empty());

        RiskFeedbackRequest req = new RiskFeedbackRequest();
        req.setRiskIndex(0);
        req.setFeedback(FeedbackType.CONFIRMED);

        BusinessException ex = assertThrows(BusinessException.class, () ->
                riskFeedbackService.submitFeedback("nonexist", 1L, req));
        assertEquals(ResultCode.NOT_FOUND, ex.getResultCode());
    }

    // ========== overview 误报率计算测试 ==========

    @Test
    @DisplayName("无反馈时误报率为 0")
    void overview_noFeedback_rateIsZero() {
        when(feedbackMapper.selectList(any())).thenReturn(List.of());

        FeedbackOverview overview = riskFeedbackService.overview(1L);

        assertEquals(0, overview.getTotalFeedbacks());
        assertEquals(0.0, overview.getFalsePositiveRate());
    }

    @Test
    @DisplayName("误报率计算：2 个误报 / 5 个总反馈 = 0.4")
    void overview_calculatesRateCorrectly() {
        List<RiskFeedback> feedbacks = List.of(
                feedback(FeedbackType.FALSE_POSITIVE),
                feedback(FeedbackType.FALSE_POSITIVE),
                feedback(FeedbackType.CONFIRMED),
                feedback(FeedbackType.CONFIRMED),
                feedback(FeedbackType.CONFIRMED)
        );
        when(feedbackMapper.selectList(any())).thenReturn(feedbacks);

        FeedbackOverview overview = riskFeedbackService.overview(1L);

        assertEquals(5, overview.getTotalFeedbacks());
        assertEquals(2, overview.getFalsePositiveCount());
        assertEquals(3, overview.getConfirmedCount());
        assertEquals(0.4, overview.getFalsePositiveRate());
    }

    @Test
    @DisplayName("全部误报时误报率为 1.0")
    void overview_allFalsePositive_rateIsOne() {
        List<RiskFeedback> feedbacks = List.of(
                feedback(FeedbackType.FALSE_POSITIVE),
                feedback(FeedbackType.FALSE_POSITIVE)
        );
        when(feedbackMapper.selectList(any())).thenReturn(feedbacks);

        FeedbackOverview overview = riskFeedbackService.overview(1L);

        assertEquals(1.0, overview.getFalsePositiveRate());
    }

    // ========== getStats 测试 ==========

    @Test
    @DisplayName("getStats 应按 riskIndex 分组统计")
    void getStats_groupsByRiskIndex() {
        List<RiskFeedback> feedbacks = List.of(
                feedbackWithIndex(0, 1L, FeedbackType.CONFIRMED),
                feedbackWithIndex(0, 2L, FeedbackType.FALSE_POSITIVE),
                feedbackWithIndex(1, 1L, FeedbackType.CONFIRMED)
        );
        when(feedbackMapper.selectList(any())).thenReturn(feedbacks);

        List<RiskFeedbackStat> stats = riskFeedbackService.getStats("r1", 1L);

        assertEquals(2, stats.size());
        // riskIndex 0: 1 confirmed + 1 false_positive
        RiskFeedbackStat stat0 = stats.stream().filter(s -> s.getRiskIndex() == 0).findFirst().orElseThrow();
        assertEquals(1, stat0.getFalsePositiveCount());
        assertEquals(1, stat0.getConfirmedCount());
        assertEquals("CONFIRMED", stat0.getMyFeedback()); // userId=1L 的反馈

        // riskIndex 1: 1 confirmed
        RiskFeedbackStat stat1 = stats.stream().filter(s -> s.getRiskIndex() == 1).findFirst().orElseThrow();
        assertEquals(0, stat1.getFalsePositiveCount());
        assertEquals(1, stat1.getConfirmedCount());
    }

    private RiskFeedback feedback(FeedbackType type) {
        return RiskFeedback.builder().feedback(type).userId(1L).build();
    }

    private RiskFeedback feedbackWithIndex(int index, Long userId, FeedbackType type) {
        return RiskFeedback.builder()
                .riskIndex(index)
                .userId(userId)
                .feedback(type)
                .build();
    }
}
