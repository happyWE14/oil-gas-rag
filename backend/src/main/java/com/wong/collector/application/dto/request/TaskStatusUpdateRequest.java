package com.wong.collector.application.dto.request;

import jakarta.validation.constraints.NotNull;

import lombok.Data;

/**
 * 任务状态更新请求。
 */
@Data
public class TaskStatusUpdateRequest {

    @NotNull
    private String status;

    private String result;

    private String errorMessage;
}
