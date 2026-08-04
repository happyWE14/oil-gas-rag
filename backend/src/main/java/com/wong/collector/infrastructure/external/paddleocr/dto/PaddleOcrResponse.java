package com.wong.collector.infrastructure.external.paddleocr.dto;

import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import lombok.Data;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class PaddleOcrResponse {
    private Integer errorCode;
    private String errorMsg;
    private String logId;
    private Result result;

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Result {
        private DataInfo dataInfo;
        private List<LayoutParsingResult> layoutParsingResults;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class DataInfo {
        private Integer numPages;
        private String type;
        private List<PageInfo> pages;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class PageInfo {
        private Integer width;
        private Integer height;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class LayoutParsingResult {
        private String inputImage;
        private Markdown markdown;
        private Map<String, String> outputImages;
        private PrunedResult prunedResult;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Markdown {
        private Map<String, String> images;
        private String text;
        private Boolean isStart;
        private Boolean isEnd;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class PrunedResult {
        @JsonProperty("layout_det_res")
        private LayoutDetRes layoutDetRes;
        @JsonProperty("model_settings")
        private ModelSettings modelSettings;
        @JsonProperty("parsing_res_list")
        private List<ParsingRes> parsingResList;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class LayoutDetRes {
        private List<Box> boxes;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Box {
        @JsonProperty("cls_id")
        private Integer clsId;
        private List<Double> coordinate;
        private String label;
        private Double score;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class ModelSettings {
        @JsonProperty("format_block_content")
        private Boolean formatBlockContent;
        @JsonProperty("use_chart_recognition")
        private Boolean useChartRecognition;
        @JsonProperty("use_doc_preprocessor")
        private Boolean useDocPreprocessor;
        @JsonProperty("use_layout_detection")
        private Boolean useLayoutDetection;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class ParsingRes {
        @JsonProperty("block_bbox")
        private List<Double> blockBbox;
        @JsonProperty("block_content")
        private String blockContent;
        @JsonProperty("block_id")
        private Integer blockId;
        @JsonProperty("block_label")
        private String blockLabel;
        @JsonProperty("block_order")
        private Integer blockOrder;
    }
}
