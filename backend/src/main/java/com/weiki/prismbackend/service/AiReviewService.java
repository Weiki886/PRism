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

            你会收到以下上下文信息：
            1. PR 标题和描述 — 理解变更目的
            2. Commit Messages — 理解开发者意图和变更历程
            3. 代码 Diff — 具体的代码变更内容
            4. 文件完整内容 — 理解修改代码的上下文（函数所在类、调用关系等）
            5. 已有评论 — 避免重复已有的反馈

            审查要求：
            - 结合完整文件上下文判断变更是否合理，避免脱离上下文的误报
            - 关注 commit message 与实际变更是否一致
            - 如果已有评论提到了某个问题，不要重复指出
            - 对于不确定的风险，标注较低的风险等级
            - 为每个风险评估置信度，帮助使用者识别可能的误报

            返回格式：
            {
              "summary": "对本次 PR 变更的简洁摘要，2-3句话，说明做了什么以及为什么",
              "risks": [
                {
                  "level": "CRITICAL|HIGH|MEDIUM|LOW",
                  "file": "文件路径",
                  "line": 行号整数或null,
                  "description": "风险描述，说明具体问题和可能的影响",
                  "confidence": "HIGH|MEDIUM|LOW"
                }
              ],
              "suggestions": ["改进建议1", "改进建议2"]
            }

            风险等级定义：
            - CRITICAL：安全漏洞（SQL注入、XSS、未授权访问、敏感信息泄露）
            - HIGH：逻辑错误、数据丢失风险、并发问题
            - MEDIUM：性能问题、代码规范违反、缺少错误处理
            - LOW：可读性、命名建议、代码风格

            置信度定义（用于误报控制）：
            - HIGH：基于完整上下文，确定是问题，几乎不可能误报
            - MEDIUM：很可能是问题，但受限于上下文不完整，存在一定误报可能
            - LOW：疑似问题，需人工确认，可能是误报
            """;

    private final ChatClient chatClient;
    private final ObjectMapper objectMapper;

    public AiReviewService(ChatClient.Builder builder, ObjectMapper objectMapper) {
        this.chatClient = builder.defaultSystem(SYSTEM_PROMPT).build();
        this.objectMapper = objectMapper;
    }

    /**
     * 使用增强上下文进行代码审查
     */
    public ReviewResponse analyze(String prTitle, String prBody, String diff,
                                  String author, String commitMessages,
                                  String reviewComments, String fileContexts) {
        StringBuilder prompt = new StringBuilder();
        prompt.append("## PR 标题\n").append(prTitle).append("\n\n");
        prompt.append("## PR 描述\n").append(prBody != null ? prBody : "（无描述）").append("\n\n");

        if (commitMessages != null && !commitMessages.isEmpty()) {
            prompt.append("## Commit 历史\n").append(commitMessages).append("\n\n");
        }

        if (reviewComments != null && !reviewComments.isEmpty()) {
            prompt.append("## 已有评论\n").append(reviewComments).append("\n\n");
        }

        prompt.append("## 代码变更 (Diff)\n").append(diff).append("\n\n");

        if (fileContexts != null && !fileContexts.isEmpty()) {
            prompt.append("## 修改文件完整内容（上下文参考）\n").append(fileContexts);
        }

        String content = chatClient.prompt()
                .user(prompt.toString())
                .call()
                .content();

        return parseResponse(content, prTitle, author);
    }

    /**
     * 兼容旧接口（无增强上下文）
     */
    public ReviewResponse analyze(String prTitle, String prBody, String diff, String author) {
        return analyze(prTitle, prBody, diff, author, null, null, null);
    }

    @SuppressWarnings("unchecked")
    private ReviewResponse parseResponse(String content, String prTitle, String author) {
        try {
            String json = content.trim();
            if (json.startsWith("```")) {
                json = json.replaceAll("(?s)^```[a-z]*\\n?", "")
                        .replaceAll("```\\s*$", "").trim();
            }

            Map<String, Object> parsed = objectMapper.readValue(json, new TypeReference<>() {});

            List<RiskItem> risks = new ArrayList<>();
            List<Map<String, Object>> rawRisks =
                    (List<Map<String, Object>>) parsed.getOrDefault("risks", List.of());
            for (Map<String, Object> r : rawRisks) {
                String confidence = (String) r.get("confidence");
                risks.add(RiskItem.builder()
                        .level((String) r.get("level"))
                        .file((String) r.get("file"))
                        .line(r.get("line") instanceof Number n ? n.intValue() : null)
                        .description((String) r.get("description"))
                        .confidence(confidence != null ? confidence : "MEDIUM")
                        .build());
            }

            List<String> suggestions =
                    (List<String>) parsed.getOrDefault("suggestions", List.of());

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
