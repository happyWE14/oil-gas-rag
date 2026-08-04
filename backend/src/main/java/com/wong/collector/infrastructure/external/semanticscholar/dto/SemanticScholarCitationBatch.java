package com.wong.collector.infrastructure.external.semanticscholar.dto;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import lombok.Data;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class SemanticScholarCitationBatch {

    private Integer offset;

    private Integer next;

    private List<SemanticScholarCitation> data;
}

