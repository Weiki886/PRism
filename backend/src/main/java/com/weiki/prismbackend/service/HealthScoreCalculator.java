package com.weiki.prismbackend.service;

import com.weiki.prismbackend.model.RiskItem;

import java.util.List;

/**
 * PR 健康分计算器。
 * 基于风险数量和等级用确定性规则计算 0-100 的健康分，并给出合并建议。
 * 采用规则计算而非 AI 打分，保证结果稳定、可复现、可解释。
 */
public final class HealthScoreCalculator {

    // 各等级扣分权重
    private static final int CRITICAL_PENALTY = 25;
    private static final int HIGH_PENALTY = 15;
    private static final int MEDIUM_PENALTY = 7;
    private static final int LOW_PENALTY = 2;

    // 合并建议阈值
    private static final int RECOMMEND_THRESHOLD = 80;
    private static final int CAUTION_THRESHOLD = 50;

    public static final String ADVICE_RECOMMEND = "RECOMMEND";
    public static final String ADVICE_CAUTION = "CAUTION";
    public static final String ADVICE_NOT_RECOMMEND = "NOT_RECOMMEND";

    private HealthScoreCalculator() {
    }

    /**
     * 根据风险列表计算健康分（0-100）。无风险时为满分 100。
     */
    public static int calculate(List<RiskItem> risks) {
        if (risks == null || risks.isEmpty()) {
            return 100;
        }
        int penalty = 0;
        for (RiskItem risk : risks) {
            penalty += penaltyOf(risk.getLevel());
        }
        return Math.max(0, 100 - penalty);
    }

    private static int penaltyOf(String level) {
        if (level == null) {
            return MEDIUM_PENALTY;
        }
        return switch (level.toUpperCase()) {
            case "CRITICAL" -> CRITICAL_PENALTY;
            case "HIGH" -> HIGH_PENALTY;
            case "MEDIUM" -> MEDIUM_PENALTY;
            case "LOW" -> LOW_PENALTY;
            default -> MEDIUM_PENALTY;
        };
    }

    /**
     * 根据健康分给出合并建议。
     */
    public static String mergeAdvice(int score) {
        if (score >= RECOMMEND_THRESHOLD) {
            return ADVICE_RECOMMEND;
        } else if (score >= CAUTION_THRESHOLD) {
            return ADVICE_CAUTION;
        } else {
            return ADVICE_NOT_RECOMMEND;
        }
    }
}
