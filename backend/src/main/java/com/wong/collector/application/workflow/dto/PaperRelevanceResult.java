package com.wong.collector.application.workflow.dto;

import java.util.Collections;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import lombok.Data;

/**
 * 论文相关性分析结果。
 * <p>
 * 模型只返回置信度与支撑/不确定信号，是否相关由业务阈值控制，避免把裁决权交给模型。
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class PaperRelevanceResult {

    /**
     * 相关性置信度（0-1），越高越相关。
     */
    private double relevanceScore;

    /**
     * 模型判断的关注领域（可选）。
     */
    private String focusArea;

    /**
     * 相关性支撑信号列表（命中关键词、场景等）。
     */
    private List<String> supportingSignals = Collections.emptyList();

    /**
     * 不确定性/降分原因。
     */
    private List<String> uncertainties = Collections.emptyList();

    /**
     * 是否可预期提取到材料/化学性质等结构化信息。
     */
    private Boolean hasExtractableMaterialInfo;

    /**
     * 清洗后的原始JSON，便于落库追溯。
     */
    private String rawPayload;

    /**
     * 业务阈值判断。
     */
    public boolean above(double threshold) {
        return relevanceScore >= threshold;
    }
}
