package com.wong.collector.application.workflow;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wong.collector.application.workflow.dto.MaterialIdentificationResult;
import com.wong.collector.application.workflow.dto.PaperRelevanceResult;
import com.wong.collector.application.workflow.util.ChunkIdNormalizer;

class PromptDtoParsingTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void paperRelevanceResult_deserializes_with_focusArea() throws Exception {
        String json = """
            {
              "relevanceScore": 0.8,
              "hasExtractableMaterialInfo": true,
              "focusArea": "fracturing",
              "supportingSignals": ["signal-1"],
              "uncertainties": []
            }
            """;
        PaperRelevanceResult result = objectMapper.readValue(json, PaperRelevanceResult.class);
        assertEquals(0.8d, result.getRelevanceScore(), 1e-9);
        assertEquals("fracturing", result.getFocusArea());
        assertTrue(result.getHasExtractableMaterialInfo());
        assertEquals(List.of("signal-1"), result.getSupportingSignals());
    }

    @Test
    void materialIdentificationResult_deserializes_prompt_schema() throws Exception {
        String json = """
            {
              "schema_version": "1.2",
              "has_materials": true,
              "materials": [
                {
                  "canonical_name": "partially hydrolyzed polyacrylamide",
                  "abbreviation": "HPAM",
                  "chemical_formula": null,
                  "aliases": ["聚丙烯酰胺"],
                  "category": "polymer",
                  "role": "target",
                  "is_primary": true,
                  "confidence": 0.91,
                  "chunk_ids": ["[ChunkId-123]", "ChunkId-00124", "125"]
                }
              ],
              "warnings": []
            }
            """;
        MaterialIdentificationResult result = objectMapper.readValue(json, MaterialIdentificationResult.class);
        assertNotNull(result.getMaterials());
        assertEquals(1, result.getMaterials().size());
        MaterialIdentificationResult.MaterialInfo info = result.getMaterials().get(0);
        assertEquals("partially hydrolyzed polyacrylamide", info.getName());
        assertEquals("HPAM", info.getAbbreviation());
        assertTrue(info.isPrimary());
        assertEquals(List.of("[ChunkId-123]", "ChunkId-00124", "125"), info.getChunkReferences());
    }

    @Test
    void materialIdentificationResult_deserializes_legacy_schema() throws Exception {
        String json = """
            {
              "hasMaterials": true,
              "materials": [
                {
                  "name": "HPG",
                  "formula": "C6H10O5",
                  "aliases": ["guar gum"],
                  "confidence": 0.7,
                  "is_primary": false,
                  "chunkReferences": ["123"]
                }
              ]
            }
            """;
        MaterialIdentificationResult result = objectMapper.readValue(json, MaterialIdentificationResult.class);
        assertNotNull(result.getMaterials());
        assertEquals(1, result.getMaterials().size());
        MaterialIdentificationResult.MaterialInfo info = result.getMaterials().get(0);
        assertEquals("HPG", info.getName());
        assertEquals("C6H10O5", info.getFormula());
        assertEquals(List.of("123"), info.getChunkReferences());
    }

    @Test
    void chunkIdNormalizer_normalizes_tokens() {
        assertEquals("123", ChunkIdNormalizer.normalize("[ChunkId-123]"));
        assertEquals("124", ChunkIdNormalizer.normalize("ChunkId-00124"));
        assertEquals("125", ChunkIdNormalizer.normalize("125"));
        assertNull(ChunkIdNormalizer.normalize("abc"));
        assertNull(ChunkIdNormalizer.normalize(""));
        assertNull(ChunkIdNormalizer.normalize(null));
    }
}

