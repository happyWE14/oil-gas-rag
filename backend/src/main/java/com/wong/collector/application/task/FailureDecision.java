package com.wong.collector.application.task;

import java.time.Duration;

import com.wong.collector.domain.task.model.FailureReason;
import com.wong.collector.domain.task.model.TaskErrorCategory;

public record FailureDecision(
    FailureReason reason,
    Duration recommendedDelay
) {

    public TaskErrorCategory category() {
        return reason.category();
    }

    public boolean isRetryable() {
        return reason.isRetryable();
    }

    public static FailureDecision rateLimited(Duration retryAfter) {
        return new FailureDecision(FailureReason.RATE_LIMITED, retryAfter);
    }

    public static FailureDecision serverError(Duration retryAfter) {
        return new FailureDecision(FailureReason.SERVER_ERROR, retryAfter);
    }

    public static FailureDecision networkError(Duration recommendedDelay) {
        return new FailureDecision(FailureReason.NETWORK_ERROR, recommendedDelay);
    }

    public static FailureDecision concurrencyConflict() {
        return new FailureDecision(FailureReason.CONCURRENCY_CONFLICT, Duration.ofMillis(200));
    }

    public static FailureDecision permanent(FailureReason reason) {
        return new FailureDecision(reason, null);
    }

    public static FailureDecision of(FailureReason reason, Duration recommendedDelay) {
        return new FailureDecision(reason, recommendedDelay);
    }
}
