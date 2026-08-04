package com.wong.collector.application.dto.response;

import java.time.LocalDateTime;
import java.util.List;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class MaterialPaperDTO {

    String materialKey;
    String material;
    PaperRefDTO paper;
    String property;
    String method;
    Double confidence;
    String detailJson;
    List<ChunkDTO> chunks;

    @Value
    @Builder
    public static class PaperRefDTO {
        String paperId;
        String title;
        String authors;
        Integer yearPublished;
        String doi;
        String state;
        Integer citationCount;
        LocalDateTime updatedAt;
    }

    @Value
    @Builder
    public static class ChunkDTO {
        String chunkId;
        Integer orderNo;
        String content;
        String embeddingModel;
    }
}

