// com.wong.collector.application.dto.response.MaterialGlobalStatsDTO
package com.wong.collector.application.dto.response;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class MaterialGlobalStatsDTO {
    private long totalMaterials;
    private long totalPapers;
    private long totalOccurrences;
    private double avgConfidence;  // 百分比形式，如 77.5
}
