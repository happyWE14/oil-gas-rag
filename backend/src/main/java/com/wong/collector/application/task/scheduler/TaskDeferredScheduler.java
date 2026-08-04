package com.wong.collector.application.task.scheduler;

import java.time.Duration;

import com.wong.collector.domain.task.model.TaskId;

public interface TaskDeferredScheduler {
    
    void scheduleRetry(TaskId taskId, int attempt, Duration delay);
    
    void scheduleLeaseCheck(TaskId taskId, long startedAtEpochMilli, Duration delay);
}
