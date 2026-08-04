package com.wong.collector.application.task.backoff;

import java.time.Duration;

import org.springframework.stereotype.Component;

@Component
public class NoRetryStrategy implements BackoffStrategy {

    @Override
    public boolean supports(BackoffContext context) {
        return context.failureReason() == null || !context.failureReason().isRetryable();
    }

    @Override
    public Duration compute(BackoffContext context) {
        return Duration.ZERO;
    }
}
