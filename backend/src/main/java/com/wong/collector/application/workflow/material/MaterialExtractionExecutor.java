package com.wong.collector.application.workflow.material;

import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;

import com.wong.collector.application.dto.request.MaterialExtractionRequest;
import com.wong.collector.application.dto.request.MaterialExtractionRequest.MaterialDTO;
import com.wong.collector.application.service.PaperApplicationService;
import com.wong.collector.application.workflow.dto.MaterialIdentificationResult;
import com.wong.collector.application.workflow.rag.DocumentContextRetriever;
import com.wong.collector.application.workflow.util.ChunkIdNormalizer;
import com.wong.collector.domain.common.exception.NotFoundException;
import com.wong.collector.domain.paper.model.DocumentChunk;
import com.wong.collector.domain.paper.model.Paper;
import com.wong.collector.domain.paper.model.PaperId;
import com.wong.collector.domain.paper.repository.PaperRepository;
import com.wong.collector.infrastructure.config.properties.MaterialExtractionBusinessProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class MaterialExtractionExecutor {

    private record ChunkSnippet(String chunkId, String content) {}

    private final PaperRepository paperRepository;
    private final PaperApplicationService paperApplicationService;
    private final MaterialAnalysisAgent analysisService;
    private final MaterialExtractionBusinessProperties properties;
    private final ObjectMapper objectMapper;
    private final DocumentContextRetriever contextRetriever;

    public String extract(String paperId) {
        Paper paper = paperRepository.findById(PaperId.of(paperId))
            .orElseThrow(() -> new NotFoundException("Paper not found"));
        List<DocumentChunk> chunks = paper.getChunks();
        String idQuery = buildIdentificationQuery(paper);
        List<ChunkSnippet> identifyContext = contextRetriever.topK(paperId, idQuery, properties.getIdentification().getTopKChunks()).stream()
            .map(hit -> new ChunkSnippet(hit.chunkId(), hit.content()))
            .collect(Collectors.toList());
        if (identifyContext.isEmpty()) {
            identifyContext = fallbackContext(chunks, properties.getIdentification().getTopKChunks());
        }
        String content = String.join("\n\n---\n\n", identifyContext.stream().map(this::formatForPrompt).collect(Collectors.toList()));

        MaterialIdentificationResult identificationResult = analysisService.identify(paperId, content);
        if (identificationResult.getMaterials() == null || identificationResult.getMaterials().isEmpty()) {
            paperApplicationService.completeExtraction(paperId, emptyRequest());
            return "materials=0";
        }
        List<MaterialDTO> dtos = identificationResult.getMaterials().stream()
            .filter(Objects::nonNull)
            .filter(mat -> mat.getConfidence() >= properties.getIdentification().getMinConfidence())
            .map(material -> buildMaterialDTO(paperId, material, chunks))
            .filter(Objects::nonNull)
            .collect(Collectors.toList());
        if (dtos.isEmpty()) {
            paperApplicationService.completeExtraction(paperId, emptyRequest());
            return "materials=0";
        }
        MaterialExtractionRequest request = new MaterialExtractionRequest();
        request.setMaterials(dtos);
        paperApplicationService.completeExtraction(paperId, request);
        return "materials=" + dtos.size();
    }

    private MaterialExtractionRequest emptyRequest() {
        MaterialExtractionRequest req = new MaterialExtractionRequest();
        req.setMaterials(List.of());
        return req;
    }

    private MaterialDTO buildMaterialDTO(String paperId, MaterialIdentificationResult.MaterialInfo material,
                                         List<DocumentChunk> chunks) {
        String materialName = resolveMaterialName(material);
        if (materialName == null) {
            return null;
        }
        MaterialDTO dto = new MaterialDTO();
        dto.setMaterial(materialName);
        List<ChunkSnippet> context = buildContext(paperId, material, chunks);
        String propertiesJson = analysisService.extractRaw(
            paperId,
            materialName,
            String.join("\n\n---\n\n", context.stream().map(this::formatForPrompt).collect(Collectors.toList()))
        );
        dto.setDetailJson(attachSources(propertiesJson, context));
        dto.setMethod("AI");
        dto.setProperty(material.getFormula());
        dto.setConfidence(material.getConfidence());
        return dto;
    }

    /**
     * 根据识别阶段的 chunkReferences 选取上下文；若为空则按顺序截取 topK。
     */
    private List<ChunkSnippet> buildContext(String paperId, MaterialIdentificationResult.MaterialInfo material,
                                      List<DocumentChunk> chunks) {
        int limit = Math.max(1, properties.getChunking().getPropertyTopKChunks());
        String query = buildMaterialQuery(material);
        List<ChunkSnippet> fromVector = contextRetriever.topK(paperId, query, limit).stream()
            .map(hit -> new ChunkSnippet(hit.chunkId(), hit.content()))
            .collect(Collectors.toList());
        if (!fromVector.isEmpty()) {
            return fromVector;
        }
        if (material.getChunkReferences() != null && !material.getChunkReferences().isEmpty()) {
            return material.getChunkReferences().stream()
                .map(ChunkIdNormalizer::normalize)
                .filter(ref -> ref != null && !ref.isBlank())
                .distinct()
                .map(ref -> findChunkSnippet(ref, chunks))
                .filter(snippet -> snippet != null && snippet.content() != null && !snippet.content().isBlank())
                .limit(limit)
                .collect(Collectors.toList());
        }
        return fallbackContext(chunks, limit);
    }

    private ChunkSnippet findChunkSnippet(String chunkId, List<DocumentChunk> chunks) {
        if (chunkId == null || chunkId.isBlank()) {
            return null;
        }
        return chunks.stream()
            .filter(c -> c.getId() != null && chunkId.equals(c.getId().toString()))
            .findFirst()
            .map(c -> new ChunkSnippet(chunkId, c.getContent()))
            .orElse(null);
    }

    /**
     * 将源片段附加到 detailJson，方便前端展示参考原文，并包含 source_chunk_ids 作为兜底索引。
     */
    private String attachSources(String rawJson, List<ChunkSnippet> sources) {
        ObjectNode node = ensureObjectNode(rawJson);
        ArrayNode passages = objectMapper.createArrayNode();
        ArrayNode ids = objectMapper.createArrayNode();
        if (sources != null) {
            sources.forEach(snippet -> {
                if (snippet == null) {
                    return;
                }
                if (snippet.chunkId() != null && !snippet.chunkId().isBlank()) {
                    ids.add(snippet.chunkId());
                }
                if (snippet.content() != null && !snippet.content().isBlank()) {
                    passages.add(snippet.content());
                }
            });
        }
        node.set("source_chunk_ids", ids);
        node.set("source_passages", passages);
        try {
            return objectMapper.writeValueAsString(node);
        } catch (Exception ex) {
            return node.toString();
        }
    }

    private ObjectNode ensureObjectNode(String rawJson) {
        if (rawJson == null) {
            rawJson = "";
        }
        try {
            JsonNode parsed = objectMapper.readTree(rawJson);
            if (parsed instanceof ObjectNode node) {
                return node;
            }
            ObjectNode wrapper = objectMapper.createObjectNode();
            wrapper.set("raw_json", parsed);
            return wrapper;
        } catch (Exception ex) {
            ObjectNode wrapper = objectMapper.createObjectNode();
            wrapper.put("parse_error", true);
            wrapper.put("raw_text", rawJson);
            return wrapper;
        }
    }

    private List<ChunkSnippet> fallbackContext(List<DocumentChunk> chunks, int limit) {
        return chunks.stream()
            .filter(c -> c.getId() != null && c.getContent() != null && !c.getContent().isBlank())
            .map(c -> new ChunkSnippet(c.getId().toString(), c.getContent()))
            .limit(limit)
            .collect(Collectors.toList());
    }

    private String formatForPrompt(ChunkSnippet snippet) {
        // 让模型可稳定引用真实 chunkId。
        return "[ChunkId-" + snippet.chunkId() + "]\n" + snippet.content();
    }

    private String buildIdentificationQuery(Paper paper) {
        StringBuilder sb = new StringBuilder();
        sb.append(paper.getTitle().value());
        if (paper.getAbstractContent() != null) {
            sb.append(" ").append(paper.getAbstractContent());
        }
        return sb.toString();
    }

    private String buildMaterialQuery(MaterialIdentificationResult.MaterialInfo material) {
        StringBuilder sb = new StringBuilder();
        String name = resolveMaterialName(material);
        if (name != null) {
            sb.append(name);
        }
        if (material.getAbbreviation() != null && !material.getAbbreviation().isBlank()) {
            sb.append(" ").append(material.getAbbreviation());
        }
        if (material.getFormula() != null) {
            sb.append(" ").append(material.getFormula());
        }
        if (material.getAliases() != null) {
            material.getAliases().forEach(alias -> sb.append(" ").append(alias));
        }
        return sb.toString();
    }

    private String resolveMaterialName(MaterialIdentificationResult.MaterialInfo material) {
        if (material == null) {
            return null;
        }
        if (material.getName() != null && !material.getName().isBlank()) {
            return material.getName();
        }
        if (material.getAbbreviation() != null && !material.getAbbreviation().isBlank()) {
            return material.getAbbreviation();
        }
        if (material.getAliases() != null) {
            for (String alias : material.getAliases()) {
                if (alias != null && !alias.isBlank()) {
                    return alias;
                }
            }
        }
        return null;
    }
}
