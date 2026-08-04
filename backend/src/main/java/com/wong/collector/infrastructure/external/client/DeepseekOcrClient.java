package com.wong.collector.infrastructure.external.client;

import com.dtflys.forest.annotation.*;
import com.dtflys.forest.http.ForestResponse;
import com.wong.collector.infrastructure.external.deepseek.dto.DeepseekChatRequest;
import com.wong.collector.infrastructure.external.deepseek.dto.DeepseekChatResponse;

@BaseRequest(baseURL = "${DEEPSEEK_OCR_BASE_URL}")
public interface DeepseekOcrClient {

    @PostRequest(url = "/chat/completions", readTimeout = 120000)
    @LogEnabled(logRequest = false)
    ForestResponse<DeepseekChatResponse> chat(@Header("Authorization") String authorization,
                                              @JSONBody DeepseekChatRequest request);
}
