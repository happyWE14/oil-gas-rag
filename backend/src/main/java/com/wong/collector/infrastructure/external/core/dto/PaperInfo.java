package com.wong.collector.infrastructure.external.core.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.wong.collector.infrastructure.external.core.FlexibleListDeserializer;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
public class PaperInfo {

    private String id;
    private String title;

    @JsonProperty("abstract")
    private String abstractText;

    private List<Author> authors;
    private Integer yearPublished;
    private String publisher;
    private String doi;
    private Integer citationCount;
    private String downloadUrl;

    @JsonDeserialize(using = FlexibleListDeserializer.class)
    private List<String> documentType;

    private List<Identifier> identifiers;
    private List<Journal> journals;
    private LocalDateTime createdDate;
    private LocalDateTime updatedDate;

    @Data
    public static class Author {
        private String name;
    }

    @Data
    public static class Identifier {
        private String identifier;
        private String type;
    }

    @Data
    public static class Journal {
        private String title;
    }
}
