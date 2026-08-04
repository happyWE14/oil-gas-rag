package com.wong.collector.application.workflow.transform;

import org.springframework.stereotype.Component;

import com.wong.collector.infrastructure.config.properties.MinerUApiProperties;
import com.wong.collector.infrastructure.external.client.MinerUApiClient;
import com.wong.collector.infrastructure.external.mineru.dto.MinerUSubmitRequest;
import com.wong.collector.infrastructure.external.mineru.dto.MinerUSubmitResponse;
import com.wong.collector.infrastructure.external.mineru.dto.MinerUTaskStatusResponse;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
@RequiredArgsConstructor
public class MinerUClient {

    private final MinerUApiClient minerUApiClient;
    private final MinerUApiProperties properties;

    public String submitTask(String downloadUrl, String dataId) {
        MinerUSubmitRequest request = MinerUSubmitRequest.builder()
            .url(downloadUrl)
            .dataId(dataId)
            .isOcr(true)
            .enableFormula(true)
            .enableTable(true)
            .modelVersion("pipeline")
            .build();
        MinerUSubmitResponse response = minerUApiClient.submitTask(auth(), request);
        if (response == null || response.getData() == null) {
            throw new IllegalStateException("MinerU 返回空响应");
        }
        if (response.getCode() != 0) {
            throw new IllegalStateException("提交任务失败:" + response.getMsg());
        }
        return response.getData().getTaskId();
    }

    public MinerUTaskStatusResponse.DataBody pollResult(String taskId, int maxAttempts, long intervalMs) {
        for (int i = 0; i < maxAttempts; i++) {
            MinerUTaskStatusResponse status = minerUApiClient.getTaskStatus(auth(), taskId);
            if (status != null && status.getData() != null) {
                String state = status.getData().getState();
                if ("done".equalsIgnoreCase(state) || "failed".equalsIgnoreCase(state)) {
                    return status.getData();
                }
            }
            try {
                Thread.sleep(intervalMs);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
        throw new IllegalStateException("MinerU 任务超时: " + taskId);
    }

    private String auth() {
        return "Bearer " + properties.getApiKey();
    }
}
