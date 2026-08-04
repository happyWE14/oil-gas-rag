package com.wong.collector.infrastructure.external.mineru.dto;

import lombok.Data;

@Data
public class MinerUTaskStatusResponse {
    private int code;
    private String msg;
    private DataBody data;

    @Data
    public static class DataBody {
        private String state;
        private String fullZipUrl;
        private ExtractProgress extractProgress;
    }

    @Data
    public static class ExtractProgress {
        private Integer totalPages;
        private Integer extractedPages;
    }
}
