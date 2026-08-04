// com.wong.collector.infrastructure.persistence.dto.MaterialGlobalStatsRow
package com.wong.collector.infrastructure.persistence.dto;

import lombok.Data;

@Data
public class MaterialGlobalStatsRow {
    private long totalMaterials;
    private long totalPapers;
    private long totalOccurrences;
    private double avgConfidence;
}
