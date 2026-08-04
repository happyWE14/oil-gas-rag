package com.wong.collector.infrastructure.external.semanticscholar.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.List;
import java.util.Map;

/**
 * Semantic Scholar 论文条目。
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class SemanticScholarPaperInfo {

    private String paperId;

    private String title;

    @JsonProperty("abstract")
    private String abstractText;

    private Integer year;

    private Integer citationCount;

    @JsonProperty("isOpenAccess")
    private Boolean openAccess;

    /**
     * Semantic Scholar 详情页 URL。
     */
    private String url;

    /**
     * 外部标识（如 DOI/ArXiv/PubMed 等）。
     */
    private Map<String, Object> externalIds;

    private List<AuthorInfo> authors;

    private OpenAccessPdf openAccessPdf;

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class AuthorInfo {
        private String authorId;
        private String name;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class OpenAccessPdf {
        private String url;
        private String status;
        private String license;
        private String disclaimer;
    }
}
