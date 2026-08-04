package com.wong.collector.infrastructure.config.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * CORE API 基础配置。
 */
@Data
@Component
@ConfigurationProperties(prefix = "core.api")
public class CoreApiProperties {

    /**
     * API 访问密钥。
     */
    private String key;

    /**
     * 单次查询最大条目。
     */
    private Integer maxResults = 50;

    private final RateLimit rateLimit = new RateLimit();

    @Data
    public static class RateLimit {
        private Long intervalMs = 1000L;
        private Integer maxConcurrency = 3;
    }

    public String buildAuthHeader() {
        return "Bearer " + key;
    }
}
