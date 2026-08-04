package com.wong.collector.domain.material.model;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Material -> Paper occurrence read model.
 * <p>
 * Note: This is a query projection (not an aggregate root).
 */
public record MaterialPaperOccurrence(
    String materialKey,
    String material,
    PaperRef paper,
    String property,
    String method,
    Double confidence,
    String detailJson,
    List<ChunkEvidence> chunks
) {

    public MaterialPaperOccurrence {
        if (materialKey == null || materialKey.isBlank()) {
            throw new IllegalArgumentException("materialKey is required");
        }
        if (material == null || material.isBlank()) {
            throw new IllegalArgumentException("material is required");
        }
        if (paper == null) {
            throw new IllegalArgumentException("paper is required");
        }
        chunks = chunks == null ? List.of() : List.copyOf(chunks);
    }

    public record PaperRef(
        String paperId,
        String title,
        String authors,
        Integer yearPublished,
        String doi,
        String state,
        Integer citationCount,
        LocalDateTime updatedAt
    ) {

        public PaperRef {
            if (paperId == null || paperId.isBlank()) {
                throw new IllegalArgumentException("paperId is required");
            }
            if (title == null || title.isBlank()) {
                throw new IllegalArgumentException("paper title is required");
            }
            if (state == null || state.isBlank()) {
                throw new IllegalArgumentException("paper state is required");
            }
        }
    }

    public record ChunkEvidence(
        String chunkId,
        Integer orderNo,
        String content,
        String embeddingModel
    ) {
        public ChunkEvidence {
            if (chunkId == null || chunkId.isBlank()) {
                throw new IllegalArgumentException("chunkId is required");
            }
        }
    }
}

