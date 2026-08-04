package com.wong.collector.infrastructure.config.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import com.wong.collector.domain.paper.model.TransformProvider;

import lombok.Data;

@Data
@Component
@ConfigurationProperties(prefix = "transform")
public class TransformBusinessProperties {
    /**
     * OCR/转换提供方，默认 DeepSeek OCR。
     */
    private TransformProvider provider = TransformProvider.PADDLE_OCR;
    /**
     * 是否将转换得到的 Markdown 上传 OSS（正文已入库，可选）。
     */
    private boolean saveMarkdown = false;
}
