package com.wong.collector.application.task.backoff;

import java.time.Duration;
import java.util.concurrent.ThreadLocalRandom;

import org.springframework.stereotype.Component;

import com.wong.collector.domain.task.model.FailureReason;

@Component
public class RateLimitBackoffStrategy implements BackoffStrategy {

    private static final Duration DEFAULT_BASE = Duration.ofMinutes(5);
    private static final Duration DEFAULT_MAX = Duration.ofHours(2);
    private static final Duration DEFAULT_JITTER_MAX = Duration.ofSeconds(30);

    @Override
    public boolean supports(BackoffContext context) {
        return context.failureReason() == FailureReason.RATE_LIMITED;
    }

    @Override
    public Duration compute(BackoffContext context) {
        Duration recommended = context.recommendedDelay();
        long baseMs;
        if (recommended != null && recommended.toMillis() > 0) {
            baseMs = recommended.toMillis();
        } else {
            int exp = Math.max(0, context.attemptCount() - 1);
            baseMs = safeMultiply(DEFAULT_BASE.toMillis(), 1L << Math.min(exp, 4));
        }

        long cappedMs = Math.min(baseMs, DEFAULT_MAX.toMillis());
        long jitterMs = ThreadLocalRandom.current().nextLong(0, DEFAULT_JITTER_MAX.toMillis() + 1);

        return Duration.ofMillis(cappedMs + jitterMs);
    }

    private long safeMultiply(long a, long b) {
        if (a <= 0 || b <= 0) {
            return 0;
        }
        if (a > Long.MAX_VALUE / b) {
            return Long.MAX_VALUE;
        }
        return a * b;
    }
}
