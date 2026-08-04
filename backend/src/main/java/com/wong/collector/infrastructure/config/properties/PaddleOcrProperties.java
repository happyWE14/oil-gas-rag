package com.wong.collector.infrastructure.config.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import lombok.Data;

@Data
@Component
@ConfigurationProperties(prefix = "paddleocr")
public class PaddleOcrProperties {

    /**
     * PaddleOCR-VL 服务地址（完整 URL），指向 /layout-parsing。
     */
    private String apiUrl = "http://localhost:8000/layout-parsing";

    /**
     * aistudio 鉴权 token。
     * <p>
     * 会以请求头形式传递：Authorization: token {token}
     */
    private String token;

    /**
     * 0=PDF, 1=图片。
     */
    private int fileType = 0;

    /**
     * 是否启用文档方向分类。
     */
    private boolean useDocOrientationClassify = false;

    /**
     * 是否启用文档展平/去弯曲（Unwarping）。
     */
    private boolean useDocUnwarping = false;

    /**
     * 是否启用图表识别。
     */
    private boolean useChartRecognition = false;
}
