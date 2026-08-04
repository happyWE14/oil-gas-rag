package com.wong.collector.infrastructure.config.properties;

import com.wong.collector.domain.paper.model.PaperEmbeddingModel;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Embedding 相关配置。
 */
@Data
@Component
@ConfigurationProperties(prefix = "embedding")
public class EmbeddingBusinessProperties {

    private String apiKey = "";
    private String baseUrl = "https://dashscope.aliyuncs.com/compatible-mode";
    private String providerModel = "text-embedding-v4";
    private int dimensions = 2048;
    private PaperEmbeddingModel modelId = PaperEmbeddingModel.TEXT_EMBEDDING_V4;
    private final Chunking chunking = new Chunking();

    public void requireExpectedDimensions(float[] vector) {
        int actual = vector == null ? 0 : vector.length;
        if (actual != dimensions) {
            throw new IllegalStateException(
                "Embedding dimension mismatch: expected " + dimensions + ", got " + actual
            );
        }
    }

    @Data
    public static class Chunking {
        private int chunkSize = 400;
        private int chunkOverlap = 80;
        private int batchSize = 8;
    }
}
