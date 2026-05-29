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

    private String status;

    private String ghRepo;

    private String ghPrNumber;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
