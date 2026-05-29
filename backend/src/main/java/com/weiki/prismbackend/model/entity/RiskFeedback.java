package com.weiki.prismbackend.model.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.weiki.prismbackend.model.enums.FeedbackType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("risk_feedback")
public class RiskFeedback {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String reviewId;

    private Integer riskIndex;

    @TableField("`user_id`")
    private Long userId;

    private FeedbackType feedback;

    private String comment;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
