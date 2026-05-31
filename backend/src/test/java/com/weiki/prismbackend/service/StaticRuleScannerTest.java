package com.weiki.prismbackend.service;

import com.weiki.prismbackend.model.RiskItem;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * StaticRuleScanner 单元测试。
 * 纯逻辑类，不依赖 Spring 容器。
 */
class StaticRuleScannerTest {

    @Test
    @DisplayName("空 diff 返回空列表")
    void emptyDiff_shouldReturnEmpty() {
        assertTrue(StaticRuleScanner.scan(null).isEmpty());
        assertTrue(StaticRuleScanner.scan("").isEmpty());
        assertTrue(StaticRuleScanner.scan("   ").isEmpty());
    }

    @Test
    @DisplayName("检测硬编码密码 → CRITICAL")
    void hardcodedPassword_shouldDetectCritical() {
        String diff = """
                ### src/Config.java
                +    String password = "admin123";
                """;
        List<RiskItem> risks = StaticRuleScanner.scan(diff);
        assertFalse(risks.isEmpty());
        assertEquals("CRITICAL", risks.get(0).getLevel());
        assertEquals("src/Config.java", risks.get(0).getFile());
        assertEquals("RULE", risks.get(0).getSource());
        assertEquals("HIGH", risks.get(0).getConfidence());
    }

    @Test
    @DisplayName("检测硬编码 API Key → CRITICAL")
    void hardcodedApiKey_shouldDetectCritical() {
        String diff = """
                ### src/Service.java
                +    String apiKey = "sk-abc123def456";
                """;
        List<RiskItem> risks = StaticRuleScanner.scan(diff);
        assertFalse(risks.isEmpty());
        assertEquals("CRITICAL", risks.get(0).getLevel());
    }

    @Test
    @DisplayName("检测空 catch 块 → MEDIUM")
    void emptyCatch_shouldDetectMedium() {
        String diff = """
                ### src/Handler.java
                +    } catch (Exception e) {}
                """;
        List<RiskItem> risks = StaticRuleScanner.scan(diff);
        assertFalse(risks.isEmpty());
        assertEquals("MEDIUM", risks.get(0).getLevel());
    }

    @Test
    @DisplayName("检测 System.out.println → LOW")
    void systemOut_shouldDetectLow() {
        String diff = """
                ### src/Debug.java
                +    System.out.println("debug");
                """;
        List<RiskItem> risks = StaticRuleScanner.scan(diff);
        assertFalse(risks.isEmpty());
        assertEquals("LOW", risks.get(0).getLevel());
    }

    @Test
    @DisplayName("检测 printStackTrace → LOW")
    void printStackTrace_shouldDetectLow() {
        String diff = """
                ### src/Handler.java
                +    e.printStackTrace();
                """;
        List<RiskItem> risks = StaticRuleScanner.scan(diff);
        assertFalse(risks.isEmpty());
        assertEquals("LOW", risks.get(0).getLevel());
    }

    @Test
    @DisplayName("检测 TODO 标记 → LOW")
    void todoComment_shouldDetectLow() {
        String diff = """
                ### src/Service.java
                +    // TODO: implement this
                """;
        List<RiskItem> risks = StaticRuleScanner.scan(diff);
        assertFalse(risks.isEmpty());
        assertEquals("LOW", risks.get(0).getLevel());
    }

    @Test
    @DisplayName("不扫描删除行（- 开头）")
    void deletedLines_shouldBeIgnored() {
        String diff = """
                ### src/Old.java
                -    String password = "admin123";
                """;
        List<RiskItem> risks = StaticRuleScanner.scan(diff);
        assertTrue(risks.isEmpty());
    }

    @Test
    @DisplayName("不扫描未变更行（无前缀）")
    void contextLines_shouldBeIgnored() {
        String diff = """
                ### src/Old.java
                     String password = "admin123";
                """;
        List<RiskItem> risks = StaticRuleScanner.scan(diff);
        assertTrue(risks.isEmpty());
    }

    @Test
    @DisplayName("+++ 开头的 diff 头部不误报")
    void diffHeader_shouldBeIgnored() {
        String diff = """
                +++ b/src/Config.java
                """;
        List<RiskItem> risks = StaticRuleScanner.scan(diff);
        assertTrue(risks.isEmpty());
    }

    @Test
    @DisplayName("正常代码不触发规则")
    void normalCode_shouldNotTrigger() {
        String diff = """
                ### src/Service.java
                +    public String getName() { return this.name; }
                """;
        List<RiskItem> risks = StaticRuleScanner.scan(diff);
        assertTrue(risks.isEmpty());
    }

    @Test
    @DisplayName("多个文件多条规则命中")
    void multipleFiles_multipleRules() {
        String diff = """
                ### src/A.java
                +    String secret = "mysecret123";
                ### src/B.java
                +    System.out.println("test");
                +    // FIXME: broken
                """;
        List<RiskItem> risks = StaticRuleScanner.scan(diff);
        assertEquals(3, risks.size());
        assertEquals("src/A.java", risks.get(0).getFile());
        assertEquals("src/B.java", risks.get(1).getFile());
        assertEquals("src/B.java", risks.get(2).getFile());
    }
}
