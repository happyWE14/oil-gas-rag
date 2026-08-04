package com.wong.collector.application.task;

import java.time.Duration;

/**
 * Marker exception for transient failures that should be retried automatically.
 */
public class RetryableTaskException extends RuntimeException {
    private final Duration recommendedDelay;

    public RetryableTaskException(String message) {
        this(message, null, null);
    }

    public RetryableTaskException(String message, Throwable cause) {
        this(message, null, cause);
    }

    public RetryableTaskException(String message, Duration recommendedDelay) {
        this(message, recommendedDelay, null);
    }

    public RetryableTaskException(String message, Duration recommendedDelay, Throwable cause) {
        super(message, cause);
        this.recommendedDelay = recommendedDelay;
    }

    public Duration getRecommendedDelay() {
        return recommendedDelay;
    }
}

