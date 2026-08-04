package com.wong.collector.application.service.search;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wong.collector.domain.search.repository.MaterialSearchRepository;
import com.wong.collector.infrastructure.persistence.po.DocumentChunkPO;
import com.wong.collector.infrastructure.persistence.po.MaterialExtractionPO;
import com.wong.collector.infrastructure.persistence.po.MaterialMetricPO;
import com.wong.collector.infrastructure.persistence.po.MaterialObservationPO;
import com.wong.collector.infrastructure.persistence.po.PaperPO;
import com.wong.collector.infrastructure.persistence.repository.DocumentChunkRepository;
import com.wong.collector.infrastructure.persistence.repository.MaterialExtractionRepository;
import com.wong.collector.infrastructure.persistence.repository.MaterialMetricRepository;
import com.wong.collector.infrastructure.persistence.repository.MaterialObservationRepository;
import com.wong.collector.infrastructure.persistence.repository.PaperRepository;
import com.wong.collector.infrastructure.search.document.MaterialEsDocument;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.DigestUtils;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class MaterialSearchService {

    private final MaterialExtractionRepository extractionRepo;
    private final MaterialMetricRepository metricRepo;
    private final MaterialObservationRepository observationRepo;
    private final DocumentChunkRepository chunkRepo;
    private final PaperRepository paperRepo;
    private final MaterialSearchRepository searchRepo;

    // ✅ 修复：注入 Spring 默认的 ObjectMapper（用于普通 JSON 处理）
    // 不需要蛇形命名策略，标准 JSON 处理即可
    private final ObjectMapper objectMapper;

    // ISO-8601 日期时间格式器
    private static final DateTimeFormatter ISO_FORMATTER = DateTimeFormatter.ISO_LOCAL_DATE_TIME;

    @Transactional(readOnly = true)
    public void syncByPaperId(String paperId) {
        log.info("Starting sync for paper: {}", paperId);

        PaperPO paper = paperRepo.selectById(paperId);
        if (paper == null) {
            log.warn("Paper {} not found", paperId);
            return;
        }

        List<MaterialExtractionPO> extractions = extractionRepo.selectList(
                new QueryWrapper<MaterialExtractionPO>()
                        .eq("paper_id", paperId)
                        .isNotNull("material")
                        .ne("material", "")
        );

        if (extractions.isEmpty()) {
            log.warn("No material extractions found for paper: {}", paperId);
            return;
        }

        List<MaterialEsDocument> documents = new ArrayList<>();

        for (MaterialExtractionPO extraction : extractions) {
            String materialKey = normalizeKey(extraction.getMaterial());

            List<MaterialMetricPO> metricPOs = metricRepo.selectList(
                    new QueryWrapper<MaterialMetricPO>()
                            .eq("paper_id", paperId)
                            .eq("material_key", materialKey)
            );

            List<MaterialObservationPO> observationPOs = observationRepo.selectList(
                    new QueryWrapper<MaterialObservationPO>()
                            .eq("paper_id", paperId)
                            .eq("material_key", materialKey)
            );

            MaterialEsDocument doc = buildDocument(
                    paper,
                    extraction,
                    metricPOs,
                    observationPOs
            );

            documents.add(doc);
        }

        if (!documents.isEmpty()) {
            searchRepo.saveAll(documents);
            log.info("Successfully synced {} documents for paper {}", documents.size(), paperId);
        }
    }

    private MaterialEsDocument buildDocument(
            PaperPO paper,
            MaterialExtractionPO extraction,
            List<MaterialMetricPO> metricPOs,
            List<MaterialObservationPO> observationPOs) {

        String docId = paper.getPaperId() + "_" + normalizeKey(extraction.getMaterial());

        MaterialEsDocument.PaperInfo paperInfo = MaterialEsDocument.PaperInfo.builder()
                .paperId(paper.getPaperId())
                .title(paper.getTitle())
                .authors(paper.getAuthors())
                .year(paper.getYearPublished())
                .doi(paper.getDoi())
                .abstractContent(paper.getAbstractContent())
                .build();

        MaterialEsDocument.MaterialInfo materialInfo = MaterialEsDocument.MaterialInfo.builder()
                .materialKey(normalizeKey(extraction.getMaterial()))
                .materialName(extraction.getMaterial())
                .confidence(extraction.getConfidence() != null ?
                        extraction.getConfidence().floatValue() : 0.8f)
                .build();

        List<MaterialEsDocument.Metric> metrics = metricPOs.stream()
                .map(this::convertMetric)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());

        List<MaterialEsDocument.Observation> observations = observationPOs.stream()
                .map(this::convertObservation)
                .collect(Collectors.toList());

        List<MaterialEsDocument.ChunkInfo> chunks = loadChunksForPaper(paper.getPaperId());

        // 构建 SyncMetadata，使用 String 类型的 ISO 日期格式
        String dataHash = generateDataHash(paper, extraction, metricPOs, observationPOs);
        String syncTimeStr = LocalDateTime.now().format(ISO_FORMATTER);

        MaterialEsDocument.SyncMetadata syncMetadata = MaterialEsDocument.SyncMetadata.builder()
                .version(System.currentTimeMillis())
                .syncTime(syncTimeStr)  // String 类型，避免 Jackson 序列化问题
                .dataHash(dataHash)
                .build();

        return MaterialEsDocument.builder()
                .docId(docId)
                .paper(paperInfo)
                .material(materialInfo)
                .metrics(metrics.isEmpty() ? null : metrics)
                .observations(observations.isEmpty() ? null : observations)
                .chunks(chunks.isEmpty() ? null : chunks)
                .syncMetadata(syncMetadata)
                .build();
    }

    /**
     * 生成数据哈希，用于版本控制
     */
    private String generateDataHash(PaperPO paper, MaterialExtractionPO extraction,
                                    List<MaterialMetricPO> metrics,
                                    List<MaterialObservationPO> observations) {
        StringBuilder sb = new StringBuilder();
        sb.append(paper.getPaperId()).append("|");
        sb.append(extraction.getMaterial()).append("|");
        sb.append(metrics.size()).append("|");
        sb.append(observations.size());

        return DigestUtils.md5DigestAsHex(sb.toString().getBytes(StandardCharsets.UTF_8));
    }

    /**
     * 转换指标数据（添加 qualifier 字段映射）
     */
    private MaterialEsDocument.Metric convertMetric(MaterialMetricPO po) {
        if (po == null) return null;

        // 处理 conditions：将 String JSON 解析为 Map
        Map<String, Object> conditionsMap = parseConditions(po.getConditions());

        MaterialEsDocument.Metric.MetricBuilder builder = MaterialEsDocument.Metric.builder()
                .metricKey(po.getMetricKey())
                .metricName(po.getMetricKey())
                .valueType(po.getValueType())
                .unit(po.getUnit())
                .qualifier(po.getQualifier())  // 添加 qualifier 字段映射
                .conditions(conditionsMap)
                .confidence(po.getConfidence() != null ? po.getConfidence().floatValue() : 1.0f);

        if ("number".equals(po.getValueType())) {
            builder.valueNum(po.getValueNum());
        } else if ("range".equals(po.getValueType())) {
            builder.valueMin(po.getValueMin());
            builder.valueMax(po.getValueMax());
        } else if ("text".equals(po.getValueType())) {
            builder.valueText(po.getValueText());
        }

        return builder.build();
    }

    /**
     * 解析 conditions 字段（支持 String 和 Map 两种类型）
     */
    private Map<String, Object> parseConditions(Object conditions) {
        if (conditions == null) {
            return null;
        }

        try {
            if (conditions instanceof String) {
                String jsonStr = (String) conditions;
                if (jsonStr.isEmpty() || "{}".equals(jsonStr)) {
                    return null;
                }
                return objectMapper.readValue(jsonStr, new TypeReference<Map<String, Object>>() {});
            } else if (conditions instanceof Map) {
                @SuppressWarnings("unchecked")
                Map<String, Object> map = (Map<String, Object>) conditions;
                return map;
            }
        } catch (Exception e) {
            log.warn("解析 conditions 失败: {}, error: {}", conditions, e.getMessage());
        }

        return null;
    }

    private MaterialEsDocument.Observation convertObservation(MaterialObservationPO po) {
        if (po == null) return null;

        return MaterialEsDocument.Observation.builder()
                .type(po.getType())
                .content(po.getContent())
                .confidence(po.getConfidence() != null ? po.getConfidence().floatValue() : 1.0f)
                .build();
    }

    /**
     * 加载Chunks（修复：id 转 String）
     */
    private List<MaterialEsDocument.ChunkInfo> loadChunksForPaper(String paperId) {
        List<DocumentChunkPO> chunkPOs = chunkRepo.selectList(
                new QueryWrapper<DocumentChunkPO>()
                        .eq("paper_id", paperId)
                        .orderByAsc("order_no")
                        .last("LIMIT 20")
        );

        return chunkPOs.stream()
                .map(chunk -> MaterialEsDocument.ChunkInfo.builder()
                        .chunkId(chunk.getId() != null ? chunk.getId().toString() : null)
                        .content(chunk.getContent())
                        .orderNo(chunk.getOrderNo())
                        .build())
                .collect(Collectors.toList());
    }

    private String normalizeKey(String material) {
        return material == null ? "" : material.toLowerCase().replaceAll("\\s+", "_");
    }
}
