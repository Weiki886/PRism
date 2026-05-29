package com.weiki.prismbackend.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.weiki.prismbackend.model.ReviewResponse;
import com.weiki.prismbackend.model.RiskItem;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class AiReviewService {

    private static final String SYSTEM_PROMPT = """
            你是一个专业的代码审查专家。分析 GitHub PR 的代码变更，以 JSON 格式返回审查结果。
            只返回 JSON，不要有任何其他内容或 markdown 代码块。

            返回格式：
            {
              "summary": "对本次 PR 变更的简洁摘要，2-3句话",
              "risks": [
                {
                  "level": "CRITICAL|HIGH|MEDIUM|LOW",
                  "file": "文件路径",
                  "line": 行号整数或null,
                  "description": "风险描述"
                }
              ],
              "suggestions": ["改进建议1", "改进建议2"]
            }

            风险等级：CRITICAL=安全漏洞，HIGH=逻辑错误/数据丢失，MEDIUM=性能/规范，LOW=可读性/命名
            """;

    private final ChatClient chatClient;
    private final ObjectMapper objectMapper;

    public AiReviewService(ChatClient.Builder builder, ObjectMapper objectMapper) {
        this.chatClient = builder.defaultSystem(SYSTEM_PROMPT).build();
        this.objectMapper = objectMapper;
    }

    public ReviewResponse analyze(String prTitle, String prBody, String diff, String author) {
        String userPrompt = "PR 标题：" + prTitle + "\nPR 描述：" + (prBody != null ? prBody : "（无描述）")
                + "\n\n代码变更：\n" + diff;

        String content = chatClient.prompt()
                .user(userPrompt)
                .call()
                .content();

        return parseResponse(content, prTitle, author);
    }

    @SuppressWarnings("unchecked")
    private ReviewResponse parseResponse(String content, String prTitle, String author) {
        try {
            String json = content.trim();
            if (json.startsWith("```")) {
                json = json.replaceAll("(?s)^```[a-z]*\\n?", "").replaceAll("```\\s*$", "").trim();
            }

            Map<String, Object> parsed = objectMapper.readValue(json, new TypeReference<>() {});

            List<RiskItem> risks = new ArrayList<>();
            List<Map<String, Object>> rawRisks = (List<Map<String, Object>>) parsed.getOrDefault("risks", List.of());
            for (Map<String, Object> r : rawRisks) {
                risks.add(RiskItem.builder()
                        .level((String) r.get("level"))
                        .file((String) r.get("file"))
                        .line(r.get("line") instanceof Number n ? n.intValue() : null)
                        .description((String) r.get("description"))
                        .build());
            }

            List<String> suggestions = (List<String>) parsed.getOrDefault("suggestions", List.of());

            return ReviewResponse.builder()
                    .id("review_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12))
                    .prTitle(prTitle)
                    .author(author)
                    .summary((String) parsed.getOrDefault("summary", "分析完成"))
                    .risks(risks)
                    .suggestions(suggestions)
                    .status("completed")
                    .build();

        } catch (Exception e) {
            return ReviewResponse.builder()
                    .id("review_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12))
                    .prTitle(prTitle)
                    .author(author)
                    .summary("AI 分析完成，但结果解析失败：" + e.getMessage())
                    .risks(List.of())
                    .suggestions(List.of())
                    .status("error")
                    .build();
        }
    }
}
