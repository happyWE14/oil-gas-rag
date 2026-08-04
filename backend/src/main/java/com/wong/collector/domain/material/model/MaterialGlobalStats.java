// com.wong.collector.domain.material.model.MaterialGlobalStats
package com.wong.collector.domain.material.model;

public record MaterialGlobalStats(
        long totalMaterials,      // 不同材料名称数量（去重）
        long totalPapers,         // 不同论文ID数量（去重）- 来自 material_extraction
        long totalOccurrences,    // 总提取记录数
        double avgConfidence      // 平均置信度
) {}
