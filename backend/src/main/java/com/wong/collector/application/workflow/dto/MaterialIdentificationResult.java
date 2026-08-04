package com.wong.collector.application.workflow.dto;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import lombok.Data;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class MaterialIdentificationResult {

    @JsonProperty("schema_version")
    private String schemaVersion;

    @JsonProperty("has_materials")
    @JsonAlias("hasMaterials")
    private boolean hasMaterials;

    private List<MaterialInfo> materials;

    private List<String> warnings;

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class MaterialInfo {

        @JsonProperty("canonical_name")
        @JsonAlias("name")
        private String name;

        @JsonProperty("chemical_formula")
        @JsonAlias("formula")
        private String formula;

        private String abbreviation;
        private String category;
        private String role;

        private List<String> aliases;
        private double confidence;

        @JsonProperty("is_primary")
        @JsonAlias("isPrimary")
        private boolean isPrimary;

        @JsonProperty("chunk_ids")
        @JsonAlias({"chunkReferences", "chunk_references"})
        private List<String> chunkReferences;
    }
}
