package com.wong.collector.infrastructure.config.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import lombok.Data;

@Data
@Component
@ConfigurationProperties(prefix = "deepseek.ocr")
public class DeepSeekOcrProperties {

    private String apiUrl = "https://api.modelverse.cn/v1/chat/completions";
    private String apiKey;
    private String model = "deepseek-ai/DeepSeek-OCR";
    private int timeoutSeconds = 60;
    /**
     * PDF 渲染 DPI（影响清晰度与开销）
     */
    private int renderDpi = 300;
}
