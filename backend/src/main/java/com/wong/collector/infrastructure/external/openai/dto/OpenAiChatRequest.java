package com.wong.collector.infrastructure.external.openai.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

/**
 * OpenAI/DeepSeek Chat Completions 请求体。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class OpenAiChatRequest {

    private List<OpenAiMessage> messages;

    private String model;

    @JsonProperty("frequency_penalty")
    private Double frequencyPenalty;

    @JsonProperty("max_tokens")
    private Integer maxTokens;

    @JsonProperty("presence_penalty")
    private Double presencePenalty;

    @JsonProperty("response_format")
    private ResponseFormat responseFormat;

    private Object stop;

    /**
     * SSE 默认开启。
     */
    @Builder.Default
    private Boolean stream = true;

    @JsonProperty("stream_options")
    private StreamOptions streamOptions;

    private Double temperature;

    @JsonProperty("top_p")
    private Double topP;

    private List<Map<String, Object>> tools;

    @JsonProperty("tool_choice")
    private Object toolChoice;

    private Boolean logprobs;

    @JsonProperty("top_logprobs")
    private Integer topLogprobs;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ResponseFormat {
        private String type;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class StreamOptions {

        @JsonProperty("include_usage")
        private Boolean includeUsage;
    }
}
