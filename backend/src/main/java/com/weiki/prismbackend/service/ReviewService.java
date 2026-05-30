package com.weiki.prismbackend.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.weiki.prismbackend.mapper.ReviewMapper;
import com.weiki.prismbackend.model.RiskItem;
import com.weiki.prismbackend.model.dto.ReviewStats;
import com.weiki.prismbackend.model.entity.Review;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
public class ReviewService {

    private final ReviewMapper reviewMapper;
    private final ObjectMapper objectMapper;

    public ReviewService(ReviewMapper reviewMapper, ObjectMapper objectMapper) {
        this.reviewMapper = reviewMapper;
        this.objectMapper = objectMapper;
    }

    public void saveReview(Review review) {
        reviewMapper.insert(review);
    }

    public void updateReview(Review review) {
        reviewMapper.updateById(review);
    }

    public Optional<Review> findById(String id) {
        return Optional.ofNullable(reviewMapper.selectById(id));
    }

    /**
     * 按 id + userId 查询，用于需要校验记录归属的场景。
     *
     * @return 记录存在且属于该用户时返回，否则为空
     */
    public Optional<Review> findByIdAndUser(String id, Long userId) {
        LambdaQueryWrapper<Review> wrapper = new LambdaQueryWrapper<Review>()
                .eq(Review::getId, id)
                .eq(Review::getUserId, userId);
        return Optional.ofNullable(reviewMapper.selectOne(wrapper));
    }

    /**
     * 删除指定用户的某条审查记录。
     * 通过 id + userId 双条件删除，确保用户只能删除自己的记录。
     *
     * @return 是否删除成功（记录不存在或不属于该用户时返回 false）
     */
    public boolean deleteByIdAndUser(String id, Long userId) {
        LambdaQueryWrapper<Review> wrapper = new LambdaQueryWrapper<Review>()
                .eq(Review::getId, id)
                .eq(Review::getUserId, userId);
        return reviewMapper.delete(wrapper) > 0;
    }

    public List<Review> findByUserId(Long userId, int page, int size) {
        Page<Review> p = new Page<>(page, size);
        LambdaQueryWrapper<Review> wrapper = new LambdaQueryWrapper<Review>()
                .eq(Review::getUserId, userId)
                .orderByDesc(Review::getCreatedAt);
        Page<Review> result = reviewMapper.selectPage(p, wrapper);
        return result.getRecords();
    }

    public long countByUserId(Long userId) {
        return reviewMapper.selectCount(
                new LambdaQueryWrapper<Review>().eq(Review::getUserId, userId)
        );
    }

    /**
     * 统计指定用户的评审概览：记录数、状态分布、累计风险数及各等级分布。
     */
    public ReviewStats statsByUserId(Long userId) {
        List<Review> reviews = reviewMapper.selectList(
                new LambdaQueryWrapper<Review>().eq(Review::getUserId, userId));

        long completed = 0, error = 0, processing = 0, totalRisks = 0;

        // 固定四个等级，保证返回结构完整（即使某等级数量为 0）
        Map<String, Long> levelDist = new LinkedHashMap<>();
        levelDist.put("CRITICAL", 0L);
        levelDist.put("HIGH", 0L);
        levelDist.put("MEDIUM", 0L);
        levelDist.put("LOW", 0L);

        for (Review r : reviews) {
            switch (r.getStatus() == null ? "" : r.getStatus()) {
                case "completed" -> completed++;
                case "error" -> error++;
                case "pending", "processing" -> processing++;
                default -> { }
            }

            for (RiskItem risk : parseRisks(r.getRisksJson())) {
                totalRisks++;
                String level = risk.getLevel();
                if (level != null && levelDist.containsKey(level)) {
                    levelDist.merge(level, 1L, Long::sum);
                }
            }
        }

        return ReviewStats.builder()
                .totalReviews(reviews.size())
                .completedReviews(completed)
                .errorReviews(error)
                .processingReviews(processing)
                .totalRisks(totalRisks)
                .riskLevelDistribution(levelDist)
                .build();
    }

    public List<RiskItem> parseRisks(String risksJson) {
        if (risksJson == null || risksJson.isBlank()) return List.of();
        try {
            return objectMapper.readValue(risksJson, new TypeReference<>() {});
        } catch (Exception e) {
            return List.of();
        }
    }

    public List<String> parseSuggestions(String suggestionsJson) {
        if (suggestionsJson == null || suggestionsJson.isBlank()) return List.of();
        try {
            return objectMapper.readValue(suggestionsJson, new TypeReference<>() {});
        } catch (Exception e) {
            return List.of();
        }
    }

    public String risksToJson(List<RiskItem> risks) {
        try {
            return objectMapper.writeValueAsString(risks);
        } catch (Exception e) {
            return "[]";
        }
    }

    public String suggestionsToJson(List<String> suggestions) {
        try {
            return objectMapper.writeValueAsString(suggestions);
        } catch (Exception e) {
            return "[]";
        }
    }
}
