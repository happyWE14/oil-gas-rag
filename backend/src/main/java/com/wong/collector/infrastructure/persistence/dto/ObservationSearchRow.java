package com.wong.collector.infrastructure.persistence.dto;

import lombok.Data;

@Data
public class ObservationSearchRow {
    private String paperId;
    private String materialKey;
    private String type;
    private String content;
    private Double confidence;
    private Double score;
}
