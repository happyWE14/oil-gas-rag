package com.wong.collector.infrastructure.persistence.dto;

import lombok.Data;

@Data
public class MaterialSearchRow {
    private String paperId;
    private String material;
    private String property;
    private Double confidence;
    private Double score;
    private String value;
    private String unit;
    private String method;
}
