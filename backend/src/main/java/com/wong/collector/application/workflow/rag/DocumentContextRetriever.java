package com.wong.collector.application.workflow.rag;

import java.util.ArrayList;
import java.util.List;

import org.springframework.ai.embedding.Embedding;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.EmbeddingRequest;
import org.springframework.ai.embedding.EmbeddingResponse;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import com.wong.collector.infrastructure.config.properties.EmbeddingBusinessProperties;
import com.wong.collector.infrastructure.persistence.mapper.DocumentChunkMapper;
import com.wong.collector.infrastructure.persistence.po.DocumentChunkPO;

import lombok.RequiredArgsConstructor;

/** Retrieves semantically similar chunks inside one indexed document. */
@Component
@RequiredArgsConstructor
public class DocumentContextRetriever {

    public record ChunkHit(String chunkId, String content) {}
    public record IndexedDocument(
            String documentId,
            String title,
            String state,
            String embeddingModel,
            long chunkCount) {}

    private final DocumentChunkMapper documentChunkMapper;
    private final EmbeddingBusinessProperties embeddingProperties;
    @Qualifier("documentEmbeddingModel")
    private final EmbeddingModel embeddingModel;

    public List<IndexedDocument> listIndexedDocuments() {
        return documentChunkMapper.listIndexedDocuments().stream()
            .map(document -> new IndexedDocument(
                document.getDocumentId(),
                document.getTitle(),
                document.getState(),
                document.getEmbeddingModel(),
                document.getChunkCount() == null ? 0 : document.getChunkCount()))
            .toList();
    }

    public List<ChunkHit> topK(String documentId, String query, int topK) {
        if (documentId == null || documentId.isBlank()
                || query == null || query.isBlank() || topK <= 0) {
            return List.of();
        }
        List<Double> vector = embed(query);
        if (vector.isEmpty()) {
            return List.of();
        }
        List<DocumentChunkPO> hits = documentChunkMapper.searchTopKByVector(documentId, vector, topK);
        if (hits == null || hits.isEmpty()) {
            return List.of();
        }
        return hits.stream()
            .filter(po -> po.getId() != null && po.getContent() != null && !po.getContent().isBlank())
            .map(po -> new ChunkHit(po.getId().toString(), po.getContent()))
            .toList();
    }

    private List<Double> embed(String query) {
        EmbeddingResponse response = embeddingModel.call(new EmbeddingRequest(List.of(query), null));
        List<Embedding> embeddings = response.getResults();
        if (embeddings.isEmpty()) {
            return List.of();
        }
        float[] output = embeddings.get(0).getOutput();
        embeddingProperties.requireExpectedDimensions(output);
        List<Double> vector = new ArrayList<>(output.length);
        for (float value : output) {
            vector.add((double) value);
        }
        return vector;
    }
}
