package com.wong.collector.application.dto.request;

import java.util.List;

import jakarta.validation.constraints.NotEmpty;

import lombok.Data;

@Data
public class EmbeddingCompletedRequest {

    @NotEmpty
    private List<ChunkDTO> chunks;

    @Data
    public static class ChunkDTO {
        private Integer orderNo;
        private String content;
        private String embeddingModel;
        private List<Double> vector;
    }
}
