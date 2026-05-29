package com.weiki.prismbackend.model.dto;

import com.weiki.prismbackend.model.enums.FeedbackType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
@Schema(description = "风险反馈请求")
public class RiskFeedbackRequest {

    @NotNull(message = "风险下标不能为空")
    @Schema(description = "风险在 risks 数组中的下标，从 0 开始", example = "0")
    private Integer riskIndex;

    @NotNull(message = "反馈类型不能为空")
    @Schema(description = "反馈类型：FALSE_POSITIVE 误报 / CONFIRMED 确认", example = "FALSE_POSITIVE")
    private FeedbackType feedback;

    @Schema(description = "反馈补充说明（可选）", example = "该处已做空值校验，不存在 NPE 风险")
    private String comment;
}
