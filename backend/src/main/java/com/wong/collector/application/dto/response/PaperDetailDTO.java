package com.wong.collector.application.dto.response;

import java.time.LocalDateTime;
import java.util.List;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class PaperDetailDTO {
    String paperId;
    String title;
    String authors;
    Integer yearPublished;
    String doi;
    String abstractContent;
    String downloadUrl;
    Integer citationCount;
    String state;
    DownloadInfoDTO downloadInfo;
    TransformInfoDTO transformInfo;
    List<ChunkDTO> chunks;
    List<MaterialDTO> materials;

    @Value
    @Builder
    public static class DownloadInfoDTO {
        String sourceUrl;
        String ossUrl;
        Long fileSize;
        String status;
        Integer tryTimes;
        LocalDateTime startedAt;
        LocalDateTime completedAt;
    }

    @Value
    @Builder
    public static class TransformInfoDTO {
        String provider;
        String status;
        String markdownPath;
        String errorMessage;
    }

    @Value
    @Builder
    public static class ChunkDTO {
        String chunkId;
        Integer orderNo;
        String content;
        String embeddingModel;
    }

    @Value
    @Builder
    public static class MaterialDTO {
        String material;
        String property;
        String method;
        Double confidence;
        String detailJson;
    }
}
