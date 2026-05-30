package com.weiki.prismbackend.model;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "PR 审查结果")
public class ReviewResponse {
    @Schema(description = "审查结果 ID", example = "review_abc123def456")
    private String id;
    @Schema(description = "PR 标题")
    private String prTitle;
    @Schema(description = "PR 作者")
    private String author;
    @Schema(description = "变更摘要")
    private String summary;
    @Schema(description = "风险列表")
    private List<RiskItem> risks;
    @Schema(description = "改进建议列表")
    private List<String> suggestions;
    @Schema(description = "PR 健康分（0-100），根据风险数量和等级综合计算，分数越高越健康", example = "78")
    private Integer healthScore;
    @Schema(description = "合并建议：RECOMMEND（推荐合并）/ CAUTION（谨慎合并）/ NOT_RECOMMEND（不推荐合并）", example = "CAUTION")
    private String mergeAdvice;
    @Schema(description = "状态：pending（待处理）/ processing（分析中）/ completed（完成）/ error（失败）", example = "completed")
    private String status;
}
