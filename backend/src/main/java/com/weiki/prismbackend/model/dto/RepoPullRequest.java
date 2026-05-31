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
@Schema(description = "仓库 PR 列表项")
public class RepoPullRequest {

    @Schema(description = "PR 编号", example = "42")
    private int number;

    @Schema(description = "PR 标题")
    private String title;

    @Schema(description = "PR 作者")
    private String author;

    @Schema(description = "作者头像 URL")
    private String avatarUrl;

    @Schema(description = "PR 状态：open / closed / merged")
    private String state;

    @Schema(description = "创建时间（ISO 8601）")
    private String createdAt;

    @Schema(description = "最后更新时间（ISO 8601）")
    private String updatedAt;

    @Schema(description = "标签列表")
    private List<String> labels;

    @Schema(description = "PR 页面链接")
    private String htmlUrl;

    @Schema(description = "是否已合并")
    private boolean merged;
}
