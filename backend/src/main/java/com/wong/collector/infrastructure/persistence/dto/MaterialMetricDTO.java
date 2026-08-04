package com.wong.collector.infrastructure.persistence.dto;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public class MaterialMetricDTO {
    private Long id;
    private String paperId;
    private String materialKey;
    private String metricKey;
    private String valueType;  // number, range, text
    private Double valueNum;
    private Double valueMin;
    private Double valueMax;
    private String valueText;
    private String unit;
    private String qualifier;
    private String conditions;  // JSON 字符串
    private Double confidence;
    private String[] chunkIds;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
