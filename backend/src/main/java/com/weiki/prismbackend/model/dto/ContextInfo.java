package com.weiki.prismbackend.model.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "本次分析所使用的上下文信息，体现上下文获取策略")
public class ContextInfo {

    @Schema(description = "PR 变更文件总数")
    private int changedFiles;

    @Schema(description = "纳入分析的 diff 估算 token 数")
    private int diffTokens;

    @Schema(description = "是否纳入 commit 历史作为上下文")
    private boolean includedCommitMessages;

    @Schema(description = "是否纳入已有评论作为上下文")
    private boolean includedReviewComments;

    @Schema(description = "是否纳入修改文件的完整内容作为上下文")
    private boolean includedFileContexts;

    @Schema(description = "使用的 AI 模型名称", example = "qwen-max")
    private String model;
}
