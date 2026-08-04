package com.wong.collector.infrastructure.config.properties;

import java.util.List;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 材料提取业务参数。
 */
@Data
@Component
@ConfigurationProperties(prefix = "material-extraction")
public class MaterialExtractionBusinessProperties {

    private final Identification identification = new Identification();
    private final Chunking chunking = new Chunking();
    private final Prompts prompts = new Prompts();

    @Data
    public static class Identification {
        private double minConfidence = 0.6;
        private int topKChunks = 12;
    }

    @Data
    public static class Chunking {
        private int chunksPerMaterial = 12;
        private int propertyTopKChunks = 12;
    }

    @Data
    public static class Prompts {
        private String materialIdentification = "Identify materials with JSON output";
        private String propertyExtraction = "Extract material properties with JSON output";
    }
}
