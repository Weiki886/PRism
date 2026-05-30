package com.weiki.prismbackend.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.weiki.prismbackend.model.ReviewResponse;
import com.weiki.prismbackend.model.RiskItem;
import com.weiki.prismbackend.model.dto.ContextInfo;
import com.weiki.prismbackend.model.entity.Review;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * PR 审查的异步处理器。
 * 拉取 GitHub 数据、调用 AI 分析等耗时操作在独立线程池中执行，
 * 通过更新 review 记录的 status 字段反映进度，供前端轮询。
 * <p>
 * 注意：@Async 方法必须由外部 Bean 调用才能生效（Spring AOP 代理），
 * 因此该逻辑独立成类，不与 ReviewController 内部方法耦合。
 */
@Slf4j
@Service
public class ReviewProcessor {

    private final GitHubService gitHubService;
    private final AiReviewService aiReviewService;
    private final ReviewService reviewService;
    private final ObjectMapper objectMapper;

    @Value("${spring.ai.openai.chat.options.model:qwen-max}")
    private String model;

    public ReviewProcessor(GitHubService gitHubService,
                           AiReviewService aiReviewService,
                           ReviewService reviewService,
                           ObjectMapper objectMapper) {
        this.gitHubService = gitHubService;
        this.aiReviewService = aiReviewService;
        this.reviewService = reviewService;
        this.objectMapper = objectMapper;
    }

    /**
     * 异步执行 PR 分析。整个过程的状态流转：
     * pending（创建时）→ processing → completed / error
     *
     * @param reviewId 已创建的 review 记录 ID
     * @param prUrl    GitHub PR 链接
     */
    @Async("reviewExecutor")
    public void process(String reviewId, String prUrl) {
        Review review = reviewService.findById(reviewId).orElse(null);
        if (review == null) {
            log.warn("待分析的 review 不存在: {}", reviewId);
            return;
        }

        try {
            // 标记为处理中
            review.setStatus("processing");
            reviewService.updateReview(review);

            // 拉取 PR 上下文
            Map<String, Object> prInfo = gitHubService.getPrInfo(prUrl);

            // AI 分析
            ReviewResponse result = aiReviewService.analyze(
                    (String) prInfo.get("title"),
                    (String) prInfo.get("body"),
                    (String) prInfo.get("diff"),
                    (String) prInfo.get("author"),
                    (String) prInfo.get("commitMessages"),
                    (String) prInfo.get("reviewComments"),
                    (String) prInfo.get("fileContexts")
            );

            // 静态规则扫描作为补充，与 AI 风险合并（规则保证确定性问题不漏报）
            List<RiskItem> mergedRisks = new ArrayList<>(result.getRisks());
            mergedRisks.addAll(StaticRuleScanner.scan((String) prInfo.get("diff")));

            // 收集本次分析使用的上下文信息，供前端透明化展示
            ContextInfo contextInfo = ContextInfo.builder()
                    .changedFiles((int) prInfo.getOrDefault("changedFiles", 0))
                    .diffTokens((int) prInfo.getOrDefault("diffTokens", 0))
                    .includedCommitMessages((boolean) prInfo.getOrDefault("hasCommitMessages", false))
                    .includedReviewComments((boolean) prInfo.getOrDefault("hasReviewComments", false))
                    .includedFileContexts((boolean) prInfo.getOrDefault("hasFileContexts", false))
                    .model(model)
                    .build();

            // 写回分析结果
            review.setPrTitle(result.getPrTitle());
            review.setAuthor(result.getAuthor());
            review.setSummary(result.getSummary());
            review.setRisksJson(reviewService.risksToJson(mergedRisks));
            review.setSuggestionsJson(reviewService.suggestionsToJson(result.getSuggestions()));
            review.setContextInfoJson(toJson(contextInfo));
            review.setStatus(result.getStatus());
            review.setGhRepo((String) prInfo.get("repo"));
            review.setGhPrNumber((String) prInfo.get("prNumber"));
            reviewService.updateReview(review);

            log.info("PR 分析完成: {}", reviewId);

        } catch (Exception e) {
            log.error("PR 分析失败: {} - {}", reviewId, e.getMessage(), e);
            review.setStatus("error");
            review.setSummary("分析失败：" + e.getMessage());
            reviewService.updateReview(review);
        }
    }

    private String toJson(ContextInfo contextInfo) {
        try {
            return objectMapper.writeValueAsString(contextInfo);
        } catch (Exception e) {
            log.warn("序列化 ContextInfo 失败: {}", e.getMessage());
            return null;
        }
    }
}
