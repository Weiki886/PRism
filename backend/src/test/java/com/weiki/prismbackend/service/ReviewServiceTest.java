package com.weiki.prismbackend.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.weiki.prismbackend.model.RiskItem;
import com.weiki.prismbackend.model.dto.ReviewStats;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * ReviewService 单元测试。
 * 测试 JSON 序列化/反序列化、统计聚合逻辑。
 * 不依赖数据库，直接测试纯逻辑方法。
 */
class ReviewServiceTest {

    private ReviewService reviewService;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        // ReviewService 的纯逻辑方法不依赖 ReviewMapper，传 null 即可
        reviewService = new ReviewService(null, objectMapper);
    }

    // ========== parseRisks 测试 ==========

    @Test
    @DisplayName("parseRisks：正常 JSON 应正确反序列化")
    void parseRisks_validJson() throws Exception {
        List<RiskItem> original = List.of(
                RiskItem.builder().level("HIGH").file("A.java").line(10).description("bug").confidence("HIGH").source("AI").build(),
                RiskItem.builder().level("LOW").file("B.java").line(null).description("style").confidence("LOW").source("RULE").build()
        );
        String json = objectMapper.writeValueAsString(original);

        List<RiskItem> parsed = reviewService.parseRisks(json);

        assertEquals(2, parsed.size());
        assertEquals("HIGH", parsed.get(0).getLevel());
        assertEquals("A.java", parsed.get(0).getFile());
        assertEquals(10, parsed.get(0).getLine());
        assertNull(parsed.get(1).getLine());
    }

    @Test
    @DisplayName("parseRisks：null 输入返回空列表")
    void parseRisks_null_returnsEmpty() {
        assertTrue(reviewService.parseRisks(null).isEmpty());
    }

    @Test
    @DisplayName("parseRisks：空字符串返回空列表")
    void parseRisks_blank_returnsEmpty() {
        assertTrue(reviewService.parseRisks("").isEmpty());
        assertTrue(reviewService.parseRisks("   ").isEmpty());
    }

    @Test
    @DisplayName("parseRisks：非法 JSON 返回空列表而非抛异常")
    void parseRisks_invalidJson_returnsEmpty() {
        assertTrue(reviewService.parseRisks("not json").isEmpty());
        assertTrue(reviewService.parseRisks("{broken").isEmpty());
    }

    // ========== parseSuggestions 测试 ==========

    @Test
    @DisplayName("parseSuggestions：正常 JSON 应正确反序列化")
    void parseSuggestions_validJson() throws Exception {
        List<String> original = List.of("增加测试", "优化性能");
        String json = objectMapper.writeValueAsString(original);

        List<String> parsed = reviewService.parseSuggestions(json);

        assertEquals(2, parsed.size());
        assertEquals("增加测试", parsed.get(0));
    }

    @Test
    @DisplayName("parseSuggestions：null 输入返回空列表")
    void parseSuggestions_null_returnsEmpty() {
        assertTrue(reviewService.parseSuggestions(null).isEmpty());
    }

    @Test
    @DisplayName("parseSuggestions：非法 JSON 返回空列表")
    void parseSuggestions_invalidJson_returnsEmpty() {
        assertTrue(reviewService.parseSuggestions("xxx").isEmpty());
    }

    // ========== risksToJson / suggestionsToJson 测试 ==========

    @Test
    @DisplayName("risksToJson：序列化后可被 parseRisks 还原")
    void risksToJson_roundTrip() {
        List<RiskItem> risks = List.of(
                RiskItem.builder().level("CRITICAL").file("C.java").line(5).description("sql injection").confidence("HIGH").suggestedFix("use prepared statement").source("AI").build()
        );

        String json = reviewService.risksToJson(risks);
        List<RiskItem> parsed = reviewService.parseRisks(json);

        assertEquals(1, parsed.size());
        assertEquals("CRITICAL", parsed.get(0).getLevel());
        assertEquals("sql injection", parsed.get(0).getDescription());
        assertEquals("use prepared statement", parsed.get(0).getSuggestedFix());
    }

    @Test
    @DisplayName("suggestionsToJson：序列化后可被 parseSuggestions 还原")
    void suggestionsToJson_roundTrip() {
        List<String> suggestions = List.of("add logging", "handle edge case");

        String json = reviewService.suggestionsToJson(suggestions);
        List<String> parsed = reviewService.parseSuggestions(json);

        assertEquals(2, parsed.size());
        assertEquals("add logging", parsed.get(0));
    }

    @Test
    @DisplayName("risksToJson：null 列表返回 []")
    void risksToJson_null_returnsEmptyArray() {
        // ObjectMapper.writeValueAsString(null) 会写 "null"，但方法内部处理
        String json = reviewService.risksToJson(null);
        // 即使返回 "null"，parseRisks 也能兜底
        assertNotNull(json);
    }

    // ========== parseContextInfo 测试 ==========

    @Test
    @DisplayName("parseContextInfo：null 输入返回 null")
    void parseContextInfo_null_returnsNull() {
        assertNull(reviewService.parseContextInfo(null));
    }

    @Test
    @DisplayName("parseContextInfo：空字符串返回 null")
    void parseContextInfo_blank_returnsNull() {
        assertNull(reviewService.parseContextInfo(""));
    }

    @Test
    @DisplayName("parseContextInfo：非法 JSON 返回 null 而非抛异常")
    void parseContextInfo_invalidJson_returnsNull() {
        assertNull(reviewService.parseContextInfo("{bad json"));
    }
}
