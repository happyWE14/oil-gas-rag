package com.wong.collector.application.service;

import com.wong.collector.application.dto.response.MaterialGlobalStatsDTO;
import com.wong.collector.domain.material.model.MaterialGlobalStats;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.wong.collector.application.dto.response.MaterialPaperDTO;
import com.wong.collector.application.dto.response.MaterialSummaryDTO;
import com.wong.collector.domain.common.model.PageResult;
import com.wong.collector.domain.material.model.MaterialPaperOccurrence;
import com.wong.collector.domain.material.model.MaterialSummary;
import com.wong.collector.domain.material.repository.MaterialPaperQuery;
import com.wong.collector.domain.material.repository.MaterialQuery;
import com.wong.collector.domain.material.repository.MaterialRepository;

import lombok.RequiredArgsConstructor;

/**
 * Material query application service.
 */
@Service
@RequiredArgsConstructor
public class MaterialApplicationService {

    private final MaterialRepository materialRepository;

    @Transactional(readOnly = true)
    public PageResult<MaterialSummaryDTO> pageMaterials(String q, Double minConfidence, int page, int size) {
        MaterialQuery query = new MaterialQuery(q, minConfidence, page, size);
        return materialRepository.page(query).map(this::toSummaryDTO);
    }

    @Transactional(readOnly = true)
    public PageResult<MaterialPaperDTO> pageMaterialPapers(String material,
                                                           Double minConfidence,
                                                           boolean includeChunks,
                                                           int chunkLimit,
                                                           int page,
                                                           int size) {
        MaterialPaperQuery query = new MaterialPaperQuery(material, minConfidence, includeChunks, chunkLimit, page, size);
        return materialRepository.pagePapers(query).map(this::toPaperDTO);
    }

    private MaterialSummaryDTO toSummaryDTO(MaterialSummary summary) {
        return MaterialSummaryDTO.builder()
            .materialKey(summary.materialKey())
            .material(summary.material())
            .paperCount(summary.paperCount())
            .occurrenceCount(summary.occurrenceCount())
            .maxConfidence(summary.maxConfidence())
            .build();
    }

    private MaterialPaperDTO toPaperDTO(MaterialPaperOccurrence occurrence) {
        return MaterialPaperDTO.builder()
            .materialKey(occurrence.materialKey())
            .material(occurrence.material())
            .paper(MaterialPaperDTO.PaperRefDTO.builder()
                .paperId(occurrence.paper().paperId())
                .title(occurrence.paper().title())
                .authors(occurrence.paper().authors())
                .yearPublished(occurrence.paper().yearPublished())
                .doi(occurrence.paper().doi())
                .state(occurrence.paper().state())
                .citationCount(occurrence.paper().citationCount())
                .updatedAt(occurrence.paper().updatedAt())
                .build())
            .property(occurrence.property())
            .method(occurrence.method())
            .confidence(occurrence.confidence())
            .detailJson(occurrence.detailJson())
            .chunks(occurrence.chunks().stream()
                .map(chunk -> MaterialPaperDTO.ChunkDTO.builder()
                    .chunkId(chunk.chunkId())
                    .orderNo(chunk.orderNo())
                    .content(chunk.content())
                    .embeddingModel(chunk.embeddingModel())
                    .build())
                .toList())
            .build();
    }

    /**
     * 获取全局统计数据
     */
    @Transactional(readOnly = true)
    public MaterialGlobalStatsDTO getGlobalStats() {
        MaterialGlobalStats stats = materialRepository.getGlobalStats();
        return MaterialGlobalStatsDTO.builder()
                .totalMaterials(stats.totalMaterials())
                .totalPapers(stats.totalPapers())
                .totalOccurrences(stats.totalOccurrences())
                .avgConfidence(Math.round(stats.avgConfidence() * 1000) / 10.0) // 转换为百分比并保留1位小数
                .build();
    }
}

