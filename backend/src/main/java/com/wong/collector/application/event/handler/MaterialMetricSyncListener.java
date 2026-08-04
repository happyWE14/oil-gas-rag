package com.wong.collector.application.event.handler;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.wong.collector.domain.paper.event.PaperMaterialExtractedEvent;
import com.wong.collector.domain.paper.model.MaterialExtraction;
import com.wong.collector.infrastructure.persistence.mapper.MaterialMetricMapper;
import com.wong.collector.infrastructure.persistence.mapper.MaterialObservationMapper;
import com.wong.collector.infrastructure.persistence.po.MaterialMetricPO;
import com.wong.collector.infrastructure.persistence.po.MaterialObservationPO;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
@RequiredArgsConstructor
public class MaterialMetricSyncListener {

    private final MaterialMetricMapper materialMetricMapper;
    private final MaterialObservationMapper materialObservationMapper;

    // ✅ 自动注入 Spring Boot 默认的 ObjectMapper（Primary Bean）
    // 不需要 @Qualifier，因为 ElasticsearchConfig 中的 @Primary 已移除
    private final ObjectMapper objectMapper;

    // 分批大小，避免内存溢出
    private static final int BATCH_SIZE = 50;
    private static final int MAX_JSON_SIZE = 10 * 1024 * 1024; // 10MB 限制

    /**
     * 处理论文材料提取完成事件 - 异步处理，不影响主流程
     */
    @Async("materialSyncExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void handle(PaperMaterialExtractedEvent event) {
        String paperId = event.getPaperId().getValue();
        List<MaterialExtraction> materials = event.getMaterials();

        if (materials == null || materials.isEmpty()) {
            log.debug("No materials to sync for paper {}", paperId);
            return;
        }

        log.info("Starting sync {} materials to metric/observation tables for paper {}",
                materials.size(), paperId);

        try {
            // 幂等性处理：先删除该论文已存在的指标数据
            materialMetricMapper.deleteByPaperId(paperId);
            materialObservationMapper.deleteByPaperId(paperId);

            List<MaterialMetricPO> metricsBuffer = new ArrayList<>(BATCH_SIZE);
            List<MaterialObservationPO> observationsBuffer = new ArrayList<>(BATCH_SIZE);
            int totalMetrics = 0;
            int totalObservations = 0;

            for (MaterialExtraction material : materials) {
                try {
                    processSingleMaterial(paperId, material, metricsBuffer, observationsBuffer);

                    // 分批刷新，避免内存堆积
                    if (metricsBuffer.size() >= BATCH_SIZE) {
                        List<MaterialMetricPO> deduped = deduplicateMetrics(metricsBuffer);
                        materialMetricMapper.batchInsert(deduped);
                        totalMetrics += deduped.size();
                        metricsBuffer.clear();
                        log.debug("Flushed {} metrics to database for paper {}", deduped.size(), paperId);
                    }

                    if (observationsBuffer.size() >= BATCH_SIZE) {
                        materialObservationMapper.batchInsert(new ArrayList<>(observationsBuffer));
                        totalObservations += observationsBuffer.size();
                        observationsBuffer.clear();
                    }
                } catch (Exception e) {
                    log.error("Failed to process material {} for paper {}, skipping: {}",
                            material.getMaterial(), paperId, e.getMessage());
                }
            }

            // 插入剩余数据
            if (!metricsBuffer.isEmpty()) {
                List<MaterialMetricPO> deduped = deduplicateMetrics(metricsBuffer);
                materialMetricMapper.batchInsert(deduped);
                totalMetrics += deduped.size();
            }
            if (!observationsBuffer.isEmpty()) {
                materialObservationMapper.batchInsert(new ArrayList<>(observationsBuffer));
                totalObservations += observationsBuffer.size();
            }

            log.info("Successfully synced {} metrics and {} observations for paper {}",
                    totalMetrics, totalObservations, paperId);

        } catch (Exception e) {
            log.error("Fatal error syncing metrics for paper {}: {}", paperId, e.getMessage(), e);
            throw new RuntimeException("Metric sync failed for paper: " + paperId, e);
        }
    }

    /**
     * 去重 metrics，避免同一批次中出现重复键导致数据库错误
     */
    private List<MaterialMetricPO> deduplicateMetrics(List<MaterialMetricPO> metrics) {
        return metrics.stream()
                .collect(Collectors.toMap(
                        m -> {
                            String condStr = "";
                            if (m.getConditions() != null) {
                                try {
                                    condStr = objectMapper.writeValueAsString(m.getConditions());
                                } catch (JsonProcessingException e) {
                                    condStr = m.getConditions().toString();
                                }
                            }
                            return m.getPaperId() + "|" + m.getMaterialKey() + "|" + m.getMetricKey() + "|" + condStr;
                        },
                        m -> m,
                        (existing, replacement) -> {
                            log.warn("Duplicate metric found for {}|{}|{}, keeping one with confidence {}",
                                    existing.getPaperId(), existing.getMaterialKey(), existing.getMetricKey(),
                                    replacement.getConfidence());
                            return replacement.getConfidence() >= existing.getConfidence() ? replacement : existing;
                        }
                ))
                .values()
                .stream()
                .collect(Collectors.toList());
    }

    /**
     * 处理单个材料（存量数据迁移专用）
     */
    public void processMaterialForMigration(String paperId, MaterialExtraction material) {
        List<MaterialMetricPO> metricsBuffer = new ArrayList<>();
        List<MaterialObservationPO> observationsBuffer = new ArrayList<>();

        processSingleMaterial(paperId, material, metricsBuffer, observationsBuffer);

        log.debug("Migration for paper {}, material {}: extracted {} metrics, {} observations",
                paperId, material.getMaterial(), metricsBuffer.size(), observationsBuffer.size());

        if (!metricsBuffer.isEmpty()) {
            List<MaterialMetricPO> deduped = deduplicateMetrics(metricsBuffer);
            materialMetricMapper.batchInsert(deduped);
            log.debug("Inserted {} metrics for paper {}", deduped.size(), paperId);
        } else {
            log.warn("No metrics extracted for paper {}, material {}. detail_json may not contain valid metric fields.",
                    paperId, material.getMaterial());
        }

        if (!observationsBuffer.isEmpty()) {
            materialObservationMapper.batchInsert(observationsBuffer);
            log.debug("Inserted {} observations for paper {}", observationsBuffer.size(), paperId);
        }
    }

    private void processSingleMaterial(String paperId, MaterialExtraction material,
                                       List<MaterialMetricPO> metricsBuffer,
                                       List<MaterialObservationPO> observationsBuffer) {
        String materialKey = normalizeMaterialKey(material.getMaterial());
        String detailJson = material.getDetailJson();

        if (detailJson == null || detailJson.isBlank() || "null".equals(detailJson.trim())) {
            log.debug("Empty detail_json for material {} in paper {}, skipping", material.getMaterial(), paperId);
            return;
        }

        if (detailJson.length() > MAX_JSON_SIZE) {
            log.warn("Detail JSON too large for material {} in paper {}: {} chars, skipping",
                    material.getMaterial(), paperId, detailJson.length());
            return;
        }

        try {
            JsonNode root = objectMapper.readTree(detailJson);

            if (root == null) {
                log.warn("Parsed JSON root is null for material {} in paper {}, detail_json: {}",
                        material.getMaterial(), paperId,
                        detailJson.substring(0, Math.min(100, detailJson.length())));
                return;
            }

            int metricsBefore = metricsBuffer.size();
            extractMetrics(paperId, materialKey, root, material.getConfidence(), metricsBuffer);

            int obsBefore = observationsBuffer.size();
            extractObservations(paperId, materialKey, root, material.getConfidence(), observationsBuffer);

            if (metricsBuffer.size() > metricsBefore || observationsBuffer.size() > obsBefore) {
                log.debug("Extracted {} metrics and {} observations from material {} in paper {}",
                        metricsBuffer.size() - metricsBefore,
                        observationsBuffer.size() - obsBefore,
                        materialKey, paperId);
            }

        } catch (Exception e) {
            log.warn("Failed to parse detail_json for material {} in paper {}: {}",
                    material.getMaterial(), paperId, e.getMessage());
        }
    }

    private void extractMetrics(String paperId, String materialKey, JsonNode root,
                                double confidence, List<MaterialMetricPO> metrics) {
        if (root == null) {
            return;
        }

        JsonNode metricsNode = root.path("metrics");
        if (metricsNode.isMissingNode() || metricsNode.isEmpty()) {
            metricsNode = root.path("properties");
        }

        if (!metricsNode.isMissingNode() && !metricsNode.isEmpty()) {
            if (metricsNode.isArray()) {
                for (JsonNode metricNode : metricsNode) {
                    MaterialMetricPO po = parseMetricNode(paperId, materialKey, metricNode, confidence, root);
                    if (po != null) metrics.add(po);
                }
            } else if (metricsNode.isObject()) {
                metricsNode.fields().forEachRemaining(entry -> {
                    MaterialMetricPO po = parseMetricEntry(paperId, materialKey, entry.getKey(),
                            entry.getValue(), confidence, root);
                    if (po != null) metrics.add(po);
                });
            }
        }

        JsonNode additionalMetrics = root.path("additional_metrics");
        if (additionalMetrics.isArray()) {
            for (JsonNode metricNode : additionalMetrics) {
                MaterialMetricPO po = parseAdditionalMetricNode(paperId, materialKey, metricNode, confidence);
                if (po != null) metrics.add(po);
            }
        }

        JsonNode coreMetrics = root.path("core_metrics");
        if (coreMetrics.isObject()) {
            coreMetrics.fields().forEachRemaining(entry -> {
                if (!entry.getValue().isEmpty()) {
                    MaterialMetricPO po = parseCoreMetricEntry(paperId, materialKey, entry.getKey(), entry.getValue(), confidence);
                    if (po != null) metrics.add(po);
                }
            });
        }

        extractDirectMetrics(paperId, materialKey, root, confidence, metrics);
    }

    private void extractDirectMetrics(String paperId, String materialKey, JsonNode root,
                                      double confidence, List<MaterialMetricPO> metrics) {
        if (root == null) return;

        String[] commonMetricFields = {"viscosity", "apparent_viscosity", "density", "temperature",
                "concentration", "molecular_weight", "shear_rate"};

        for (String field : commonMetricFields) {
            JsonNode valueNode = root.path(field);
            if (!valueNode.isMissingNode() && !valueNode.isNull()) {
                MaterialMetricPO po = new MaterialMetricPO();
                po.setPaperId(paperId);
                po.setMaterialKey(materialKey);
                po.setMetricKey(normalizeMetricKey(field));
                po.setConfidence(confidence);
                po.setChunkIds(extractChunkIds(root));
                po.setConditions(new HashMap<>());
                po.setCreateTime(LocalDateTime.now());
                po.setUpdateTime(LocalDateTime.now());
                po.setValueType("text");

                if (valueNode.isNumber()) {
                    po.setValueType("number");
                    po.setValueNum(valueNode.doubleValue());
                } else if (valueNode.isTextual()) {
                    String text = valueNode.asText();
                    Double parsed = tryParseNumber(text);
                    if (parsed != null) {
                        po.setValueType("number");
                        po.setValueNum(parsed);
                    } else {
                        po.setValueType("text");
                        po.setValueText(text);
                    }
                } else {
                    po.setValueType("text");
                    po.setValueText(valueNode.toString());
                }
                metrics.add(po);
            }
        }
    }

    private MaterialMetricPO parseMetricNode(String paperId, String materialKey, JsonNode node,
                                             double confidence, JsonNode root) {
        if (node == null) return null;

        String metricKey = getTextOrNull(node, "name", "key", "metric", "property");
        if (metricKey == null || metricKey.isBlank()) return null;

        MaterialMetricPO po = new MaterialMetricPO();
        po.setPaperId(paperId);
        po.setMaterialKey(materialKey);
        po.setMetricKey(normalizeMetricKey(metricKey));
        po.setConfidence(confidence);
        po.setChunkIds(extractChunkIds(root));
        po.setConditions(new HashMap<>());
        po.setCreateTime(LocalDateTime.now());
        po.setUpdateTime(LocalDateTime.now());
        po.setValueType("text");

        JsonNode valueNode = node.path("value");
        JsonNode minNode = node.path("min");
        JsonNode maxNode = node.path("max");

        if (!minNode.isMissingNode() && !maxNode.isMissingNode()) {
            po.setValueType("range");
            po.setValueMin(minNode.isNumber() ? minNode.doubleValue() : tryParseNumber(minNode.asText()));
            po.setValueMax(maxNode.isNumber() ? maxNode.doubleValue() : tryParseNumber(maxNode.asText()));
        } else if (!valueNode.isMissingNode()) {
            if (valueNode.isNumber()) {
                po.setValueType("number");
                po.setValueNum(valueNode.doubleValue());
            } else {
                String text = valueNode.asText();
                Double parsed = tryParseNumber(text);
                if (parsed != null) {
                    po.setValueType("number");
                    po.setValueNum(parsed);
                } else {
                    po.setValueType("text");
                    po.setValueText(text);
                }
            }
        } else {
            po.setValueType("text");
            po.setValueText(node.toString());
        }

        po.setUnit(getTextOrNull(node, "unit", "units"));
        po.setQualifier(getTextOrNull(node, "qualifier", "modifier"));

        JsonNode conditionsNode = node.path("conditions");
        if (!conditionsNode.isMissingNode() && conditionsNode.isObject()) {
            po.setConditions(convertJsonNodeToMap(conditionsNode));
        }

        return po;
    }

    private MaterialMetricPO parseMetricEntry(String paperId, String materialKey, String key,
                                              JsonNode value, double confidence, JsonNode root) {
        if (value == null) return null;

        MaterialMetricPO po = new MaterialMetricPO();
        po.setPaperId(paperId);
        po.setMaterialKey(materialKey);
        po.setMetricKey(normalizeMetricKey(key));
        po.setConfidence(confidence);
        po.setChunkIds(extractChunkIds(root));
        po.setConditions(new HashMap<>());
        po.setCreateTime(LocalDateTime.now());
        po.setUpdateTime(LocalDateTime.now());
        po.setValueType("text");

        if (value.isNumber()) {
            po.setValueType("number");
            po.setValueNum(value.doubleValue());
        } else if (value.isTextual()) {
            String text = value.asText();
            Double parsed = tryParseNumber(text);
            if (parsed != null) {
                po.setValueType("number");
                po.setValueNum(parsed);
            } else {
                po.setValueType("text");
                po.setValueText(text);
            }
        } else if (value.isObject()) {
            ObjectNode copy = value.deepCopy();
            copy.put("name", key);
            return parseMetricNode(paperId, materialKey, copy, confidence, root);
        } else {
            return null;
        }

        return po;
    }

    private MaterialMetricPO parseAdditionalMetricNode(String paperId, String materialKey,
                                                       JsonNode node, double defaultConfidence) {
        if (node == null) return null;

        try {
            String metricKey = getTextOrNull(node, "name");
            if (metricKey == null || metricKey.isBlank()) {
                return null;
            }

            MaterialMetricPO po = new MaterialMetricPO();
            po.setPaperId(paperId);
            po.setMaterialKey(materialKey);
            po.setMetricKey(normalizeMetricKey(metricKey));
            po.setUnit(getTextOrNull(node, "unit"));

            JsonNode confNode = node.path("confidence");
            po.setConfidence(confNode.isNumber() ? confNode.doubleValue() : defaultConfidence);

            po.setChunkIds(extractChunkIdsFromNode(node.path("chunk_ids")));
            po.setCreateTime(LocalDateTime.now());
            po.setUpdateTime(LocalDateTime.now());
            po.setConditions(new HashMap<>());
            po.setValueType("text");

            JsonNode valueNode = node.path("value");
            if (!valueNode.isMissingNode() && valueNode.isObject()) {
                String valueType = getTextOrNull(valueNode, "type");

                if ("list".equals(valueType)) {
                    po.setValueType("text");
                    JsonNode listNode = valueNode.path("list");
                    if (listNode.isArray()) {
                        List<String> values = new ArrayList<>();
                        for (JsonNode item : listNode) {
                            if (item.isNumber()) {
                                values.add(String.valueOf(item.doubleValue()));
                            } else if (item.isTextual()) {
                                values.add(item.asText());
                            }
                        }
                        po.setValueText(String.join(",", values));
                    } else {
                        String rawText = getTextOrNull(valueNode, "raw");
                        if (rawText != null) {
                            po.setValueText(rawText);
                        }
                    }
                } else if ("number".equals(valueType)) {
                    po.setValueType("number");
                    JsonNode numNode = valueNode.path("num");
                    if (numNode.isNumber()) {
                        po.setValueNum(numNode.doubleValue());
                    }
                } else if ("range".equals(valueType)) {
                    po.setValueType("range");
                    JsonNode minNode = valueNode.path("min");
                    JsonNode maxNode = valueNode.path("max");
                    if (minNode.isNumber()) po.setValueMin(minNode.doubleValue());
                    if (maxNode.isNumber()) po.setValueMax(maxNode.doubleValue());
                } else {
                    po.setValueType("text");
                    String rawText = getTextOrNull(valueNode, "raw");
                    if (rawText != null) {
                        po.setValueText(rawText);
                    } else {
                        po.setValueText(valueNode.toString());
                    }
                }

                po.setQualifier(getTextOrNull(valueNode, "qualifier"));
            }

            JsonNode conditionNode = node.path("condition");
            if (!conditionNode.isMissingNode() && conditionNode.isObject()) {
                po.setConditions(convertJsonNodeToMap(conditionNode));
            }

            if (po.getValueType() == null) {
                po.setValueType("text");
            }
            if (!Arrays.asList("number", "range", "text").contains(po.getValueType())) {
                po.setValueType("text");
            }

            return po;
        } catch (Exception e) {
            log.warn("Failed to parse additional_metric for {}: {}", materialKey, e.getMessage());
            return null;
        }
    }

    private Map<String, Object> convertJsonNodeToMap(JsonNode node) {
        if (node == null || !node.isObject()) {
            return new HashMap<>();
        }
        try {
            return objectMapper.convertValue(node, new TypeReference<Map<String, Object>>() {});
        } catch (Exception e) {
            log.warn("Failed to convert JsonNode to Map: {}", e.getMessage());
            return new HashMap<>();
        }
    }

    private MaterialMetricPO parseCoreMetricEntry(String paperId, String materialKey,
                                                  String key, JsonNode value, double confidence) {
        if (value == null || !value.isArray() || value.isEmpty()) {
            return null;
        }

        MaterialMetricPO po = new MaterialMetricPO();
        po.setPaperId(paperId);
        po.setMaterialKey(materialKey);
        po.setMetricKey(normalizeMetricKey(key));
        po.setConfidence(confidence);
        po.setCreateTime(LocalDateTime.now());
        po.setUpdateTime(LocalDateTime.now());
        po.setConditions(new HashMap<>());
        po.setValueType("text");

        JsonNode firstItem = value.get(0);
        if (firstItem.isNumber()) {
            po.setValueType("number");
            po.setValueNum(firstItem.doubleValue());
        } else if (firstItem.isTextual()) {
            String text = firstItem.asText();
            Double parsed = tryParseNumber(text);
            if (parsed != null) {
                po.setValueType("number");
                po.setValueNum(parsed);
            } else {
                po.setValueType("text");
                po.setValueText(text);
            }
        }

        return po;
    }

    private void extractObservations(String paperId, String materialKey, JsonNode root,
                                     double confidence, List<MaterialObservationPO> observations) {
        if (root == null) return;

        String[] observationFields = {"formulation", "result_summary", "measurement_method",
                "comparison", "limitation", "notes", "remarks", "description"};

        for (String field : observationFields) {
            JsonNode node = root.path(field);
            if (!node.isMissingNode() && !node.isNull()) {
                addObservation(observations, paperId, materialKey, field, node, confidence, null);
            }
        }

        JsonNode observationsNode = root.path("observations");
        if (observationsNode.isArray()) {
            for (JsonNode obs : observationsNode) {
                String type = getTextOrNull(obs, "type");
                String text = getTextOrNull(obs, "text");
                String[] chunkIds = extractChunkIdsFromNode(obs.path("chunk_ids"));
                Double conf = obs.path("confidence").isNumber() ?
                        obs.path("confidence").doubleValue() : confidence;

                if (text != null && !text.isBlank()) {
                    addObservation(observations, paperId, materialKey,
                            type != null ? type : "general",
                            obs.path("text"), conf, chunkIds);
                }
            }
        }

        JsonNode passagesNode = root.path("source_passages");
        if (passagesNode.isArray()) {
            for (int i = 0; i < passagesNode.size(); i++) {
                JsonNode passage = passagesNode.get(i);
                if (passage.isTextual()) {
                    String content = passage.asText();
                    if (content.length() > 50) {
                        MaterialObservationPO po = new MaterialObservationPO();
                        po.setPaperId(paperId);
                        po.setMaterialKey(materialKey);
                        po.setType("source_passage");
                        po.setContent(content.length() > 4000 ? content.substring(0, 4000) : content);
                        po.setConfidence(confidence);
                        po.setCreateTime(LocalDateTime.now());
                        observations.add(po);
                    }
                }
            }
        }
    }

    private void addObservation(List<MaterialObservationPO> observations, String paperId,
                                String materialKey, String type, JsonNode contentNode,
                                double confidence, String[] chunkIds) {
        if (contentNode == null) return;

        String content = contentNode.isTextual() ? contentNode.asText() : contentNode.toString();
        if (content == null || content.isBlank() || content.length() < 3) {
            return;
        }

        MaterialObservationPO po = new MaterialObservationPO();
        po.setPaperId(paperId);
        po.setMaterialKey(materialKey);
        po.setType(normalizeObservationType(type));
        po.setContent(content.length() > 4000 ? content.substring(0, 4000) : content);
        po.setConfidence(confidence);
        po.setChunkIds(chunkIds != null ? chunkIds : extractChunkIds(null));
        po.setCreateTime(LocalDateTime.now());
        observations.add(po);
    }

    private String normalizeMaterialKey(String material) {
        if (material == null) return "unknown";
        return material.toLowerCase().trim().replaceAll("\\s+", "_");
    }

    private String normalizeMetricKey(String key) {
        if (key == null) return "unknown";
        return key.toLowerCase().trim()
                .replaceAll("[^a-z0-9_]", "_")
                .replaceAll("_+", "_")
                .replaceAll("^_|_$", "");
    }

    private String normalizeObservationType(String type) {
        if (type == null) return "general";
        String normalized = type.toLowerCase().trim().replaceAll("\\s+", "_");
        return switch (normalized) {
            case "notes", "remarks" -> "result_summary";
            case "description" -> "formulation";
            default -> normalized;
        };
    }

    private String getTextOrNull(JsonNode node, String... keys) {
        if (node == null) return null;
        for (String key : keys) {
            JsonNode child = node.path(key);
            if (!child.isMissingNode() && child.isTextual()) {
                String text = child.asText();
                if (text != null && !text.isBlank()) {
                    return text;
                }
            }
        }
        return null;
    }

    private String[] extractChunkIds(JsonNode root) {
        if (root == null) return null;
        JsonNode idsNode = root.path("source_chunk_ids");
        if (idsNode.isMissingNode() || !idsNode.isArray() || idsNode.isEmpty()) {
            return null;
        }
        List<String> ids = new ArrayList<>();
        for (JsonNode id : idsNode) {
            if (id.isTextual()) {
                ids.add(id.asText());
            } else if (id.isNumber()) {
                ids.add(String.valueOf(id.longValue()));
            }
        }
        return ids.isEmpty() ? null : ids.toArray(new String[0]);
    }

    private String[] extractChunkIdsFromNode(JsonNode chunkIdsNode) {
        if (chunkIdsNode.isMissingNode() || !chunkIdsNode.isArray()) {
            return null;
        }
        List<String> ids = new ArrayList<>();
        for (JsonNode id : chunkIdsNode) {
            if (id.isTextual()) {
                ids.add(id.asText());
            } else if (id.isNumber()) {
                ids.add(String.valueOf(id.longValue()));
            }
        }
        return ids.isEmpty() ? null : ids.toArray(new String[0]);
    }

    private Double tryParseNumber(String text) {
        if (text == null || text.isBlank()) return null;
        try {
            String cleaned = text.replaceAll("[^0-9.\\-+eE]", "");
            if (cleaned.isEmpty()) return null;
            return Double.parseDouble(cleaned);
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
