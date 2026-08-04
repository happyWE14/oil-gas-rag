package com.wong.collector.application.workflow.embedding;

import com.wong.collector.application.dto.request.EmbeddingCompletedRequest;
import com.wong.collector.application.service.PaperApplicationService;
import com.wong.collector.domain.common.exception.NotFoundException;
import com.wong.collector.domain.paper.model.Paper;
import com.wong.collector.domain.paper.model.PaperContent;
import com.wong.collector.domain.paper.model.PaperId;
import com.wong.collector.domain.paper.repository.PaperContentRepository;
import com.wong.collector.domain.paper.repository.PaperRepository;
import com.wong.collector.infrastructure.config.properties.EmbeddingBusinessProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.Embedding;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.EmbeddingRequest;
import org.springframework.ai.embedding.EmbeddingResponse;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class PaperEmbeddingExecutor {

    private final PaperRepository paperRepository;
    private final PaperApplicationService paperApplicationService;
    private final PaperContentRepository paperContentRepository;
    private final DocumentChunkSplitter documentChunkSplitter;
    private final EmbeddingBusinessProperties embeddingProperties;
    @Qualifier("documentEmbeddingModel")
    private final EmbeddingModel embeddingModel;

    public String embed(String paperId) {
        Paper paper = paperRepository.findById(PaperId.of(paperId))
            .orElseThrow(() -> new NotFoundException("Paper not found: " + paperId));
        // 允许不上传 Markdown，但正文必须存在
        PaperContent content = paperContentRepository.findLatest(paper.getId())
            .orElseThrow(() -> new IllegalStateException("正文未生成"));
        if (content.getContent() == null || content.getContent().isBlank()) {
            throw new IllegalStateException("正文为空");
        }
        List<Document> segments = documentChunkSplitter.split(content.getContent(), paper);
        List<EmbeddingCompletedRequest.ChunkDTO> chunkDTOs = buildChunks(segments);
        EmbeddingCompletedRequest request = new EmbeddingCompletedRequest();
        request.setChunks(chunkDTOs);
        paperApplicationService.completeEmbedding(paperId, request);
        return "chunks=" + chunkDTOs.size();
    }

    private List<EmbeddingCompletedRequest.ChunkDTO> buildChunks(List<Document> documents) {
        List<EmbeddingCompletedRequest.ChunkDTO> result = new ArrayList<>();
        List<String> batch = new ArrayList<>();
        int order = 0;
        int batchSize = Math.max(1, embeddingProperties.getChunking().getBatchSize());
        for (Document document : documents) {
            batch.add(document.getText());
            if (batch.size() >= batchSize) {
                result.addAll(callEmbedding(batch, order));
                order += batch.size();
                batch.clear();
            }
        }
        if (!batch.isEmpty()) {
            result.addAll(callEmbedding(batch, order));
        }
        return result;
    }

    private List<EmbeddingCompletedRequest.ChunkDTO> callEmbedding(List<String> texts, int startOrder) {
        EmbeddingResponse response = embeddingModel.call(new EmbeddingRequest(texts, null));
        List<Embedding> embeddings = response.getResults();
        if (embeddings == null || embeddings.size() != texts.size()) {
            int actual = embeddings == null ? 0 : embeddings.size();
            throw new IllegalStateException(
                "Embedding response count mismatch: expected " + texts.size() + ", got " + actual
            );
        }
        List<EmbeddingCompletedRequest.ChunkDTO> dtos = new ArrayList<>();
        for (int i = 0; i < texts.size(); i++) {
            EmbeddingCompletedRequest.ChunkDTO dto = new EmbeddingCompletedRequest.ChunkDTO();
            dto.setOrderNo(startOrder + i);
            dto.setContent(texts.get(i));
            dto.setEmbeddingModel(resolveModelName());
            float[] output = embeddings.get(i).getOutput();
            embeddingProperties.requireExpectedDimensions(output);
            List<Double> vector = new ArrayList<>(output.length);
            for (float value : output) {
                vector.add((double) value);
            }
            dto.setVector(vector);
            dtos.add(dto);
        }
        return dtos;
    }

    private String resolveModelName() {
        return embeddingProperties.getModelId().name();
    }
}
