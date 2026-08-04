package com.wong.collector.infrastructure.persistence.po;

import lombok.Data;

@Data
public class IndexedDocumentPO {
    private String documentId;
    private String title;
    private String state;
    private String embeddingModel;
    private Long chunkCount;
}
