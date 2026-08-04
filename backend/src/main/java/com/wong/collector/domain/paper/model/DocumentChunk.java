package com.wong.collector.domain.paper.model;

import java.util.List;

import com.wong.collector.domain.common.exception.DomainException;

import lombok.Getter;

/**
 * 文档分块实体。
 */
@Getter
public class DocumentChunk {

    private final Long id;
    private final int orderNo;
    private final String content;
    private final PaperEmbeddingModel model;
    private final List<Double> vector;

    private DocumentChunk(Long id, int orderNo, String content,
                          PaperEmbeddingModel model, List<Double> vector) {
        if (id != null && id <= 0) {
            throw new DomainException("Chunk id must be > 0");
        }
        if (orderNo < 0) {
            throw new DomainException("Chunk order must be >= 0");
        }
        if (content == null || content.isBlank()) {
            throw new DomainException("Chunk content required");
        }
        this.id = id;
        this.orderNo = orderNo;
        this.content = content;
        this.model = model;
        this.vector = vector;
    }

    public static DocumentChunk of(int orderNo, String content,
                                   PaperEmbeddingModel model, List<Double> vector) {
        return new DocumentChunk(null, orderNo, content, model, vector);
    }

    public static DocumentChunk restore(Long id, int orderNo, String content,
                                        PaperEmbeddingModel model, List<Double> vector) {
        return new DocumentChunk(id, orderNo, content, model, vector);
    }

}
