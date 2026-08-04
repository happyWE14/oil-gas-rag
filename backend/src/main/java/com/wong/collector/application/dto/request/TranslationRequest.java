package com.wong.collector.application.dto.request;

import com.fasterxml.jackson.annotation.JsonCreator;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import javax.validation.constraints.NotNull;
import java.util.Arrays;

@Data
public class TranslationRequest {

    @NotBlank(message = "翻译文本不能为空")
    private String text;

    @NotNull(message = "翻译类型不能为空")
    private TranslationType type;

    private String context;

    public enum TranslationType {
        MATERIAL_NAME,
        PAPER_TITLE,
        EVIDENCE_CHUNK,
        OBSERVATION,
        METRIC_DESCRIPTION;

        // 支持大小写不敏感的反序列化
        @JsonCreator
        public static TranslationType fromString(String value) {
            if (value == null) return null;
            return Arrays.stream(values())
                    .filter(t -> t.name().equalsIgnoreCase(value))
                    .findFirst()
                    .orElseThrow(() -> new IllegalArgumentException("无效的翻译类型: " + value));
        }
    }
}
