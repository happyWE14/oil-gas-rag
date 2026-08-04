package com.wong.collector.application.task.backoff;

import java.time.Duration;

public interface BackoffStrategy {

    Duration compute(BackoffContext context);

    boolean supports(BackoffContext context);
}
