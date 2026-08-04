package com.wong.collector.infrastructure.external.deepseek.dto;

import java.util.List;

import lombok.Data;

@Data
public class DeepseekChatResponse {

    private List<Choice> choices;

    @Data
    public static class Choice {
        private Message message;
    }

    @Data
    public static class Message {
        private String content;
    }
}
