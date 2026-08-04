package com.wong.collector.infrastructure.config.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Semantic Scholar Academic Graph API 配置。
 * <p>
 * 参考文档：https://www.semanticscholar.org/product/api
 */
@Data
@Component
@ConfigurationProperties(prefix = "semantic-scholar.api")
public class SemanticScholarApiProperties {

    /**
     * API Key（可选但强烈建议配置，以获得独立限流额度）。
     */
    private String key;

    /**
     * 单次搜索最大返回条数。
     */
    private Integer maxResults = 100;

    private final RateLimit rateLimit = new RateLimit();

    @Data
    public static class RateLimit {
        /**
         * 两次请求之间的最小间隔。
         */
        private Long intervalMs = 1000L;

        /**
         * 并发上限。
         */
        private Integer maxConcurrency = 1;
    }

    public String apiKeyHeaderValue() {
        if (key == null || key.isBlank()) {
            return null;
        }
        return key;
    }
}

