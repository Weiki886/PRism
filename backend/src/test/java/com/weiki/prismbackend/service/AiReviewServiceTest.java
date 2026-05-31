package com.weiki.prismbackend.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.weiki.prismbackend.model.ReviewResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.client.ChatClient;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * AiReviewService 单元测试。
 * mock ChatClient，验证 prompt 构建正确性、JSON 解析健壮性、异常降级逻辑。
 */
@ExtendWith(MockitoExtension.class)
class AiReviewServiceTest {

    @Mock
    private ChatClient.Builder chatClientBuilder;
    @Mock
    private ChatClient chatClient;
    @Mock
    private ChatClient.ChatClientRequestSpec requestSpec;
    @Mock
    private ChatClient.CallResponseSpec callResponseSpec;

    private AiReviewService aiReviewService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        when(chatClientBuilder.defaultSystem(anyString())).thenReturn(chatClientBuilder);
        when(chatClientBuilder.build()).thenReturn(chatClient);
        aiReviewService = new AiReviewService(chatClientBuilder, objectMapper);
    }

    private void mockChatResponse(String content) {
        when(chatClient.prompt()).thenReturn(requestSpec);
        when(requestSpec.user(any(String.class))).thenReturn(requestSpec);
        when(requestSpec.call()).thenReturn(callResponseSpec);
        when(callResponseSpec.content()).thenReturn(content);
    }

    // ========== JSON 解析测试 ==========

    @Test
    @DisplayName("正常 JSON 响应应正确解析为 ReviewResponse")
    void analyze_validJson_shouldParse() {
        String json = """
                {
                  "summary": "修复了登录超时问题",
                  "risks": [
                    {
                      "level": "HIGH",
                      "file": "src/auth/Login.java",
                      "line": 42,
                      "description": "未处理空指针",
                      "confidence": "HIGH",
                      "suggestedFix": "if (user != null) { ... }"
                    }
                  ],
                  "suggestions": ["增加单元测试", "添加日志"]
                }
                """;
        mockChatResponse(json);

        ReviewResponse result = aiReviewService.analyze("fix login", "desc", "diff", "author");

        assertEquals("修复了登录超时问题", result.getSummary());
        assertEquals(1, result.getRisks().size());
        assertEquals("HIGH", result.getRisks().get(0).getLevel());
        assertEquals(42, result.getRisks().get(0).getLine());
        assertEquals("AI", result.getRisks().get(0).getSource());
        assertEquals(2, result.getSuggestions().size());
        assertEquals("completed", result.getStatus());
    }

    @Test
    @DisplayName("带 markdown 代码块包裹的 JSON 也能正确解析")
    void analyze_markdownWrappedJson_shouldParse() {
        String json = """
                ```json
                {
                  "summary": "重构了配置模块",
                  "risks": [],
                  "suggestions": ["考虑添加文档"]
                }
                ```
                """;
        mockChatResponse(json);

        ReviewResponse result = aiReviewService.analyze("refactor config", null, "diff", "dev");

        assertEquals("重构了配置模块", result.getSummary());
        assertEquals(0, result.getRisks().size());
        assertEquals(1, result.getSuggestions().size());
        assertEquals("completed", result.getStatus());
    }

    @Test
    @DisplayName("risk 中 line 为 null 时应正确处理")
    void analyze_nullLine_shouldHandleGracefully() {
        String json = """
                {
                  "summary": "test",
                  "risks": [{"level": "LOW", "file": "a.java", "line": null, "description": "style", "confidence": "LOW", "suggestedFix": null}],
                  "suggestions": []
                }
                """;
        mockChatResponse(json);

        ReviewResponse result = aiReviewService.analyze("test", "", "diff", "a");

        assertNull(result.getRisks().get(0).getLine());
        assertEquals("LOW", result.getRisks().get(0).getConfidence());
    }

    @Test
    @DisplayName("risk 中缺少 confidence 字段时默认为 MEDIUM")
    void analyze_missingConfidence_defaultsMedium() {
        String json = """
                {
                  "summary": "test",
                  "risks": [{"level": "MEDIUM", "file": "b.java", "line": 10, "description": "perf issue"}],
                  "suggestions": []
                }
                """;
        mockChatResponse(json);

        ReviewResponse result = aiReviewService.analyze("test", "", "diff", "a");

        assertEquals("MEDIUM", result.getRisks().get(0).getConfidence());
    }

    // ========== 异常降级测试 ==========

    @Test
    @DisplayName("AI 返回非 JSON 内容时应降级为 error 状态")
    void analyze_invalidJson_shouldFallbackToError() {
        mockChatResponse("I cannot analyze this code because...");

        ReviewResponse result = aiReviewService.analyze("test", "", "diff", "a");

        assertEquals("error", result.getStatus());
        assertTrue(result.getSummary().contains("解析失败"));
        assertTrue(result.getRisks().isEmpty());
    }

    @Test
    @DisplayName("AI 返回空字符串时应降级为 error 状态")
    void analyze_emptyResponse_shouldFallbackToError() {
        mockChatResponse("");

        ReviewResponse result = aiReviewService.analyze("test", "", "diff", "a");

        assertEquals("error", result.getStatus());
    }

    // ========== Prompt 构建测试 ==========

    @Test
    @DisplayName("有增强上下文时 prompt 应包含 commit 历史和评论")
    void analyze_withContext_shouldIncludeInPrompt() {
        String json = """
                {"summary": "ok", "risks": [], "suggestions": []}
                """;
        mockChatResponse(json);

        aiReviewService.analyze("title", "body", "diff", "author",
                "- fix: commit msg", "reviewer: looks good", "class Foo {}");

        ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
        verify(requestSpec).user(captor.capture());
        String prompt = captor.getValue();
        assertTrue(prompt.contains("Commit 历史"));
        assertTrue(prompt.contains("fix: commit msg"));
        assertTrue(prompt.contains("已有评论"));
        assertTrue(prompt.contains("looks good"));
        assertTrue(prompt.contains("修改文件完整内容"));
    }

    @Test
    @DisplayName("无增强上下文时 prompt 不包含可选段落")
    void analyze_withoutContext_shouldOmitOptionalSections() {
        String json = """
                {"summary": "ok", "risks": [], "suggestions": []}
                """;
        mockChatResponse(json);

        aiReviewService.analyze("title", "body", "diff", "author", null, null, null);

        ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
        verify(requestSpec).user(captor.capture());
        String prompt = captor.getValue();
        assertFalse(prompt.contains("Commit 历史"));
        assertFalse(prompt.contains("已有评论"));
        assertFalse(prompt.contains("修改文件完整内容"));
    }

    @Test
    @DisplayName("PR 描述为 null 时 prompt 中显示（无描述）")
    void analyze_nullBody_shouldShowPlaceholder() {
        String json = """
                {"summary": "ok", "risks": [], "suggestions": []}
                """;
        mockChatResponse(json);

        aiReviewService.analyze("title", null, "diff", "author");

        ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
        verify(requestSpec).user(captor.capture());
        assertTrue(captor.getValue().contains("（无描述）"));
    }
}
