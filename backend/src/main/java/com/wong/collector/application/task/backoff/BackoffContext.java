package com.wong.collector.application.task.backoff;

import java.time.Duration;

import com.wong.collector.domain.task.model.FailureReason;
import com.wong.collector.domain.task.model.TaskType;

public record BackoffContext(
    TaskType taskType,
    int attemptCount,
    Duration recommendedDelay,
    FailureReason failureReason
) {
    public BackoffContext {
        if (attemptCount < 0) {
            throw new IllegalArgumentException("attemptCount must be non-negative");
        }
    }
}
