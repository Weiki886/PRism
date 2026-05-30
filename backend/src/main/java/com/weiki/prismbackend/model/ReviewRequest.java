package com.weiki.prismbackend.model;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

@Data
@Schema(description = "PR 审查请求")
public class ReviewRequest {
    @NotBlank(message = "PR 链接不能为空")
    @Pattern(regexp = "https?://github\\.com/[^/]+/[^/]+/pull/\\d+.*",
            message = "PR 链接格式不正确，应形如 https://github.com/owner/repo/pull/123")
    @Schema(description = "GitHub PR 链接", example = "https://github.com/owner/repo/pull/123")
    private String prUrl;
}
