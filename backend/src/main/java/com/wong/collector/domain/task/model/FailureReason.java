package com.wong.collector.domain.task.model;

/**
 * Fine-grained failure classification for error-type-aware retry/backoff strategies.
 * Each reason maps to a {@link TaskErrorCategory} for backward compatibility.
 */
public enum FailureReason {

    // --- Rate limiting (429, quota exceeded) ---
    RATE_LIMITED(TaskErrorCategory.TRANSIENT, true),

    // --- Server errors (5xx, timeout, connection reset) ---
    SERVER_ERROR(TaskErrorCategory.TRANSIENT, true),

    // --- Transient IO (network glitch, DNS failure) ---
    NETWORK_ERROR(TaskErrorCategory.TRANSIENT, true),

    // --- Concurrency conflict (optimistic lock failure) ---
    CONCURRENCY_CONFLICT(TaskErrorCategory.TRANSIENT, true),

    // --- Client errors - permanent ---
    AUTH_FAILURE(TaskErrorCategory.PERMANENT, false),           // 401/403
    NOT_FOUND(TaskErrorCategory.PERMANENT, false),              // 404
    INVALID_REQUEST(TaskErrorCategory.PERMANENT, false),        // 400
    CONFIGURATION_ERROR(TaskErrorCategory.PERMANENT, false),    // Missing API key, etc.

    // --- Unknown fallback ---
    UNKNOWN(TaskErrorCategory.PERMANENT, false);

    private final TaskErrorCategory category;
    private final boolean retryable;

    FailureReason(TaskErrorCategory category, boolean retryable) {
        this.category = category;
        this.retryable = retryable;
    }

    public TaskErrorCategory category() {
        return category;
    }

    public boolean isRetryable() {
        return retryable;
    }
}
