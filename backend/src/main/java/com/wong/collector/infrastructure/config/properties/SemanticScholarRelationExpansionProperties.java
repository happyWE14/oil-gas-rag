package com.wong.collector.infrastructure.config.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import lombok.Data;

/**
 * Semantic Scholar citation/reference 扩展配置。
 * <p>
 * 设计上保留多跳（maxDepth）能力，但当前只执行单跳（maxDepth=1）。
 */
@Data
@Component
@ConfigurationProperties(prefix = "semantic-scholar.relation-expansion")
public class SemanticScholarRelationExpansionProperties {

    /**
     * 是否启用 citation/reference 扩展导入。
     */
    private boolean enabled = false;

    /**
     * 单方向（citations / references）最多抓取多少条。
     */
    private int limit = 50;

    /**
     * 预留多跳扩展能力；当前只支持 1。
     */
    private int maxDepth = 1;
}

