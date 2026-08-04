package com.wong.collector.infrastructure.external.openai.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 单条对话消息。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OpenAiMessage {

    private String role;

    private String content;

    private String name;
}
