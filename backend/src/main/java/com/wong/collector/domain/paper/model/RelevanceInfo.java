package com.wong.collector.domain.paper.model;

import com.wong.collector.domain.common.exception.DomainException;
import lombok.Getter;

/**
 * 相关性分析结果。
 */
@Getter
public class RelevanceInfo {

    private final boolean relevant;
    private final double score;
    private final RelevanceAnalyzer analyzer;
    private final String reason;
    /**
     * 原始模型JSON结果，便于追溯。
     */
    private final String rawResult;
    /**
     * 判定时使用的关键词快照（JSON字符串）。
     */
    private final String keywordsSnapshot;
    /**
     * 判定阈值，便于复现。
     */
    private final Double thresholdUsed;
    /**
     * 模型是否认为可提取材料/化学性质信息。
     */
    private final Boolean hasExtractableMaterialInfo;

    private RelevanceInfo(boolean relevant, double score,
                          RelevanceAnalyzer analyzer, String reason,
                          String rawResult, String keywordsSnapshot,
                          Double thresholdUsed, Boolean hasExtractableMaterialInfo) {
        if (analyzer == null) {
            throw new DomainException("Analyzer required");
        }
        this.relevant = relevant;
        this.score = score;
        this.analyzer = analyzer;
        this.reason = reason;
        this.rawResult = rawResult;
        this.keywordsSnapshot = keywordsSnapshot;
        this.thresholdUsed = thresholdUsed;
        this.hasExtractableMaterialInfo = hasExtractableMaterialInfo;
    }

    public static RelevanceInfo of(boolean relevant, double score,
                                   RelevanceAnalyzer analyzer, String reason,
                                   String rawResult, String keywordsSnapshot,
                                   Double thresholdUsed, Boolean hasExtractableMaterialInfo) {
        return new RelevanceInfo(relevant, score, analyzer, reason,
            rawResult, keywordsSnapshot, thresholdUsed, hasExtractableMaterialInfo);
    }

}
