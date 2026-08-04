package com.wong.collector.application.dto.response;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class TranslationResponse {
    private String originalText;
    private String translatedText;
    private String source;      // cache 或 api
    private Long costTime;      // 耗时(ms)
}
