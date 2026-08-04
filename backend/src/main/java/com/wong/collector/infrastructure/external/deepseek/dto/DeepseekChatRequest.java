package com.wong.collector.infrastructure.external.deepseek.dto;

import java.util.List;

import lombok.Data;

@Data
public class DeepseekChatRequest {
    private List<Message> messages;
    private String model;

    @Data
    public static class Message {
        private String role;
        private List<Content> content;
    }

    @Data
    public static class Content {
        private String type; // "text" or "image_url"
        private String text;
        private ImageUrl image_url;
    }

    @Data
    public static class ImageUrl {
        private String url;
    }
}
