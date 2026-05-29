package com.weiki.prismbackend.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.weiki.prismbackend.common.ResultCode;
import com.weiki.prismbackend.exception.BusinessException;
import com.weiki.prismbackend.mapper.RiskFeedbackMapper;
import com.weiki.prismbackend.model.dto.RiskFeedbackRequest;
import com.weiki.prismbackend.model.dto.RiskFeedbackStat;
import com.weiki.prismbackend.model.entity.RiskFeedback;
import com.weiki.prismbackend.model.enums.FeedbackType;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 风险反馈服务：记录用户对 AI 识别出的风险的反馈（误报/确认），
 * 用于统计误报率，辅助评估和改进分析准确性。
 */
@Service
public class RiskFeedbackService {

    private final RiskFeedbackMapper feedbackMapper;
    private final ReviewService reviewService;

    public RiskFeedbackService(RiskFeedbackMapper feedbackMapper, ReviewService reviewService) {
        this.feedbackMapper = feedbackMapper;
        this.reviewService = reviewService;
    }

    /**
     * 提交反馈。同一用户对同一风险重复提交时更新已有记录。
     */
    public void submitFeedback(String reviewId, Long userId, RiskFeedbackRequest req) {
        // 校验 review 存在
        reviewService.findById(reviewId)
                .orElseThrow(() -> new BusinessException(ResultCode.NOT_FOUND));

        RiskFeedback existing = feedbackMapper.selectOne(new LambdaQueryWrapper<RiskFeedback>()
                .eq(RiskFeedback::getReviewId, reviewId)
                .eq(RiskFeedback::getRiskIndex, req.getRiskIndex())
                .eq(RiskFeedback::getUserId, userId));

        if (existing != null) {
            existing.setFeedback(req.getFeedback());
            existing.setComment(req.getComment() != null ? req.getComment() : "");
            feedbackMapper.updateById(existing);
        } else {
            RiskFeedback feedback = RiskFeedback.builder()
                    .reviewId(reviewId)
                    .riskIndex(req.getRiskIndex())
                    .userId(userId)
                    .feedback(req.getFeedback())
                    .comment(req.getComment() != null ? req.getComment() : "")
                    .build();
            feedbackMapper.insert(feedback);
        }
    }

    /**
     * 查询某个 review 下所有风险的反馈统计。
     */
    public List<RiskFeedbackStat> getStats(String reviewId, Long currentUserId) {
        List<RiskFeedback> all = feedbackMapper.selectList(new LambdaQueryWrapper<RiskFeedback>()
                .eq(RiskFeedback::getReviewId, reviewId));

        Map<Integer, List<RiskFeedback>> grouped = all.stream()
                .collect(Collectors.groupingBy(RiskFeedback::getRiskIndex));

        List<RiskFeedbackStat> stats = new ArrayList<>();
        for (Map.Entry<Integer, List<RiskFeedback>> entry : grouped.entrySet()) {
            List<RiskFeedback> list = entry.getValue();
            long fp = list.stream()
                    .filter(f -> f.getFeedback() == FeedbackType.FALSE_POSITIVE).count();
            long confirmed = list.stream()
                    .filter(f -> f.getFeedback() == FeedbackType.CONFIRMED).count();
            String myFeedback = list.stream()
                    .filter(f -> f.getUserId().equals(currentUserId))
                    .map(f -> f.getFeedback().getValue())
                    .findFirst().orElse(null);

            stats.add(RiskFeedbackStat.builder()
                    .riskIndex(entry.getKey())
                    .falsePositiveCount(fp)
                    .confirmedCount(confirmed)
                    .myFeedback(myFeedback)
                    .build());
        }
        return stats;
    }
}
