package com.wong.collector.application.dto.response;

import com.wong.collector.domain.common.model.PageResult;

public record KeywordSearchResponse(
    String query,
    Results results,
    Metadata metadata
) {
    public record Results(
        PageResult<PaperHit> papers,
        PageResult<MaterialHit> materials
    ) {}

    public record PaperHit(
        String paperId,
        String title,
        String authors,
        Integer yearPublished,
        String paperSource,
        String status,
        double score
    ) {}

    public record MaterialHit(
        String paperId,
        String material,
        String property,
        Double confidence,
        double score
    ) {}

    public record Metadata(long searchTimeMs) {}
}
