package com.wong.collector.infrastructure.persistence.dto;

import java.time.LocalDateTime;

import lombok.Data;

/**
 * Persistence projection row for material->paper occurrences.
 */
@Data
public class MaterialPaperRow {

    private String materialKey;
    private String material;
    private String property;
    private String method;
    private Double confidence;
    private String detailJson;

    private String paperId;
    private String title;
    private String authors;
    private Integer yearPublished;
    private String doi;
    private String state;
    private Integer citationCount;
    private LocalDateTime updatedAt;
}

