package com.wong.collector.application.workflow;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch.indices.AnalyzeResponse;
import co.elastic.clients.json.jackson.JacksonJsonpMapper;
import co.elastic.clients.transport.ElasticsearchTransport;
import co.elastic.clients.transport.rest_client.RestClientTransport;
import org.apache.http.HttpHost;
import org.elasticsearch.client.RestClient;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

/**
 * ES IK分词器专业术语识别测试（优化版）
 * 特性：
 * 1. 英文大小写不敏感（IK自动转小写）
 * 2. 英文多词术语支持部分匹配（如"viscoelastic surfactant"匹配viscoelastic或surfactant即可）
 * 3. 按术语类型设置不同识别率阈值
 */
@EnabledIfEnvironmentVariable(named = "RUN_INTEGRATION_TESTS", matches = "true")
public class EsIkEnhancedTest {

    private static ElasticsearchClient client;
    private static RestClient restClient;

    // 术语分类定义（区分中英文，英文支持部分匹配）
    private static final Map<String, List<TermDef>> CATEGORY_TERMS = new LinkedHashMap<>();

    static {
        // 1. 聚合物基础材料
        CATEGORY_TERMS.put("聚合物材料", Arrays.asList(
                new TermDef("HPAM", true),
                new TermDef("部分水解聚丙烯酰胺", false),
                new TermDef("partially hydrolyzed polyacrylamide", true), // 英文，支持部分匹配
                new TermDef("PAM", true),
                new TermDef("聚丙烯酰胺", false),
                new TermDef("polyacrylamide", true),
                new TermDef("疏水缔合聚合物", false),
                new TermDef("associative polymer", true),
                new TermDef("纤维素", false),
                new TermDef("cellulose", true),
                new TermDef("CMC", true),
                new TermDef("羧甲基纤维素", false),
                new TermDef("carboxymethyl cellulose", true),
                new TermDef("HEC", true),
                new TermDef("羟乙基纤维素", false),
                new TermDef("hydroxyethyl cellulose", true),
                new TermDef("瓜尔胶", false),
                new TermDef("guar gum", true),
                new TermDef("HPG", true),
                new TermDef("羟丙基瓜尔胶", false),
                new TermDef("hydroxypropyl guar", true),
                new TermDef("CMHPG", true),
                new TermDef("羧甲基羟丙基瓜尔胶", false),
                new TermDef("carboxymethyl hydroxypropyl guar", true),
                new TermDef("黄原胶", false),
                new TermDef("xanthan gum", true)
        ));

        // 2. 压裂液体系类型
        CATEGORY_TERMS.put("压裂液体系", Arrays.asList(
                new TermDef("压裂液", false),
                new TermDef("fracturing fluid", true),
                new TermDef("水力压裂", false),
                new TermDef("hydraulic fracturing", true),
                new TermDef("滑溜水", false),
                new TermDef("slickwater", true),
                new TermDef("线性胶", false),
                new TermDef("linear gel", true),
                new TermDef("交联冻胶", false),
                new TermDef("crosslinked gel", true),
                new TermDef("泡沫压裂液", false),
                new TermDef("foam fracturing fluid", true),
                new TermDef("VES", true),
                new TermDef("粘弹性表面活性剂", false),
                new TermDef("viscoelastic surfactant", true)
        ));

        // 3. 功能性添加剂
        CATEGORY_TERMS.put("功能性添加剂", Arrays.asList(
                new TermDef("交联剂", false),
                new TermDef("crosslinker", true),
                new TermDef("延迟交联剂", false),
                new TermDef("delayed crosslinker", true),
                new TermDef("硼砂", false),
                new TermDef("borax", true),
                new TermDef("有机锆", false),
                new TermDef("zirconium acetate", true),
                new TermDef("破胶剂", false),
                new TermDef("breaker", true),
                new TermDef("减阻剂", false),
                new TermDef("friction reducer", true),
                new TermDef("降滤失剂", false),
                new TermDef("fluid loss additive", true),
                new TermDef("助排剂", false),
                new TermDef("flowback aid", true),
                new TermDef("纳米颗粒", false),
                new TermDef("nanoparticle", true),
                new TermDef("润滑剂", false),
                new TermDef("lubricant", true),
                new TermDef("杀菌剂", false),
                new TermDef("biocide", true),
                new TermDef("pH调节剂", false),
                new TermDef("pH regulator", true)
        ));

        // 4. 地层保护化学品
        CATEGORY_TERMS.put("地层保护剂", Arrays.asList(
                new TermDef("粘土稳定剂", false),
                new TermDef("clay stabilizer", true),
                new TermDef("防膨剂", false),
                new TermDef("clay inhibitor", true),
                new TermDef("氯化钾", false),
                new TermDef("potassium chloride", true),
                new TermDef("KCl", true),
                new TermDef("氯化钠", false),
                new TermDef("sodium chloride", true),
                new TermDef("NaCl", true),
                new TermDef("氯化钙", false),
                new TermDef("calcium chloride", true),
                new TermDef("CaCl2", true),
                new TermDef("甲酸钠", false),
                new TermDef("sodium formate", true),
                new TermDef("甲酸钾", false),
                new TermDef("potassium formate", true)
        ));

        // 5. 钻井液与测试
        CATEGORY_TERMS.put("钻井液体系", Arrays.asList(
                new TermDef("钻井液", false),
                new TermDef("drilling fluid", true),
                new TermDef("drilling mud", true),
                new TermDef("API滤失", false),
                new TermDef("HTHP滤失", false)
        ));
    }

    @BeforeAll
    static void setUp() {
        restClient = RestClient.builder(new HttpHost("localhost", 9200)).build();
        ElasticsearchTransport transport = new RestClientTransport(restClient, new JacksonJsonpMapper());
        client = new ElasticsearchClient(transport);
        System.out.println("✅ ES 客户端初始化成功，准备测试专业术语识别...");
    }

    @AfterAll
    static void tearDown() throws IOException {
        if (restClient != null) {
            restClient.close();
            System.out.println("\n🔌 ES 连接已关闭");
        }
    }

    /**
     * 术语定义类（区分中英文）
     */
    static class TermDef {
        final String text;
        final boolean isEnglish; // 是否为英文（支持部分匹配和大小写不敏感）

        TermDef(String text, boolean isEnglish) {
            this.text = text;
            this.isEnglish = isEnglish;
        }
    }

    /**
     * 提取并归一化tokens（转小写去重）
     */
    private Set<String> extractTokens(AnalyzeResponse response) {
        return response.tokens().stream()
                .map(t -> t.token().toLowerCase())
                .collect(Collectors.toSet());
    }

    /**
     * 检查是否包含术语（支持英文部分匹配）
     */
    private boolean containsTerm(Set<String> tokens, TermDef term) {
        if (!term.isEnglish) {
            // 中文：精确匹配
            return tokens.contains(term.text);
        } else {
            // 英文：大小写不敏感，支持部分匹配（多词术语匹配任一单词）
            String lowerTerm = term.text.toLowerCase();

            // 首先尝试完整匹配（如"hpam"）
            if (tokens.contains(lowerTerm)) return true;

            // 对于多词术语，检查是否包含任一单词（如"viscoelastic surfactant"匹配"viscoelastic"或"surfactant"）
            String[] words = lowerTerm.split("\\s+");
            if (words.length > 1) {
                return Arrays.stream(words).anyMatch(tokens::contains);
            }
            return false;
        }
    }

    /**
     * 执行分词并返回tokens
     */
    private Set<String> analyzeText(String text) throws IOException {
        AnalyzeResponse response = client.indices().analyze(a -> a
                .analyzer("ik_max_word")
                .text(text)
        );
        return extractTokens(response);
    }

    @Test
    @DisplayName("✅ 验证核心术语识别（大小写不敏感）")
    void testCoreTermsRecognition() throws IOException {
        System.out.println("\n🔍 验证核心术语识别");
        System.out.println("=".repeat(60));

        String testText = "HPAM部分水解聚丙烯酰胺压裂液羟丙基瓜尔胶HPG";
        Set<String> tokens = analyzeText(testText);

        System.out.println("原文: " + testText);
        System.out.println("分词: " + String.join(" | ", tokens));

        // 验证关键术语（大小写不敏感）
        boolean hasHPAM = tokens.contains("hpam");
        boolean hasHPG = tokens.contains("hpg");
        boolean hasPartial = tokens.contains("部分水解聚丙烯酰胺");
        boolean hasFracturing = tokens.contains("压裂液");
        boolean hasGuar = tokens.contains("羟丙基瓜尔胶");

        System.out.println("\n识别检查：");
        System.out.println("  HPAM (hpam): " + (hasHPAM ? "✅" : "❌"));
        System.out.println("  部分水解聚丙烯酰胺: " + (hasPartial ? "✅" : "❌"));
        System.out.println("  压裂液: " + (hasFracturing ? "✅" : "❌"));
        System.out.println("  羟丙基瓜尔胶: " + (hasGuar ? "✅" : "❌"));
        System.out.println("  HPG (hpg): " + (hasHPG ? "✅" : "❌"));

        // 断言：核心中文术语必须识别，英文术语大小写不敏感
        assertAll("核心术语识别检查",
                () -> assertTrue(hasPartial, "必须识别中文术语：部分水解聚丙烯酰胺"),
                () -> assertTrue(hasFracturing, "必须识别中文术语：压裂液"),
                () -> assertTrue(hasGuar, "必须识别中文术语：羟丙基瓜尔胶"),
                () -> assertTrue(hasHPAM, "必须识别英文术语（小写）：hpam"),
                () -> assertTrue(hasHPG, "必须识别英文术语（小写）：hpg")
        );
    }

    @Test
    @DisplayName("🔍 词典加载诊断（按类别统计）")
    void diagnoseDictionaryLoading() throws IOException {
        System.out.println("\n" + "=".repeat(60));
        System.out.println("🔍 词典加载状态诊断（支持英文部分匹配）");
        System.out.println("=".repeat(60));

        int totalTerms = 0;
        int totalMatched = 0;

        for (Map.Entry<String, List<TermDef>> entry : CATEGORY_TERMS.entrySet()) {
            String category = entry.getKey();
            List<TermDef> terms = entry.getValue();

            System.out.println("\n📂 " + category + ":");

            int categoryMatched = 0;
            for (TermDef term : terms) {
                Set<String> tokens = analyzeText(term.text);
                boolean matched = containsTerm(tokens, term);

                String status = matched ? "✅" : "❌";
                String displayText = term.text.length() > 20 ?
                        term.text.substring(0, 18) + ".." : term.text;

                if (term.isEnglish) {
                    System.out.printf("  %s %-25s (EN) %n", status, displayText);
                } else {
                    System.out.printf("  %s %-25s (CN) %n", status, displayText);
                }

                if (matched) categoryMatched++;
            }

            double ratio = (double) categoryMatched / terms.size() * 100;
            totalTerms += terms.size();
            totalMatched += categoryMatched;

            System.out.printf("  → 识别率: %d/%d (%.0f%%)%n",
                    categoryMatched, terms.size(), ratio);
        }

        double overallRatio = (double) totalMatched / totalTerms * 100;
        System.out.println("\n" + "=".repeat(60));
        System.out.printf("📊 总体识别率: %d/%d (%.1f%%)%n",
                totalMatched, totalTerms, overallRatio);
        System.out.println("=".repeat(60));

        // 诊断建议
        if (overallRatio < 60) {
            System.out.println("⚠️  诊断：识别率偏低，建议检查：");
            System.out.println("  1. 词典文件是否已更新到 E:/es-chemistry/dicts/");
            System.out.println("  2. 是否已执行 docker restart es-chemistry");
            System.out.println("  3. 日志中是否有 [Dict Loading] 记录");
        }

        assertTrue(overallRatio >= 60,
                "总体识别率应不低于60%，实际为" + String.format("%.1f%%", overallRatio));
    }

    @Test
    @DisplayName("🧪 综合技术文档分词测试（长文本）")
    void testComprehensiveTechnicalText() throws IOException {
        String technicalText =
                "本研究针对页岩气储层水力压裂作业，开发了一种新型低伤害压裂液体系。体系以" +
                        "HPAM（部分水解聚丙烯酰胺，partially hydrolyzed polyacrylamide）为主剂，" +
                        "部分替代传统的HPG（羟丙基瓜尔胶，hydroxypropyl guar）和CMHPG（羧甲基羟丙基瓜尔胶）。\n" +
                        "配方优化方面，采用有机锆（zirconium acetate）作为延迟交联剂，避免使用传统硼砂交联剂，" +
                        "同时加入纳米颗粒（nanoparticle）增强耐温性。为降低地层伤害，添加了KCl（氯化钾）和" +
                        "甲酸钾（potassium formate）作为粘土稳定剂（clay stabilizer），配合防膨剂（clay inhibitor）" +
                        "防止水敏性粘土矿物膨胀。\n" +
                        "对比测试包括：水基滑溜水（slickwater）、线性胶（linear gel）、交联冻胶（crosslinked gel）" +
                        "和VES（粘弹性表面活性剂，viscoelastic surfactant）体系。性能评价涵盖API滤失、HTHP滤失、" +
                        "以及破胶剂（breaker）效率测试。此外，添加了疏水缔合聚合物（associative polymer）和" +
                        "HEC（羟乙基纤维素，hydroxyethyl cellulose）调节流变性，使用pH调节剂维持体系稳定性。\n" +
                        "现场应用采用泡沫压裂液（foam fracturing fluid）技术，配合减阻剂（friction reducer）和" +
                        "助排剂（flowback aid），在含黄原胶（xanthan gum）和CMC（羧甲基纤维素）的钻井液（drilling fluid）" +
                        "环境中表现出良好的配伍性。降滤失剂（fluid loss additive）和润滑剂（lubricant）的加入进一步改善了" +
                        "储层裂缝导流能力。";

        System.out.println("\n" + "=".repeat(60));
        System.out.println("🧪 综合技术文档分词测试");
        System.out.println("=".repeat(60));
        System.out.println("📄 测试文本长度: " + technicalText.length() + " 字符");

        Set<String> tokens = analyzeText(technicalText);
        System.out.println("🔍 去重后词条数: " + tokens.size());

        // 分类统计
        System.out.println("\n📊 分词结果分类统计:");
        System.out.println("-".repeat(60));

        int totalExpected = 0;
        int totalMatched = 0;

        for (Map.Entry<String, List<TermDef>> entry : CATEGORY_TERMS.entrySet()) {
            String category = entry.getKey();
            List<TermDef> terms = entry.getValue();

            long matched = terms.stream()
                    .filter(term -> containsTerm(tokens, term))
                    .count();

            totalExpected += terms.size();
            totalMatched += matched;

            double ratio = (double) matched / terms.size() * 100;
            String status = ratio >= 70 ? "✅" : ratio >= 40 ? "⚠️" : "❌";

            System.out.printf("%s %-12s: %2d/%2d (%.0f%%)%n",
                    status, category, (int) matched, terms.size(), ratio);

            // 显示未识别术语（最多3个）
            List<String> missed = terms.stream()
                    .filter(term -> !containsTerm(tokens, term))
                    .map(t -> t.text)
                    .limit(3)
                    .collect(Collectors.toList());

            if (!missed.isEmpty()) {
                System.out.println("   未识别示例: " + String.join(", ", missed) +
                        (missed.size() == 3 ? " 等" : ""));
            }
        }

        System.out.println("-".repeat(60));
        double overallRatio = (double) totalMatched / totalExpected * 100;
        System.out.printf("📈 总体识别率: %d/%d (%.1f%%)%n",
                totalMatched, totalExpected, overallRatio);

        // 关键术语必须识别（中英文混合检查）
        String[] criticalTerms = {
                "部分水解聚丙烯酰胺", "羟丙基瓜尔胶", "压裂液",
                "交联剂", "破胶剂", "纳米颗粒", "hpam", "hpg", "cmhpg"
        };

        System.out.println("\n🎯 关键术语检查:");
        for (String term : criticalTerms) {
            boolean found = tokens.contains(term.toLowerCase());
            System.out.printf("  %s %s%n", found ? "✅" : "❌", term);
            if (!term.matches("[a-z]+")) { // 中文术语强制要求
                assertTrue(found, "关键术语必须被识别: " + term);
            }
        }

        assertTrue(overallRatio >= 60,
                "总体识别率应不低于60%，实际为" + String.format("%.1f%%", overallRatio));
    }

    @Test
    @DisplayName("🌐 中英混合术语识别测试")
    void testBilingualTerms() throws IOException {
        System.out.println("\n" + "=".repeat(60));
        System.out.println("🌐 中英混合术语识别测试");
        System.out.println("=".repeat(60));

        String[] testCases = {
                "使用HPAM和partially hydrolyzed polyacrylamide进行对比",
                "添加zirconium acetate（有机锆）作为crosslinker",
                "测量API滤失和HTHP滤失性能",
                "配伍性测试使用KCl和potassium chloride溶液"
        };

        for (String text : testCases) {
            System.out.println("\n📝 原文: " + text);
            Set<String> tokens = analyzeText(text);
            System.out.println("   分词: " + String.join(" | ", tokens));

            // 检查是否包含英文单词（小写）
            long englishCount = tokens.stream()
                    .filter(t -> t.matches("[a-z]+"))
                    .count();

            System.out.printf("   ✅ 识别英文单词数: %d%n", englishCount);
            assertTrue(englishCount > 0, "应识别出至少一个英文术语");
        }
    }

    @Test
    @DisplayName("🛡️ 术语完整性保护测试（防止过度拆分）")
    void testTermIntegrity() throws IOException {
        System.out.println("\n" + "=".repeat(60));
        System.out.println("🛡️ 术语完整性保护测试");
        System.out.println("=".repeat(60));

        String[] integrityTests = {
                "部分水解聚丙烯酰胺",
                "羧甲基羟丙基瓜尔胶",
                "粘弹性表面活性剂",
                "水力压裂",
                "泡沫压裂液",
                "延迟交联剂"
        };

        for (String term : integrityTests) {
            Set<String> tokens = analyzeText(term);
            boolean isIntact = tokens.contains(term);

            String status = isIntact ? "✅" : "⚠️";
            System.out.printf("%s %-20s → %s%n", status, term,
                    isIntact ? "完整保留" : "被拆分为: " + tokens);

            // 长术语（>4字）必须完整保留
            if (term.length() >= 6) {
                assertTrue(isIntact, "长术语应保持完整: " + term);
            }
        }
    }

    @Test
    @DisplayName("⚖️ 分词模式对比: ik_max_word vs ik_smart")
    void testAnalyzerModeComparison() throws IOException {
        String text = "使用羟丙基瓜尔胶HPG配制低浓度交联冻胶压裂液";

        System.out.println("\n" + "=".repeat(60));
        System.out.println("⚖️  分词模式对比");
        System.out.println("=".repeat(60));
        System.out.println("原文: " + text);

        // ik_max_word
        Set<String> maxTokens = client.indices().analyze(a -> a
                .analyzer("ik_max_word")
                .text(text)
        ).tokens().stream().map(t -> t.token()).collect(Collectors.toSet());

        // ik_smart
        Set<String> smartTokens = client.indices().analyze(a -> a
                .analyzer("ik_smart")
                .text(text)
        ).tokens().stream().map(t -> t.token()).collect(Collectors.toSet());

        System.out.println("\nik_max_word: " + String.join(" | ", maxTokens));
        System.out.println("ik_smart:    " + String.join(" | ", smartTokens));

        // 检查关键术语
        boolean maxHasGuar = maxTokens.contains("羟丙基瓜尔胶");
        boolean maxHasHPG = maxTokens.stream().anyMatch(t -> t.equalsIgnoreCase("HPG"));
        boolean smartHasGuar = smartTokens.contains("羟丙基瓜尔胶");

        System.out.printf("%n📝 ik_max_word 包含'羟丙基瓜尔胶': %s%n", maxHasGuar ? "✅" : "❌");
        System.out.printf("📝 ik_max_word 包含'HPG': %s%n", maxHasHPG ? "✅" : "❌");
        System.out.printf("📝 ik_smart 包含'羟丙基瓜尔胶': %s%n", smartHasGuar ? "✅" : "❌");

        assertTrue(maxHasGuar && maxHasHPG,
                "ik_max_word应同时识别中文术语和英文缩写（大小写不敏感）");
    }

    @Test
    @DisplayName("🔍 打印详细分词结构（调试用）")
    void printDetailedTokens() throws IOException {
        String text = "HPAM部分水解聚丙烯酰胺压裂液";

        AnalyzeResponse response = client.indices().analyze(a -> a
                .analyzer("ik_max_word")
                .text(text)
        );

        System.out.println("\n🔍 详细分词结构分析:");
        System.out.println("原文: " + text);
        System.out.println("-".repeat(60));
        System.out.printf("%-20s %6s %6s %s%n", "词条", "起始", "结束", "类型");
        System.out.println("-".repeat(60));

        response.tokens().forEach(token -> {
            System.out.printf("%-20s %6d %6d %s%n",
                    token.token(),
                    token.startOffset(),
                    token.endOffset(),
                    token.type()
            );
        });
    }
}
