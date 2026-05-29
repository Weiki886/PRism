package com.weiki.prismbackend.model;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "风险条目")
public class RiskItem {
    @Schema(description = "风险等级：CRITICAL / HIGH / MEDIUM / LOW", example = "HIGH")
    private String level;
    @Schema(description = "涉及文件路径", example = "src/auth/AuthService.java")
    private String file;
    @Schema(description = "行号，可能为 null", example = "42")
    private Integer line;
    @Schema(description = "风险描述")
    private String description;
}
