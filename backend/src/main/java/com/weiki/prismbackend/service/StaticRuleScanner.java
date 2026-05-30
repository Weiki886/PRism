package com.weiki.prismbackend.service;

import com.weiki.prismbackend.model.RiskItem;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * 静态规则扫描器，作为 AI 分析的补充兜底。
 * 用正则规则扫描 diff 中的新增代码行，识别确定性强的常见问题
 * （硬编码密钥、空异常捕获、调试输出等），保证这类基础问题不漏报。
 * <p>
 * 规则结果与 AI 结果合并，规则负责"确定性问题不漏报"，AI 负责"语义级深度理解"，
 * 两者结合提升整体的误报漏报控制能力。
 */
public final class StaticRuleScanner {

    /** 单条规则定义 */
    private record Rule(Pattern pattern, String level, String description, String suggestedFix) {
    }

    private static final List<Rule> RULES = List.of(
            new Rule(
                    Pattern.compile("(?i)(password|passwd|pwd|secret|api[_-]?key|token|access[_-]?key)\\s*[=:]\\s*[\"'][^\"']{3,}[\"']"),
                    "CRITICAL",
                    "疑似硬编码敏感信息（密码/密钥/Token），存在凭据泄露风险",
                    "将敏感信息移至环境变量或配置中心，代码中通过占位符引用"
            ),
            new Rule(
                    Pattern.compile("catch\\s*\\([^)]*\\)\\s*\\{\\s*\\}"),
                    "MEDIUM",
                    "空的异常捕获块，异常被静默吞掉，不利于排查问题",
                    "至少记录日志：log.error(\"...\", e); 或向上抛出"
            ),
            new Rule(
                    Pattern.compile("System\\.out\\.println|System\\.err\\.println"),
                    "LOW",
                    "使用 System.out/err 打印而非日志框架，不便于日志管理",
                    "改用日志框架：log.info(...) / log.debug(...)"
            ),
            new Rule(
                    Pattern.compile("printStackTrace\\s*\\(\\s*\\)"),
                    "LOW",
                    "使用 printStackTrace 输出异常，应使用日志框架记录",
                    "改为 log.error(\"异常描述\", e);"
            ),
            new Rule(
                    Pattern.compile("(?i)//\\s*(TODO|FIXME|XXX)"),
                    "LOW",
                    "存在未完成标记（TODO/FIXME），可能是遗留的未完成逻辑",
                    "完成对应逻辑或在合并前确认可暂留"
            )
    );

    private StaticRuleScanner() {
    }

    /**
     * 扫描 diff，对新增代码行（以 + 开头）应用规则，返回命中的风险列表。
     * diff 按 "### 文件名" 分段（与 GitHubService.buildDiff 输出格式一致）。
     */
    public static List<RiskItem> scan(String diff) {
        List<RiskItem> result = new ArrayList<>();
        if (diff == null || diff.isBlank()) {
            return result;
        }

        String currentFile = "";
        for (String line : diff.split("\n")) {
            // 文件分隔标记
            if (line.startsWith("### ")) {
                currentFile = line.substring(4).trim();
                continue;
            }

            // 只扫描新增行（+ 开头，且排除 diff 头部的 +++）
            if (!line.startsWith("+") || line.startsWith("+++")) {
                continue;
            }

            String code = line.substring(1);
            for (Rule rule : RULES) {
                if (rule.pattern().matcher(code).find()) {
                    result.add(RiskItem.builder()
                            .level(rule.level())
                            .file(currentFile)
                            .line(null)
                            .description(rule.description())
                            .confidence("HIGH")
                            .suggestedFix(rule.suggestedFix())
                            .source("RULE")
                            .build());
                }
            }
        }
        return result;
    }
}
