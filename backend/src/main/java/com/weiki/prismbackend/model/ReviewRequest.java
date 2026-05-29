package com.weiki.prismbackend.model;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "PR 审查请求")
public class ReviewRequest {
    @Schema(description = "GitHub PR 链接", example = "https://github.com/owner/repo/pull/123")
    private String prUrl;
}
