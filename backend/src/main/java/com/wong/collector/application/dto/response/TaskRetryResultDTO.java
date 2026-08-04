package com.wong.collector.application.dto.response;

import java.time.LocalDateTime;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class TaskRetryResultDTO {
    Long taskId;
    String previousStatus;
    String currentStatus;
    int retryCount;
    int maxRetries;
    LocalDateTime nextRetryAt;
    String dispatchStatus;
    String reason;
    LocalDateTime scheduledAt;
}

