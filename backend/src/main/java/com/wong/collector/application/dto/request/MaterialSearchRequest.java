package com.wong.collector.application.dto.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.Data;

@Data
public class MaterialSearchRequest {
    // 文本搜索字段
    private String materialName;
    private String paperTitle;

    // 指标过滤
    private String metricKey;
    private MetricRange metricRange;

    // 向量搜索字段（前端传递）
    private String queryText;      // 语义描述文本
    private float[] queryVector;   // 向量（2048维）
    private Double vectorWeight;   // 向量权重（默认0.7）
    private Double textWeight;     // 文本权重（默认0.3）

    // 年份范围
    private Integer yearFrom;
    private Integer yearTo;

    // 分页
    @Min(0)
    private int page = 0;

    @Min(1)
    @Max(200)
    private int size = 10;

    // 排序：relevance/confidence/year
    private String sortBy = "relevance";

    @Data
    public static class MetricRange {
        private Double min;
        private Double max;
    }
}
