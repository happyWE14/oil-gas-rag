package com.wong.collector.infrastructure.config;

import com.wong.collector.infrastructure.config.properties.EmbeddingBusinessProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.openai.OpenAiEmbeddingModel;
import org.springframework.ai.openai.OpenAiEmbeddingOptions;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.ai.document.MetadataMode;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@RequiredArgsConstructor
public class AliyunEmbeddingConfig {

    private final EmbeddingBusinessProperties properties;

    @Bean(name = "openAiEmbeddingModel")
    public EmbeddingModel openAiEmbeddingModel() {
        // 关键修正1：baseUrl 不要以 /v1 结尾，让 OpenAiApi 自动追加 /v1/embeddings
        // 或者如果用了 /v1 结尾，就必须自定义 embeddingsPath 为 /embeddings
        OpenAiApi openAiApi = OpenAiApi.builder()
                .baseUrl(properties.getBaseUrl())
                .apiKey(properties.getApiKey())
                // .embeddingsPath("/v1/embeddings")  // 这是默认值，可不写
                .build();

        OpenAiEmbeddingOptions options = OpenAiEmbeddingOptions.builder()
                .model(properties.getProviderModel())
                .dimensions(properties.getDimensions())
                .build();

        return new OpenAiEmbeddingModel(openAiApi, MetadataMode.EMBED, options);
    }
}
