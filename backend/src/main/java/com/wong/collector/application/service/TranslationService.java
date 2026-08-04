package com.wong.collector.application.service;

import com.wong.collector.application.dto.request.TranslationRequest;
import com.wong.collector.application.dto.response.TranslationResponse;

import java.util.List;

public interface TranslationService {

    /**
     * 翻译文本，优先从缓存获取，否则调用 DeepSeek API
     */
    TranslationResponse translate(TranslationRequest request);

    /**
     * 批量翻译
     */
    List<TranslationResponse> translateBatch(List<TranslationRequest> requests);

    /**
     * 清除翻译缓存
     */
    void clearCache(String text, TranslationRequest.TranslationType type);
}
