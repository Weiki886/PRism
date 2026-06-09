package com.weiki.prismbackend.service;

import java.util.List;

import com.weiki.prismbackend.model.RiskItem;
import com.weiki.prismbackend.model.entity.Review;

/**
 * 审查结果评论格式化器。
 * 将审查结果格式化为 GitHub PR Comment 的 Markdown 正文。
 */
public final class ReviewCommentFormatter {

    private ReviewCommentFormatter() {
    }

    /**
     * 格式化审查结果为 GitHub Comment 正文。
     */
    public static String format(Review review, List<RiskItem> risks,
                                List<String> suggestions, int healthScore, String mergeAdvice) {
        StringBuilder sb = new StringBuilder();

        sb.append("## PRism Code Review\n\n");

        // 健康分 & 合并建议
        sb.append("| 健康分 | 合并建议 |\n");
        sb.append("|--------|----------|\n");
        sb.append("| **").append(healthScore).append(" / 100** | **").append(adviceLabel(mergeAdvice)).append("** |\n\n");

        // 变更摘要
        sb.append("### 变更摘要\n\n");
        sb.append(nullSafe(review.getSummary())).append("\n\n");

        // 风险项表格
        if (!risks.isEmpty()) {
            sb.append("### 风险项 (").append(risks.size()).append(")\n\n");
            sb.append("| # | 等级 | 置信度 | 文件 | 描述 |\n");
            sb.append("|---|------|--------|------|------|\n");
            for (int i = 0; i < risks.size(); i++) {
                RiskItem r = risks.get(i);
                sb.append("| ").append(i + 1)
                        .append(" | ").append(nullSafe(r.getLevel()))
                        .append(" | ").append(nullSafe(r.getConfidence()))
                        .append(" | `").append(nullSafe(r.getFile())).append("`");
                if (r.getLine() != null) {
                    sb.append(" (L").append(r.getLine()).append(")");
                }
                sb.append(" | ").append(nullSafe(r.getDescription())).append(" |\n");

                if (r.getSuggestedFix() != null && !r.getSuggestedFix().isBlank()) {
                    String fix = r.getSuggestedFix().replace("|", "\\|").replace("\n", " ");
                    sb.append("| | | | | Suggested fix: `").append(truncate(fix, 120)).append("` |\n");
                }
            }
            sb.append("\n");
        } else {
            sb.append("### 风险项\n\n");
            sb.append("No obvious risks detected.\n\n");
        }

        // 改进建议
        if (!suggestions.isEmpty()) {
            sb.append("### 改进建议\n\n");
            for (String s : suggestions) {
                sb.append("- ").append(s).append("\n");
            }
            sb.append("\n");
        }

        sb.append("---\n");
        sb.append("*Powered by [PRism](https://github.com/Weiki886/PRism) AI Code Review*\n");
        return sb.toString();
    }

    private static String nullSafe(String s) {
        return s == null ? "-" : s;
    }

    private static String truncate(String s, int maxLen) {
        if (s == null) return "-";
        return s.length() <= maxLen ? s : s.substring(0, maxLen) + "...";
    }

    private static String adviceLabel(String advice) {
        if (advice == null) return "-";
        return switch (advice) {
            case "RECOMMEND" -> "RECOMMEND (Recommended to merge)";
            case "CAUTION" -> "CAUTION (Merge with caution)";
            case "NOT_RECOMMEND" -> "NOT RECOMMENDED (Significant issues)";
            default -> advice;
        };
    }
}
