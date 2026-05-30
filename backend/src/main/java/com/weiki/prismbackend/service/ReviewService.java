package com.weiki.prismbackend.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.weiki.prismbackend.mapper.ReviewMapper;
import com.weiki.prismbackend.model.RiskItem;
import com.weiki.prismbackend.model.entity.Review;
import org.springframework.stereotype.Service;

import java.util.List;
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
