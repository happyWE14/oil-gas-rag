package com.wong.collector.infrastructure.external.openai.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.List;

/**
 * OpenAI/DeepSeek 流式响应单个 chunk 结构，便于在 SSE onMessage 回调中转换。
 */
@Data
public class OpenAiChatChunk {

    private String id;

    private String object;

    private Long created;

    private String model;

    @JsonProperty("system_fingerprint")
    private String systemFingerprint;

    private List<Choice> choices;

    @Data
    public static class Choice {

        private Integer index;

        private Delta delta;

        @JsonProperty("finish_reason")
        private String finishReason;
    }

    @Data
    public static class Delta {

        private String content;

        @JsonProperty("reasoning_content")
        private String reasoningContent;

        private String role;
    }
}
