package com.wong.collector.infrastructure.config;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

/**
 * 基于 Spring AI 提供的 ChatModel / EmbeddingModel 进一步声明项目所需的 Bean。
 * 通过这种方式即可无缝对接 spring-ai-alibaba 或其他 Spring AI Provider，避免手写 OpenAI SDK。
 */
@Configuration
public class AiClientConfig {

    @Bean
    public ChatClient.Builder chatClientBuilder(ChatModel chatModel) {
        return ChatClient.builder(chatModel);
    }

    @Bean("documentEmbeddingModel")
    @Primary
    public EmbeddingModel documentEmbeddingModel(@Qualifier("openAiEmbeddingModel") EmbeddingModel embeddingModel) {
        return embeddingModel;
    }

    @Bean("materialEmbeddingModel")
    public EmbeddingModel materialEmbeddingModel(@Qualifier("openAiEmbeddingModel") EmbeddingModel embeddingModel) {
        return embeddingModel;
    }
}
