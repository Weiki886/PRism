package com.weiki.prismbackend.service;

import com.weiki.prismbackend.model.RiskItem;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * HealthScoreCalculator 单元测试。
 * 纯逻辑类，不依赖 Spring 容器。
 */
class HealthScoreCalculatorTest {

    @Test
    @DisplayName("无风险时健康分为满分 100")
    void noRisks_shouldReturn100() {
        assertEquals(100, HealthScoreCalculator.calculate(null));
        assertEquals(100, HealthScoreCalculator.calculate(List.of()));
    }

    @Test
    @DisplayName("单个 CRITICAL 风险扣 25 分")
    void singleCritical_shouldDeduct25() {
        List<RiskItem> risks = List.of(risk("CRITICAL"));
        assertEquals(75, HealthScoreCalculator.calculate(risks));
    }

    @Test
    @DisplayName("单个 HIGH 风险扣 15 分")
    void singleHigh_shouldDeduct15() {
        List<RiskItem> risks = List.of(risk("HIGH"));
        assertEquals(85, HealthScoreCalculator.calculate(risks));
    }

    @Test
    @DisplayName("单个 MEDIUM 风险扣 7 分")
    void singleMedium_shouldDeduct7() {
        List<RiskItem> risks = List.of(risk("MEDIUM"));
        assertEquals(93, HealthScoreCalculator.calculate(risks));
    }

    @Test
    @DisplayName("单个 LOW 风险扣 2 分")
    void singleLow_shouldDeduct2() {
        List<RiskItem> risks = List.of(risk("LOW"));
        assertEquals(98, HealthScoreCalculator.calculate(risks));
    }

    @Test
    @DisplayName("混合风险累计扣分：1 MEDIUM + 1 LOW = 91")
    void mixedRisks_shouldAccumulate() {
        List<RiskItem> risks = List.of(risk("MEDIUM"), risk("LOW"));
        assertEquals(91, HealthScoreCalculator.calculate(risks));
    }

    @Test
    @DisplayName("大量风险时分数不低于 0")
    void manyRisks_shouldNotGoBelowZero() {
        List<RiskItem> risks = List.of(
                risk("CRITICAL"), risk("CRITICAL"), risk("CRITICAL"),
                risk("CRITICAL"), risk("CRITICAL")
        );
        assertEquals(0, HealthScoreCalculator.calculate(risks));
    }

    @Test
    @DisplayName("未知等级按 MEDIUM 扣分")
    void unknownLevel_shouldDeductAsMedium() {
        List<RiskItem> risks = List.of(risk("UNKNOWN"));
        assertEquals(93, HealthScoreCalculator.calculate(risks));
    }

    @Test
    @DisplayName("合并建议：≥80 推荐合并")
    void mergeAdvice_recommend() {
        assertEquals("RECOMMEND", HealthScoreCalculator.mergeAdvice(100));
        assertEquals("RECOMMEND", HealthScoreCalculator.mergeAdvice(80));
    }

    @Test
    @DisplayName("合并建议：50-79 谨慎合并")
    void mergeAdvice_caution() {
        assertEquals("CAUTION", HealthScoreCalculator.mergeAdvice(79));
        assertEquals("CAUTION", HealthScoreCalculator.mergeAdvice(50));
    }

    @Test
    @DisplayName("合并建议：<50 不推荐合并")
    void mergeAdvice_notRecommend() {
        assertEquals("NOT_RECOMMEND", HealthScoreCalculator.mergeAdvice(49));
        assertEquals("NOT_RECOMMEND", HealthScoreCalculator.mergeAdvice(0));
    }

    private RiskItem risk(String level) {
        return RiskItem.builder().level(level).build();
    }
}
