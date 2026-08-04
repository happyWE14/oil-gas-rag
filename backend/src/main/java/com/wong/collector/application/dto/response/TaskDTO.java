package com.wong.collector.application.dto.response;

import java.time.LocalDateTime;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class TaskDTO {
    Long id;
    String paperId;
    String taskType;
    String taskName;
    String status;
    String description;
    LocalDateTime startTime;
    LocalDateTime endTime;
    Long durationMs;
    String errorMessage;
    String errorCategory;
    String errorReason;
    LocalDateTime nextRetryAt;
    int retryCount;
    int maxRetries;
    String parameters;
    String result;
    String createdBy;
}
