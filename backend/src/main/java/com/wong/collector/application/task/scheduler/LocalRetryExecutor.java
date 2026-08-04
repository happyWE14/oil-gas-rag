package com.wong.collector.application.task.scheduler;

import com.wong.collector.domain.task.model.TaskId;

public interface LocalRetryExecutor {
    void executeRetry(TaskId taskId);
    void executeLeaseCheck(TaskId taskId, long startedAtEpochMilli);
}
