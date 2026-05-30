package com.weiki.prismbackend.model.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "用户评审统计概览")
public class ReviewStats {

    @Schema(description = "评审记录总数")
    private long totalReviews;

    @Schema(description = "已完成的评审数")
    private long completedReviews;

    @Schema(description = "分析失败的评审数")
    private long errorReviews;

    @Schema(description = "进行中的评审数（pending + processing）")
    private long processingReviews;

    @Schema(description = "累计发现的风险总数")
    private long totalRisks;

    @Schema(description = "各风险等级的数量分布，key 为 CRITICAL/HIGH/MEDIUM/LOW")
    private Map<String, Long> riskLevelDistribution;
}
