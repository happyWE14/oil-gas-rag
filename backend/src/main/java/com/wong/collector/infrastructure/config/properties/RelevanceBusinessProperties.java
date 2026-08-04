package com.wong.collector.infrastructure.config.properties;

import java.util.Arrays;
import java.util.List;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 论文预分析配置。
 */
@Data
@Component
@ConfigurationProperties(prefix = "paper.relevance")
public class RelevanceBusinessProperties {

    /**
     * 默认判定关键词。
     */
    private List<String> keywords = List.of();

    /**
     * 判断提示词。
     */
    private String paperRelevancePrompt = "You are a domain expert. Return true if the paper matches the keywords, else false.";

    /**
     * 用户输入模板，支持 {title}/{abstract}/{keywords} 占位符。
     */
    private String paperRelevanceUserTemplate = "Title: {title}\nAbstract: {abstract}\nKeywords: {keywords}";

    /**
     * 业务阈值（0-1），模型只返回置信度，最终判定由阈值控制。
     */
    private Double decisionThreshold = 0.6;
}
