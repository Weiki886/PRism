package com.weiki.prismbackend.model.dto;

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
@Schema(description = "GitHub 仓库基本信息")
public class RepoInfo {

    @Schema(description = "仓库全名，如 owner/repo")
    private String fullName;

    @Schema(description = "仓库描述")
    private String description;

    @Schema(description = "主要编程语言")
    private String language;

    @Schema(description = "Star 数")
    private int starCount;

    @Schema(description = "Fork 数")
    private int forkCount;

    @Schema(description = "Open Issues 数（含 PR）")
    private int openIssuesCount;

    @Schema(description = "仓库 Owner 头像")
    private String ownerAvatarUrl;

    @Schema(description = "仓库 Owner 名称")
    private String ownerName;

    @Schema(description = "仓库页面链接")
    private String htmlUrl;

    @Schema(description = "默认分支")
    private String defaultBranch;

    @Schema(description = "是否为私有仓库")
    private boolean isPrivate;

    @Schema(description = "话题标签")
    private List<String> topics;

    @Schema(description = "最后推送时间（ISO 8601）")
    private String pushedAt;
}
