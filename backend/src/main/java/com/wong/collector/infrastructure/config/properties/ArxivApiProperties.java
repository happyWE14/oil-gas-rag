package com.wong.collector.infrastructure.config.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * arXiv API 调用参数。
 */
@Data
@Component
@ConfigurationProperties(prefix = "arxiv.api")
public class ArxivApiProperties {

    /**
     * 单次最大返回条数。
     */
    private int maxResults = 200;

    private final RateLimit rateLimit = new RateLimit();

    @Data
    public static class RateLimit {
        private long intervalMs = 3000L;
        private int maxConcurrency = 1;
    }
}
