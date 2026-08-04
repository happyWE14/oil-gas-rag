package com.wong.collector.infrastructure.external.semanticscholar.dto;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import lombok.Data;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class SemanticScholarCitation {

    private List<String> contexts;

    private List<String> intents;

    private List<ContextWithIntent> contextsWithIntent;

    private Boolean isInfluential;

    private SemanticScholarPaperInfo citingPaper;

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class ContextWithIntent {
        private String context;
        private List<String> intents;
    }
}

