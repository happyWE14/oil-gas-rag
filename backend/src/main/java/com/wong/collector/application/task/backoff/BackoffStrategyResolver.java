package com.wong.collector.application.task.backoff;

import java.time.Duration;
import java.util.List;

import org.springframework.stereotype.Component;

@Component
public class BackoffStrategyResolver {

    private final List<BackoffStrategy> strategies;
    private final ExponentialBackoffStrategy fallbackStrategy;

    public BackoffStrategyResolver(List<BackoffStrategy> strategies, ExponentialBackoffStrategy fallbackStrategy) {
        this.strategies = strategies;
        this.fallbackStrategy = fallbackStrategy;
    }

    public Duration resolve(BackoffContext context) {
        for (BackoffStrategy strategy : strategies) {
            if (strategy.supports(context)) {
                return strategy.compute(context);
            }
        }
        return fallbackStrategy.compute(context);
    }
}
