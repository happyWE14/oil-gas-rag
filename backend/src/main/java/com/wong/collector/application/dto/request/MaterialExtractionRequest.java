package com.wong.collector.application.dto.request;

import java.util.List;

import jakarta.validation.constraints.NotNull;

import lombok.Data;

@Data
public class MaterialExtractionRequest {

    @NotNull
    private List<MaterialDTO> materials;

    @Data
    public static class MaterialDTO {
        private String material;
        private String property;
        private String method;
        private Double confidence;
        /**
         * 材料属性的完整JSON结果，避免提前锁定字段。
         */
        private String detailJson;
    }
}
