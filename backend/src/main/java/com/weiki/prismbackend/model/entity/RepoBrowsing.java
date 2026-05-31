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
@TableName("repo_browsing")
public class RepoBrowsing {

    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField("`user_id`")
    private Long userId;

    private String repoUrl;

    private String fullName;

    private String description;

    private String language;

    private Integer starCount;

    private String ownerAvatarUrl;

    private String htmlUrl;

    private Boolean isPrivate;

    private LocalDateTime lastVisitedAt;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;

    @TableLogic
    @TableField(select = false)
    private Integer deleted;
}
