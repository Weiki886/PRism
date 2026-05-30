package com.weiki.prismbackend.model.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("review")
public class Review {

    @TableId(type = IdType.ASSIGN_UUID)
    private String id;

    private String prUrl;

    private String prTitle;

    private String author;

    @TableField("`user_id`")
    private Long userId;

    private String summary;

    private String risksJson;

    private String suggestionsJson;

    private String contextInfoJson;

    private String status;

    private String ghRepo;

    private String ghPrNumber;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;

    /**
     * 逻辑删除标记：0-未删除，1-已删除。
     * 删除时执行 UPDATE 置 1，查询时自动过滤已删除记录。
     */
    @TableLogic
    @TableField(select = false)
    private Integer deleted;
}
