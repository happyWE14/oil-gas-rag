package com.wong.collector.application.dto.search;

import java.util.Map;

public record Citation(
    String id,
    String paperId,
    String title,
    String snippet,
    String type,
    Map<String, Object> metadata
) {
    public static Citation paper(String paperId, String title, String snippet, double score) {
        return new Citation(
            "cit_" + paperId.hashCode(),
            paperId,
            title,
            snippet,
            "paper",
            Map.of("score", score)
        );
    }

    /**
     * 创建材料类型的 Citation。
     * 对 paperId/material 做防御性空值处理，避免 NPE。
     */
    public static Citation material(String paperId, String material, String property, 
                                     String value, String unit, double confidence, double score) {
        // 防御性空值处理
        String safePaperId = paperId != null ? paperId : "";
        String safeMaterial = material != null ? material : "Unknown";
        String safeProperty = property != null ? property : "";
        String safeValue = value != null ? value : "";
        String safeUnit = unit != null ? unit : "";
        
        String formattedTitle = safeMaterial + (!safeProperty.isEmpty() 
            ? " (" + safeProperty + ")" : "");
        
        String formattedSnippet;
        if (!safeValue.isEmpty()) {
            formattedSnippet = String.format("%s: %s%s", 
                !safeProperty.isEmpty() ? safeProperty : "Metric",
                safeValue,
                !safeUnit.isEmpty() ? " " + safeUnit : "");
        } else {
            formattedSnippet = safeProperty;
        }
        
        return new Citation(
            "cit_" + (safePaperId + safeMaterial).hashCode(),
            safePaperId,
            formattedTitle,
            formattedSnippet,
            "material",
            Map.of("confidence", confidence, "score", score,
                   "property", safeProperty,
                   "value", safeValue,
                   "unit", safeUnit)
        );
    }

    public static Citation metric(String paperId, String materialKey, String metricKey, 
                                   Double value, String unit, double confidence) {
        String title = materialKey + " - " + metricKey;
        String snippet = value != null 
            ? String.format("%s: %.2f%s", metricKey, value, unit != null ? " " + unit : "")
            : metricKey;
        return new Citation(
            "cit_" + (paperId + materialKey + metricKey).hashCode(),
            paperId,
            title,
            snippet,
            "metric",
            Map.of("metricKey", metricKey, "value", value != null ? value : 0.0, 
                   "unit", unit != null ? unit : "", "confidence", confidence)
        );
    }

    public static Citation observation(String paperId, String materialKey, String type,
                                        String content, double confidence, double score) {
        return new Citation(
            "cit_" + (paperId + materialKey + type).hashCode(),
            paperId,
            materialKey + " (" + type + ")",
            content,
            "observation",
            Map.of("type", type, "confidence", confidence, "score", score)
        );
    }
}
