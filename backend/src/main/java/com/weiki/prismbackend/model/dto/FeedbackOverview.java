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
@Schema(description = "误报率统计概览")
public class FeedbackOverview {

    @Schema(description = "反馈总数")
    private long totalFeedbacks;

    @Schema(description = "标记为误报的数量")
    private long falsePositiveCount;

    @Schema(description = "标记为确认的数量")
    private long confirmedCount;

    @Schema(description = "误报率（误报数 / 反馈总数），保留两位小数，无反馈时为 0", example = "0.25")
    private double falsePositiveRate;
}
