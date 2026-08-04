package com.wong.collector.infrastructure.config;

import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

@Configuration
public class DeepSeekModelConfig {

    @Value("${spring.ai.openai.api-key:}")
    private String apiKey;

    @Value("${spring.ai.openai.base-url:https://api.deepseek.com}")
    private String baseUrl;

    /**
     * 材料提取专用 - deepseek-reasoner (R1)
     */
    @Primary
    @Bean(name = "materialExtractionModel")
    public ChatModel materialExtractionModel() {
        OpenAiApi openAiApi = OpenAiApi.builder()
                .baseUrl(baseUrl.trim())
                .apiKey(apiKey)
                .build();

        OpenAiChatOptions options = OpenAiChatOptions.builder()
                .model("deepseek-reasoner")
                .temperature(0.7)
                // deepseek-reasoner 使用 maxCompletionTokens 而非 maxTokens
                .maxCompletionTokens(100000)
                .build();

        // 新版本使用 Builder 模式
        return OpenAiChatModel.builder()
                .openAiApi(openAiApi)
                .defaultOptions(options)
                .build();
    }

    /**
     * 预处理分析专用 - deepseek-chat (V3)
     */
    @Bean(name = "preAnalysisModel")
    public ChatModel preAnalysisModel() {
        OpenAiApi openAiApi = OpenAiApi.builder()
                .baseUrl(baseUrl.trim())
                .apiKey(apiKey)
                .build();

        OpenAiChatOptions options = OpenAiChatOptions.builder()
                .model("deepseek-chat")
                .temperature(0.7)
                .maxCompletionTokens(10000)
                .build();

        return OpenAiChatModel.builder()
                .openAiApi(openAiApi)
                .defaultOptions(options)
                .build();
    }
}
