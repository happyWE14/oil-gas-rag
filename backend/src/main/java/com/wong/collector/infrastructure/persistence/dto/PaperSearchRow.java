package com.wong.collector.infrastructure.persistence.dto;

import lombok.Data;

@Data
public class PaperSearchRow {
    private String paperId;
    private String title;
    private String authors;
    private Integer yearPublished;
    private String paperSource;
    private String status;
    private Double score;
}
