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
@Schema(description = "单条风险的反馈统计")
public class RiskFeedbackStat {

    @Schema(description = "风险下标")
    private Integer riskIndex;

    @Schema(description = "标记为误报的次数")
    private long falsePositiveCount;

    @Schema(description = "标记为确认的次数")
    private long confirmedCount;

    @Schema(description = "当前用户的反馈类型，未反馈则为 null")
    private String myFeedback;
}
