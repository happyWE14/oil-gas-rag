package com.wong.collector.infrastructure.repository;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import com.wong.collector.domain.material.model.MaterialGlobalStats;
import com.wong.collector.infrastructure.persistence.dto.MaterialGlobalStatsRow;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wong.collector.domain.common.model.PageResult;
import com.wong.collector.domain.material.model.MaterialPaperOccurrence;
import com.wong.collector.domain.material.model.MaterialPaperOccurrence.ChunkEvidence;
import com.wong.collector.domain.material.model.MaterialPaperOccurrence.PaperRef;
import com.wong.collector.domain.material.model.MaterialSummary;
import com.wong.collector.domain.material.repository.MaterialPaperQuery;
import com.wong.collector.domain.material.repository.MaterialQuery;
import com.wong.collector.domain.material.repository.MaterialRepository;
import com.wong.collector.infrastructure.persistence.dto.MaterialPaperRow;
import com.wong.collector.infrastructure.persistence.dto.MaterialSummaryRow;
import com.wong.collector.infrastructure.persistence.mapper.DocumentChunkMapper;
import com.wong.collector.infrastructure.persistence.mapper.MaterialExtractionMapper;
import com.wong.collector.infrastructure.persistence.po.DocumentChunkPO;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Repository
@RequiredArgsConstructor
public class MaterialRepositoryImpl implements MaterialRepository {

    private final MaterialExtractionMapper materialExtractionMapper;
    private final DocumentChunkMapper documentChunkMapper;
    private final ObjectMapper objectMapper;

    @Override
    @Transactional(readOnly = true)
    public PageResult<MaterialSummary> page(MaterialQuery query) {
        if (query == null) {
            throw new IllegalArgumentException("query is required");
        }

        long total = safeLong(materialExtractionMapper.countDistinctMaterialKey(query.q(), query.minConfidence()));
        if (total <= 0) {
            return PageResult.empty(query.page(), query.size());
        }
        long totalPages = PageResult.computeTotalPages(total, query.size());
        if (query.page() > totalPages) {
            return PageResult.of(List.of(), query.page(), query.size(), total);
        }

        List<MaterialSummaryRow> rows = materialExtractionMapper.pageMaterialSummary(
                query.q(),
                query.minConfidence(),
                query.size(),
                query.offset()
        );
        List<MaterialSummary> items = rows.stream()
                .filter(Objects::nonNull)
                .map(row -> new MaterialSummary(
                        row.getMaterialKey(),
                        row.getMaterial(),
                        safeLong(row.getPaperCount()),
                        safeLong(row.getOccurrenceCount()),
                        row.getMaxConfidence()
                ))
                .toList();
        return PageResult.of(items, query.page(), query.size(), total);
    }

    @Override
    @Transactional(readOnly = true)
    public PageResult<MaterialPaperOccurrence> pagePapers(MaterialPaperQuery query) {
        if (query == null) {
            throw new IllegalArgumentException("query is required");
        }

        long total = safeLong(materialExtractionMapper.countMaterialPapers(query.materialKey(), query.minConfidence()));
        if (total <= 0) {
            return PageResult.empty(query.page(), query.size());
        }
        long totalPages = PageResult.computeTotalPages(total, query.size());
        if (query.page() > totalPages) {
            return PageResult.of(List.of(), query.page(), query.size(), total);
        }

        List<MaterialPaperRow> rows = materialExtractionMapper.pageMaterialPapers(
                query.materialKey(),
                query.minConfidence(),
                query.size(),
                query.offset()
        );

        if (rows == null || rows.isEmpty()) {
            return PageResult.of(List.of(), query.page(), query.size(), total);
        }

        if (!query.includeChunks()) {
            List<MaterialPaperOccurrence> items = rows.stream()
                    .filter(Objects::nonNull)
                    .map(row -> toOccurrence(row, List.of()))
                    .toList();
            return PageResult.of(items, query.page(), query.size(), total);
        }

        List<List<Long>> chunkIdsPerRow = new ArrayList<>(rows.size());
        Set<Long> uniqueChunkIds = new LinkedHashSet<>();
        for (MaterialPaperRow row : rows) {
            List<Long> ids = extractChunkIds(row == null ? null : row.getDetailJson(), query.chunkLimit());
            chunkIdsPerRow.add(ids);
            uniqueChunkIds.addAll(ids);
        }

        // 关键修复：将 Long 转换为 String 传入，匹配数据库 varchar 类型
        Map<Long, DocumentChunkPO> chunkById = uniqueChunkIds.isEmpty()
                ? Map.of()
                : fetchChunksById(uniqueChunkIds).stream()
                .filter(po -> po != null && po.getId() != null)
                .collect(Collectors.toMap(
                        po -> Long.parseLong(po.getId().toString()),
                        po -> po,
                        (a, b) -> a
                ));

        List<MaterialPaperOccurrence> items = new ArrayList<>(rows.size());
        for (int i = 0; i < rows.size(); i++) {
            MaterialPaperRow row = rows.get(i);
            List<Long> ids = chunkIdsPerRow.get(i);
            List<ChunkEvidence> chunks = ids.stream()
                    .map(chunkById::get)
                    .filter(Objects::nonNull)
                    .map(po -> new ChunkEvidence(
                            po.getId().toString(),
                            po.getOrderNo(),
                            po.getContent(),
                            po.getEmbeddingModel()
                    ))
                    .toList();
            items.add(toOccurrence(row, chunks));
        }
        return PageResult.of(items, query.page(), query.size(), total);
    }

    @Override
    public MaterialGlobalStats getGlobalStats() {
        // 调用Mapper查询统计数据
        MaterialGlobalStatsRow row = materialExtractionMapper.selectGlobalStats();

        // 处理查询结果为null的情况（理论上不会为null，但做防御性编程）
        if (row == null) {
            return new MaterialGlobalStats(0L, 0L, 0L, 0.0);
        }

        // 将基础设施层DTO转换为领域模型
        return new MaterialGlobalStats(
                row.getTotalMaterials(),      // 材料种类数（去重）
                row.getTotalPapers(),         // 来源论文数（不同paper_id去重）
                row.getTotalOccurrences(),    // 提取记录总数
                row.getAvgConfidence()        // 平均置信度（0-1之间的小数）
        );
    }

    /**
     * 修复：将 Long ID 转为 String 传入，适配 document_chunk.id 为 varchar 的情况
     * 如果 document_chunk.id 实际是 bigint，请将此方法改回 selectBatchIds(new ArrayList<>(ids))
     */
    private List<DocumentChunkPO> fetchChunksById(Set<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return List.of();
        }
        // 转换为 String 列表，适配可能的 varchar 类型主键
        List<String> stringIds = ids.stream()
                .map(String::valueOf)
                .collect(Collectors.toList());
        return documentChunkMapper.selectByIds(stringIds);
    }

    private MaterialPaperOccurrence toOccurrence(MaterialPaperRow row, List<ChunkEvidence> chunks) {
        if (row == null) {
            throw new IllegalArgumentException("material row is required");
        }
        PaperRef paper = new PaperRef(
                row.getPaperId(),
                row.getTitle(),
                row.getAuthors(),
                row.getYearPublished(),
                row.getDoi(),
                row.getState(),
                row.getCitationCount(),
                row.getUpdatedAt()
        );
        return new MaterialPaperOccurrence(
                row.getMaterialKey(),
                row.getMaterial(),
                paper,
                row.getProperty(),
                row.getMethod(),
                row.getConfidence(),
                row.getDetailJson(),
                chunks
        );
    }

    private List<Long> extractChunkIds(String detailJson, int limit) {
        if (detailJson == null || detailJson.isBlank() || limit <= 0) {
            return List.of();
        }
        try {
            JsonNode root = objectMapper.readTree(detailJson);
            JsonNode idsNode = root == null ? null : root.get("source_chunk_ids");
            if (idsNode == null || !idsNode.isArray()) {
                return List.of();
            }
            LinkedHashSet<Long> dedup = new LinkedHashSet<>();
            for (JsonNode node : idsNode) {
                Long id = toLongId(node);
                if (id != null && id > 0) {
                    dedup.add(id);
                    if (dedup.size() >= limit) {
                        break;
                    }
                }
            }
            return List.copyOf(dedup);
        } catch (Exception ex) {
            return List.of();
        }
    }

    private Long toLongId(JsonNode node) {
        if (node == null || node.isNull()) {
            return null;
        }
        if (node.isNumber()) {
            return node.longValue();
        }
        if (node.isTextual()) {
            String text = node.asText();
            if (text == null || text.isBlank()) {
                return null;
            }
            try {
                return Long.parseLong(text.trim());
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }

    private long safeLong(Long value) {
        return value == null ? 0L : value;
    }
}
