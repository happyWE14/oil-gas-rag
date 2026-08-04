package com.wong.collector.domain.paper.model;

import java.util.List;

/**
 * 用于持久化的快照对象。
 */
public record PaperSnapshot(
        PaperId id,
        Title title,
        Authors authors,
        DOI doi,
        YearPublished yearPublished,
        PaperSource source,
        String abstractContent,
        String downloadUrl,
        Integer citationCount,
        DownloadInfo downloadInfo,
        TransformInfo transformInfo,
        RelevanceInfo relevanceInfo,
        List<DocumentChunk> chunks,
        List<MaterialExtraction> materials,
        PaperState state) {
}
