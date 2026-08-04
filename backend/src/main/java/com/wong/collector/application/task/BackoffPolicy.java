package com.wong.collector.application.task;

import java.time.Duration;

import org.springframework.stereotype.Component;

import com.wong.collector.application.task.backoff.BackoffContext;
import com.wong.collector.application.task.backoff.BackoffStrategyResolver;
import com.wong.collector.domain.task.model.TaskType;

@Component
public class BackoffPolicy {

    private final BackoffStrategyResolver strategyResolver;

    public BackoffPolicy(BackoffStrategyResolver strategyResolver) {
        this.strategyResolver = strategyResolver;
    }

    public Duration compute(TaskType type, int attemptCount, FailureDecision decision) {
        BackoffContext context = new BackoffContext(
            type,
            attemptCount,
            decision.recommendedDelay(),
            decision.reason()
        );
        return strategyResolver.resolve(context);
    }
}
