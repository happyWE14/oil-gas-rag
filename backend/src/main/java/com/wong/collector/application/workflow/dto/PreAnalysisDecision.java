package com.wong.collector.application.workflow.dto;

import java.util.List;

/**
 * 预分析决策结果，包含置信度、关键词快照和原始JSON，便于后续复核与调参。
 */
public record PreAnalysisDecision(
        boolean relevant,
        double score,
        String reason,
        String analyzer,
        String rawResult,
        List<String> keywordsUsed,
        Double thresholdUsed,
        Boolean hasExtractableMaterialInfo) {
}
