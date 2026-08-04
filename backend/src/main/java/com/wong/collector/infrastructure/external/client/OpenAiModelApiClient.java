package com.wong.collector.infrastructure.external.client;

import com.dtflys.forest.annotation.BaseRequest;
import com.dtflys.forest.annotation.Header;
import com.dtflys.forest.annotation.Headers;
import com.dtflys.forest.annotation.JSONBody;
import com.dtflys.forest.annotation.PostRequest;
import com.dtflys.forest.http.ForestSSE;
import com.wong.collector.infrastructure.external.openai.dto.OpenAiChatRequest;

/**
 * Chat Completions SSE 客户端，使用 Spring 配置的 OpenAI base-url 与 API key。
 */
@BaseRequest(baseURL = "${OPENAI_FOREST_BASE_URL}")
public interface OpenAiModelApiClient {

    @PostRequest(url = "/chat/completions")
    @Headers({
        "Accept:text/event-stream",
        "Content-Type:application/json"
    })
    ForestSSE streamChatCompletions(
        @Header("Authorization") String authorization,
        @JSONBody OpenAiChatRequest request
    );
}
