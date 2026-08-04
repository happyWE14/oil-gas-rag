package com.wong.collector.application.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import lombok.Data;

@Data
public class PreAnalysisResultRequest {

    @NotNull
    private Boolean relevant;

    private Double score;

    @NotBlank
    private String analyzer;

    private String reason;

    /**
     * AI返回的原始JSON，便于后续复核/重放。
     */
    private String rawResult;

    /**
     * 当次判定使用的关键词快照，避免配置变更导致记录不可复现。
     */
    private String keywordsSnapshot;

    /**
     * 判定时使用的阈值。
     */
    private Double thresholdUsed;

    /**
     * 模型判断是否可提取材料/化学性质信息。
     */
    private Boolean hasExtractableMaterialInfo;
}
