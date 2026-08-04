package com.wong.collector.domain.task.model;

import java.time.Duration;

/**
 * 任务执行配置。
 */
public record TaskExecutionProfile(String executorBean,
                                   int maxConcurrency,
                                   int backlogThreshold,
                                   Duration staleRunningTimeout) {

    public static TaskExecutionProfile of(String executorBean, int maxConcurrency, int backlogThreshold, Duration staleRunningTimeout) {
        return new TaskExecutionProfile(executorBean, maxConcurrency, backlogThreshold, staleRunningTimeout);
    }

    public static TaskExecutionProfile serial(String executorBean) {
        return serial(executorBean, Duration.ofMinutes(10));
    }

    public static TaskExecutionProfile parallel(String executorBean, int concurrency) {
        return parallel(executorBean, concurrency, Duration.ofMinutes(10));
    }

    public static TaskExecutionProfile serial(String executorBean, Duration staleRunningTimeout) {
        return new TaskExecutionProfile(executorBean, 1, 100, staleRunningTimeout);
    }

    public static TaskExecutionProfile parallel(String executorBean, int concurrency, Duration staleRunningTimeout) {
        return new TaskExecutionProfile(executorBean, concurrency, 1000, staleRunningTimeout);
    }
}
