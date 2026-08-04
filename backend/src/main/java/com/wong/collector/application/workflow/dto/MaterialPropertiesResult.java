package com.wong.collector.application.workflow.dto;

import lombok.Data;

import java.util.List;

@Data
public class MaterialPropertiesResult {
    private String materialName;
    private List<PropertyInfo> properties;

    @Data
    public static class PropertyInfo {
        private String category;
        private String description;
        private List<Integer> chunkReferences;
    }
}
