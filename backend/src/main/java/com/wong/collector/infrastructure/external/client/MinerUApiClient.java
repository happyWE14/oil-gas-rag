package com.wong.collector.infrastructure.external.client;

import com.dtflys.forest.annotation.BaseRequest;
import com.dtflys.forest.annotation.GetRequest;
import com.dtflys.forest.annotation.Header;
import com.dtflys.forest.annotation.JSONBody;
import com.dtflys.forest.annotation.PostRequest;
import com.dtflys.forest.annotation.Var;
import com.wong.collector.infrastructure.external.mineru.dto.MinerUSubmitRequest;
import com.wong.collector.infrastructure.external.mineru.dto.MinerUSubmitResponse;
import com.wong.collector.infrastructure.external.mineru.dto.MinerUTaskStatusResponse;

@BaseRequest(baseURL = "${MINERU_API_BASE_URL}")
public interface MinerUApiClient {

    @PostRequest("/extract/task")
    MinerUSubmitResponse submitTask(@Header("Authorization") String authorization,
                                    @JSONBody MinerUSubmitRequest request);

    @GetRequest("/extract/task/{taskId}")
    MinerUTaskStatusResponse getTaskStatus(@Header("Authorization") String authorization,
                                           @Var("taskId") String taskId);
}
