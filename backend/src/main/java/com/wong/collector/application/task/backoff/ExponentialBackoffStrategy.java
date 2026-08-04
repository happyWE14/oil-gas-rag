package com.wong.collector.application.task.backoff;

import java.time.Duration;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;

import org.springframework.stereotype.Component;

import com.wong.collector.domain.task.model.FailureReason;
import com.wong.collector.domain.task.model.TaskType;

@Component
public class ExponentialBackoffStrategy implements BackoffStrategy {

    private static final Duration DEFAULT_BASE = Duration.ofSeconds(2);
    private static final Duration DEFAULT_MAX = Duration.ofMinutes(5);
    private static final Duration DEFAULT_JITTER_MAX = Duration.ofMillis(1000);

    private static final Set<FailureReason> SUPPORTED_REASONS = Set.of(
        FailureReason.SERVER_ERROR,
        FailureReason.NETWORK_ERROR,
        FailureReason.CONCURRENCY_CONFLICT
    );

    @Override
    public boolean supports(BackoffContext context) {
        return context.failureReason() != null && SUPPORTED_REASONS.contains(context.failureReason());
    }

    @Override
    public Duration compute(BackoffContext context) {
        Duration base = baseDelay(context.taskType());
        int exp = Math.max(0, context.attemptCount() - 1);
        long candidateMs = safeMultiply(base.toMillis(), 1L << Math.min(exp, 16));
        long cappedMs = Math.min(candidateMs, DEFAULT_MAX.toMillis());
        long jitterMs = ThreadLocalRandom.current().nextLong(0, DEFAULT_JITTER_MAX.toMillis() + 1);
        long delayMs = cappedMs + jitterMs;

        Duration recommended = context.recommendedDelay();
        if (recommended != null) {
            delayMs = Math.max(delayMs, recommended.toMillis());
        }
        return Duration.ofMillis(delayMs);
    }

    private Duration baseDelay(TaskType type) {
        if (type == null) {
            return DEFAULT_BASE;
        }
        return switch (type) {
            case PAPER_DOWNLOAD -> Duration.ofSeconds(1);
            case PAPER_TRANSFORM -> Duration.ofSeconds(10);
            case PAPER_EMBEDDING, PAPER_PRE_ANALYSIS, MATERIAL_EXTRACTION -> Duration.ofSeconds(5);
            default -> DEFAULT_BASE;
        };
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
