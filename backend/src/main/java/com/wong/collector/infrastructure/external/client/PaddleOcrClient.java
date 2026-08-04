package com.wong.collector.infrastructure.external.client;

import com.dtflys.forest.annotation.BaseRequest;
import com.dtflys.forest.annotation.Header;
import com.dtflys.forest.annotation.JSONBody;
import com.dtflys.forest.annotation.LogEnabled;
import com.dtflys.forest.annotation.PostRequest;
import com.dtflys.forest.http.ForestResponse;
import com.wong.collector.infrastructure.external.paddleocr.dto.PaddleOcrRequest;
import com.wong.collector.infrastructure.external.paddleocr.dto.PaddleOcrResponse;

@BaseRequest(baseURL = "${PADDLEOCR_API_URL}")
public interface PaddleOcrClient {

    @PostRequest(
        contentType = "application/json",
        readTimeout = 300000,
        connectTimeout = 10000
    )
    @LogEnabled(logRequest = false)
    ForestResponse<PaddleOcrResponse> parse(@Header("Authorization") String authorization, @JSONBody PaddleOcrRequest request);
}
