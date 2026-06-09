package com.weiki.prismbackend.service;

import com.weiki.prismbackend.model.RiskItem;
import com.weiki.prismbackend.model.entity.Review;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ReviewCommentFormatterTest {

    private static Review sampleReview() {
        return Review.builder()
                .prTitle("Fix login timeout")
                .author("octocat")
                .ghRepo("owner/repo")
                .prUrl("https://github.com/owner/repo/pull/42")
                .summary("Fixed session timeout by extending TTL to 30 minutes.")
                .build();
    }

    @Test
    @DisplayName("无风险时评论应包含 heading 和摘要")
    void noRisks_shouldHaveHeadingAndSummary() {
        var review = sampleReview();
        review.setSummary("Minor refactoring.");

        String result = ReviewCommentFormatter.format(review, List.of(), List.of(), 100, "RECOMMEND");

        assertTrue(result.contains("## PRism Code Review"));
        assertTrue(result.contains("100 / 100"));
        assertTrue(result.contains("RECOMMEND"));
        assertTrue(result.contains("Minor refactoring."));
        assertTrue(result.contains("No obvious risks detected"));
        assertTrue(result.contains("PRism"));
    }

    @Test
    @DisplayName("有风险项时评论应包含风险表格")
    void withRisks_shouldIncludeRiskTable() {
        var review = sampleReview();
        var risks = List.of(
                RiskItem.builder()
                        .level("CRITICAL")
                        .file("src/auth/Login.java")
                        .line(42)
                        .description("SQL injection risk")
                        .confidence("HIGH")
                        .suggestedFix("Use PreparedStatement")
                        .build(),
                RiskItem.builder()
                        .level("LOW")
                        .file("src/util/Helper.java")
                        .line(null)
                        .description("Unused import")
                        .confidence("HIGH")
                        .suggestedFix(null)
                        .build()
        );

        String result = ReviewCommentFormatter.format(review, risks, List.of(), 68, "CAUTION");

        assertTrue(result.contains("68 / 100"));
        assertTrue(result.contains("CAUTION"));
        assertTrue(result.contains("CRITICAL"));
        assertTrue(result.contains("SQL injection"));
        assertTrue(result.contains("src/auth/Login.java"));
        assertTrue(result.contains("(L42)"));
        assertTrue(result.contains("Suggested fix"));
        assertTrue(result.contains("LOW"));
        assertTrue(result.contains("Unused import"));
        assertFalse(result.contains("(Lnull)"));
    }

    @Test
    @DisplayName("有改进建议时评论应包含建议列表")
    void withSuggestions_shouldIncludeSuggestionList() {
        var review = sampleReview();
        var suggestions = List.of(
                "Add unit tests for session management",
                "Log timeout events"
        );

        String result = ReviewCommentFormatter.format(review, List.of(), suggestions, 100, "RECOMMEND");

        assertTrue(result.contains("Add unit tests for session management"));
        assertTrue(result.contains("Log timeout events"));
    }

    @Test
    @DisplayName("合并建议 NOT_RECOMMEND 应正确转换")
    void notRecommendAdvice_shouldShowNotRecommended() {
        var review = sampleReview();

        String result = ReviewCommentFormatter.format(review, List.of(), List.of(), 30, "NOT_RECOMMEND");

        assertTrue(result.contains("30 / 100"));
        assertTrue(result.contains("NOT RECOMMENDED"));
    }

    @Test
    @DisplayName("suggestedFix 含管道符时应转义")
    void suggestedFix_withPipe_shouldBeEscaped() {
        var review = sampleReview();
        var risks = List.of(
                RiskItem.builder()
                        .level("MEDIUM")
                        .file("test.java")
                        .line(1)
                        .description("foo | bar")
                        .confidence("MEDIUM")
                        .suggestedFix("replace x | y with z")
                        .build()
        );

        String result = ReviewCommentFormatter.format(review, risks, List.of(), 93, "RECOMMEND");

        assertTrue(result.contains("Suggested fix"));
        // pipe should be escaped so it doesn't break markdown table
        assertDoesNotThrow(() -> result.contains("\\|"));
    }

    @Test
    @DisplayName("评论末尾应包含 PRism 链接")
    void shouldContainPrismFooter() {
        var review = sampleReview();

        String result = ReviewCommentFormatter.format(review, List.of(), List.of(), 100, "RECOMMEND");

        assertTrue(result.contains("[PRism](https://github.com/Weiki886/PRism)"));
    }
}
