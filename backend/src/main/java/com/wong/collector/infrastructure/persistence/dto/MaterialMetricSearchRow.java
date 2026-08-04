package com.wong.collector.infrastructure.persistence.dto;

import lombok.Data;

@Data
public class MaterialMetricSearchRow {
    private String paperId;
    private String materialKey;
    private String metricKey;
    private String valueType;
    private Double valueNum;
    private Double valueMin;
    private Double valueMax;
    private String valueText;
    private String unit;
    private Double confidence;
}
