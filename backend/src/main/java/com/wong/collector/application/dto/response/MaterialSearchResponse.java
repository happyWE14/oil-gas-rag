package com.wong.collector.application.dto.response;

import lombok.Data;
import java.util.List;
import java.util.Map;

@Data
public class MaterialSearchResponse {
    private String docId;
    private PaperInfo paper;
    private MaterialInfo material;
    private List<MetricInfo> metrics;
    private List<ObservationInfo> observations;
    private List<ChunkInfo> chunks;
    private float[] embeddingVector;

    @Data
    public static class PaperInfo {
        private String paperId;
        private String title;
        private String authors;
        private Integer year;
        private String doi;
        private String abstractContent;
    }

    @Data
    public static class MaterialInfo {
        private String materialKey;
        private String materialName;
        private Float confidence;
    }

    @Data
    public static class MetricInfo {
        private String metricKey;
        private String metricName;
        private String valueType;
        private Double valueNum;
        private Double valueMin;
        private Double valueMax;
        private String valueText;
        private String unit;
        private Map<String, Object> conditions;
        private Float confidence;
    }

    @Data
    public static class ObservationInfo {
        private String type;
        private String content;
        private Float confidence;
    }

    @Data
    public static class ChunkInfo {
        private String chunkId;
        private String content;
        private Integer orderNo;
    }
}
