package com.wong.collector.infrastructure.external.paddleocr.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class PaddleOcrRequest {
    private String file;
    private Integer fileType;

    /**
     * 是否启用文档方向分类。
     */
    private Boolean useDocOrientationClassify;

    /**
     * 是否启用文档展平/去弯曲（Unwarping）。
     */
    private Boolean useDocUnwarping;

    /**
     * 是否启用图表识别。
     */
    private Boolean useChartRecognition;
}
