package com.weiki.prismbackend.model.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "用户最近浏览过的仓库记录")
public class RepoBrowsingDTO {

    @Schema(description = "记录 ID")
    private Long id;

    @Schema(description = "仓库链接")
    private String repoUrl;

    @Schema(description = "仓库全名 owner/repo")
    private String fullName;

    @Schema(description = "仓库描述")
    private String description;

    @Schema(description = "主要编程语言")
    private String language;

    @Schema(description = "Star 数")
    private Integer starCount;

    @Schema(description = "Owner 头像 URL")
    private String ownerAvatarUrl;

    @Schema(description = "仓库 GitHub 页面链接")
    private String htmlUrl;

    @Schema(description = "是否私有仓库")
    private boolean isPrivate;

    @Schema(description = "最近一次浏览时间")
    private LocalDateTime lastVisitedAt;
}
