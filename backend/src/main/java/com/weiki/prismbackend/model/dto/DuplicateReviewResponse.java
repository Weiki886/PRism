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
@Schema(description = "PR 重复提交时返回的已有记录信息")
public class DuplicateReviewResponse {

    @Schema(description = "已有分析记录的 ID", example = "review_abc123def456")
    private String existingReviewId;

    @Schema(description = "已有记录的状态", example = "completed")
    private String status;

    @Schema(description = "PR 标题", example = "feat: add login")
    private String prTitle;
}
