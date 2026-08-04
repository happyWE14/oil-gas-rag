package com.wong.collector.domain.search.model;

import lombok.Data;

@Data
public class MaterialSearchCriteria {
    private String materialName;
    private String paperTitle;
    private String metricKey;
    private MetricRange metricRange;
    private float[] queryVector;
    private Double vectorWeight;
    private Double textWeight;
    private Integer yearFrom;
    private Integer yearTo;
    private int page;
    private int size;

    // 新增：最小置信度过滤（0.0 - 1.0）
    private Float minConfidence;

    // 新增排序字段
    private String sortField;      // 如 "material.confidence", "paper.year"
    private String sortDirection;  // "asc" 或 "desc"

    @Data
    public static class MetricRange {
        private Double min;
        private Double max;
    }
}
