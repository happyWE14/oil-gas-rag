package com.wong.collector.application.dto.request;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import lombok.Data;

/**
 * 创建任务请求。
 */
@Data
public class CreateTaskRequest {

    @NotNull
    private String taskType;

    @NotBlank
    private String taskName;

    private String description;

    private String parameters;

    private String paperId;

    @Min(1)
    private int maxRetries = 3;

    private String createdBy;
}
