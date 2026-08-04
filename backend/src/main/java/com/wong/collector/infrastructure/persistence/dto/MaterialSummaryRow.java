package com.wong.collector.infrastructure.persistence.dto;

import lombok.Data;

/**
 * Persistence projection row for material summary aggregation.
 */
@Data
public class MaterialSummaryRow {

    private String materialKey;
    private String material;
    private Long paperCount;
    private Long occurrenceCount;
    private Double maxConfidence;
}

