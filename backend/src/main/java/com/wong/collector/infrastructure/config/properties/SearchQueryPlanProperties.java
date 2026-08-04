package com.wong.collector.infrastructure.config.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * 搜索查询构建配置：关键词簇、模板、长度限制等。
 */
@Data
@Component
@ConfigurationProperties(prefix = "paper.search.query-plan")
public class SearchQueryPlanProperties {

    /**
     * 关键词簇列表，按角色区分。
     */
    private List<Cluster> clusters;

    /**
     * provider -> 模板；占位符：{anchor} {subject} {boost}
     */
    private Map<String, String> templates;

    /**
     * provider -> 限制参数。
     */
    private Map<String, Limit> limits;

    /**
     * 每个 provider 生成的最大查询条数。
     */
    private Integer maxQueriesPerProvider = 10;

    /**
     * 每个查询中性能/增强词的最大 OR 数量。
     */
    private Integer boostTermsPerQuery = 4;

    @Data
    public static class Cluster {
        /**
         * 簇名称：用于快照。
         */
        private String name;

        /**
         * 角色：anchor/subject/boost。
         */
        private String role;

        /**
         * 词条列表。
         */
        private List<String> terms;

        /**
         * 单簇最多取多少个词。
         */
        private Integer maxTerms;
    }

    @Data
    public static class Limit {
        /**
         * 查询字符串最大长度。
         */
        private Integer maxLength;

        /**
         * OR 块的最大项数。
         */
        private Integer maxOrTerms;
    }
}
