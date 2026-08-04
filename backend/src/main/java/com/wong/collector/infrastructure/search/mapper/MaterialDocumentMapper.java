package com.wong.collector.infrastructure.search.mapper;

import com.wong.collector.infrastructure.persistence.po.DocumentChunkPO;
import com.wong.collector.infrastructure.persistence.po.MaterialExtractionPO;
import com.wong.collector.infrastructure.persistence.po.MaterialObservationPO;
import com.wong.collector.infrastructure.persistence.po.PaperPO;
import com.wong.collector.infrastructure.persistence.repository.DocumentChunkRepository;
import com.wong.collector.infrastructure.persistence.repository.MaterialObservationRepository;
import com.wong.collector.infrastructure.persistence.repository.PaperRepository;
import com.wong.collector.infrastructure.search.document.MaterialEsDocument;
import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.mapstruct.AfterMapping;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.mapstruct.Named;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.util.CollectionUtils;

import java.time.LocalDateTime;
import java.math.BigDecimal;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Mapper(componentModel = "spring", imports = {LocalDateTime.class})
public abstract class MaterialDocumentMapper {

    @Autowired
    protected MaterialObservationRepository observationRepo;

    @Autowired
    protected DocumentChunkRepository chunkRepo;

    @Autowired
    protected PaperRepository paperRepo;

    @Autowired
    protected ObjectMapper objectMapper;

    /**
     * 主映射方法
     */
    @Mapping(target = "docId", source = ".", qualifiedByName = "generateDocId")
    @Mapping(target = "paper", ignore = true)  // 在 AfterMapping 中填充
    @Mapping(target = "material.materialKey", source = "material", qualifiedByName = "normalizeKey")
    @Mapping(target = "material.materialName", source = "material")
    @Mapping(target = "material.confidence", source = "confidence", qualifiedByName = "bigDecimalToFloat")
    @Mapping(target = "metrics", ignore = true)
    @Mapping(target = "observations", ignore = true)
    @Mapping(target = "chunks", ignore = true)  // 添加缺失的字段
    @Mapping(target = "embeddingVector", ignore = true)
    @Mapping(target = "syncMetadata", expression = "java(createSyncMetadata())")
    @Mapping(target = "fullTextSearch", ignore = true)  // 添加缺失的字段
    public abstract MaterialEsDocument toDocument(MaterialExtractionPO materialExtractionPO);

    /**
     * 生成文档 ID
     */
    @Named("generateDocId")
    public String generateDocId(MaterialExtractionPO po) {
        return po.getPaperId() + "_" + normalizeKey(po.getMaterial());
    }

    /**
     * 标准化材料 Key
     */
    @Named("normalizeKey")
    public String normalizeKey(String material) {
        return material == null ? "" : material.toLowerCase().replaceAll("\\s+", "_");
    }

    /**
     * BigDecimal 转 Float
     */
    @Named("bigDecimalToFloat")
    public Float bigDecimalToFloat(BigDecimal confidence) {
        return confidence != null ? confidence.floatValue() : 0.8f;  // 提供默认值
    }

    /**
     * 创建同步元数据
     */
    public MaterialEsDocument.SyncMetadata createSyncMetadata() {
        return MaterialEsDocument.SyncMetadata.builder()
                .version(System.currentTimeMillis())
                .syncTime(String.valueOf(LocalDateTime.parse(String.valueOf(LocalDateTime.now()))))
                .build();
    }

    /**
     * 后置处理：填充复杂关联数据
     */
    @AfterMapping
    protected void enrichDocument(MaterialExtractionPO materialExtractionPO, @MappingTarget MaterialEsDocument doc) {
        String materialKey = normalizeKey(materialExtractionPO.getMaterial());

        // 1. 填充论文详细信息
        enrichPaperInfo(materialExtractionPO, doc);

        // 2. 填充 Observations
        enrichObservations(materialExtractionPO, doc, materialKey);

        // 3. 解析 Metrics
        enrichMetrics(materialExtractionPO, doc);

        // 4. 填充 Chunks
        enrichChunks(materialExtractionPO, doc);
    }

    /**
     * 填充论文详细信息
     * 修复：PaperMeta -> PaperInfo
     */
    private void enrichPaperInfo(MaterialExtractionPO po, MaterialEsDocument doc) {
        try {
            PaperPO paper = paperRepo.selectById(po.getPaperId());
            if (paper != null) {
                // ✅ 修复：使用 PaperInfo 而不是 PaperMeta
                MaterialEsDocument.PaperInfo paperInfo = MaterialEsDocument.PaperInfo.builder()
                        .paperId(paper.getPaperId())
                        .title(paper.getTitle())
                        .authors(paper.getAuthors())
                        .year(paper.getYearPublished())
                        .doi(paper.getDoi())
                        .abstractContent(paper.getAbstractContent())
                        .build();

                doc.setPaper(paperInfo);
                log.debug("论文信息已填充: {} - {}", paper.getPaperId(), paper.getTitle());
            } else {
                log.warn("未找到论文信息: paperId={}", po.getPaperId());
                // 降级处理
                MaterialEsDocument.PaperInfo fallback = MaterialEsDocument.PaperInfo.builder()
                        .paperId(po.getPaperId())
                        .build();
                doc.setPaper(fallback);
            }
        } catch (Exception e) {
            log.error("查询论文信息失败: {}", po.getPaperId(), e);
            MaterialEsDocument.PaperInfo fallback = MaterialEsDocument.PaperInfo.builder()
                    .paperId(po.getPaperId())
                    .build();
            doc.setPaper(fallback);
        }
    }

    /**
     * 填充观察记录
     * 修复：ObservationEs -> Observation
     */
    private void enrichObservations(MaterialExtractionPO po, MaterialEsDocument doc, String materialKey) {
        try {
            List<MaterialObservationPO> observationPOs = observationRepo.selectList(
                    new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<MaterialObservationPO>()
                            .eq("paper_id", po.getPaperId())
                            .eq("material_key", materialKey)
            );

            if (!CollectionUtils.isEmpty(observationPOs)) {
                // ✅ 修复：使用 Observation 而不是 ObservationEs
                List<MaterialEsDocument.Observation> observations = observationPOs.stream()
                        .map(this::convertObservation)
                        .collect(Collectors.toList());
                doc.setObservations(observations);
            }
        } catch (Exception e) {
            log.warn("Failed to enrich observations for {}: {}", po.getPaperId(), e.getMessage());
        }
    }

    /**
     * 转换观察记录
     * 修复：ObservationEs -> Observation
     */
    private MaterialEsDocument.Observation convertObservation(MaterialObservationPO po) {
        // ✅ 修复：使用 Observation 而不是 ObservationEs
        MaterialEsDocument.Observation obs = MaterialEsDocument.Observation.builder()
                .type(po.getType())
                .content(po.getContent())
                .confidence(po.getConfidence() != null ? po.getConfidence().floatValue() : 1.0f)
                .build();

        return obs;
    }

    /**
     * 解析并填充指标数据
     * 修复：MetricEs -> Metric
     */
    @SneakyThrows
    private void enrichMetrics(MaterialExtractionPO po, MaterialEsDocument doc) {
        if (po.getDetailJson() == null || po.getDetailJson().isEmpty()) return;

        Map<String, Object> map = objectMapper.readValue(po.getDetailJson(), Map.class);
        if (map.containsKey("core_metrics")) {  // 注意：实际字段名可能是 core_metrics
            Map<String, List<Map<String, Object>>> coreMetrics = (Map<String, List<Map<String, Object>>>) map.get("core_metrics");

            List<MaterialEsDocument.Metric> metrics = new ArrayList<>();
            for (Map.Entry<String, List<Map<String, Object>>> entry : coreMetrics.entrySet()) {
                String metricKey = entry.getKey();
                for (Map<String, Object> m : entry.getValue()) {
                    metrics.add(convertMetric(m, metricKey));
                }
            }
            doc.setMetrics(metrics);
        }
    }

    /**
     * 转换指标
     * 修复：MetricEs -> Metric
     */
    private MaterialEsDocument.Metric convertMetric(Map<String, Object> m, String metricKey) {
        // ✅ 修复：使用 Metric 而不是 MetricEs
        MaterialEsDocument.Metric metric = MaterialEsDocument.Metric.builder()
                .metricKey(metricKey)
                .metricName(metricKey)  // 如果没有 name 就用 key
                .build();

        // 处理 value 对象
        if (m.containsKey("value")) {
            Map<String, Object> valueMap = (Map<String, Object>) m.get("value");
            String valueType = (String) valueMap.get("type");
            metric.setValueType(valueType);

            if ("number".equals(valueType) && valueMap.containsKey("num")) {
                metric.setValueNum(((Number) valueMap.get("num")).doubleValue());
            } else if ("range".equals(valueType)) {
                if (valueMap.containsKey("min")) {
                    metric.setValueMin(((Number) valueMap.get("min")).doubleValue());
                }
                if (valueMap.containsKey("max")) {
                    metric.setValueMax(((Number) valueMap.get("max")).doubleValue());
                }
            } else if ("text".equals(valueType) && valueMap.containsKey("raw")) {
                metric.setValueText((String) valueMap.get("raw"));
            }
        }

        // unit
        if (m.containsKey("unit")) {
            metric.setUnit((String) m.get("unit"));
        }

        // conditions
        if (m.containsKey("condition")) {
            metric.setConditions((Map<String, Object>) m.get("condition"));
        }

        // confidence
        if (m.containsKey("confidence")) {
            metric.setConfidence(((Number) m.get("confidence")).floatValue());
        }

        return metric;
    }

    /**
     * 填充 Chunks
     */
    private void enrichChunks(MaterialExtractionPO po, MaterialEsDocument doc) {
        try {
            List<DocumentChunkPO> chunkPOs = chunkRepo.selectList(
                    new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<DocumentChunkPO>()
                            .eq("paper_id", po.getPaperId())
                            .orderByAsc("order_no")
                            .last("LIMIT 20")
            );

            if (!CollectionUtils.isEmpty(chunkPOs)) {
                List<MaterialEsDocument.ChunkInfo> chunks = chunkPOs.stream()
                        .map(chunk -> MaterialEsDocument.ChunkInfo.builder()
                                .chunkId(String.valueOf(chunk.getId()))
                                .content(chunk.getContent())
                                .orderNo(chunk.getOrderNo())
                                .build())
                        .collect(Collectors.toList());
                doc.setChunks(chunks);
            }
        } catch (Exception e) {
            log.warn("Failed to enrich chunks for {}: {}", po.getPaperId(), e.getMessage());
        }
    }
}
