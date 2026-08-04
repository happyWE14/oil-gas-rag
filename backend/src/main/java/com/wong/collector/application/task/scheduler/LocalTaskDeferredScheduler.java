package com.wong.collector.application.task.scheduler;

import java.time.Duration;
import java.time.Instant;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Lazy;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.stereotype.Component;

import com.wong.collector.domain.task.model.TaskId;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
@ConditionalOnProperty(name = "paper.event.mode", havingValue = "spring", matchIfMissing = true)
public class LocalTaskDeferredScheduler implements TaskDeferredScheduler {

    private static final Duration MIN_LEASE_DELAY = Duration.ofSeconds(1);
    
    private final TaskScheduler taskScheduler;
    private final LocalRetryExecutor localRetryExecutor;
    
    public LocalTaskDeferredScheduler(TaskScheduler taskScheduler,
                                       @Lazy LocalRetryExecutor localRetryExecutor) {
        this.taskScheduler = taskScheduler;
        this.localRetryExecutor = localRetryExecutor;
    }

    @Override
    public void scheduleRetry(TaskId taskId, int attempt, Duration delay) {
        Duration safeDelay = normalizeDelay(delay);
        
        if (safeDelay.isZero()) {
            log.debug("Immediate retry via local executor: taskId={} attempt={}", taskId.getValue(), attempt);
            localRetryExecutor.executeRetry(taskId);
            return;
        }
        
        log.info("Scheduling retry via local scheduler: taskId={} attempt={} delay={}s", 
            taskId.getValue(), attempt, safeDelay.getSeconds());
        Instant runAt = Instant.now().plus(safeDelay);
        taskScheduler.schedule(() -> localRetryExecutor.executeRetry(taskId), runAt);
    }

    @Override
    public void scheduleLeaseCheck(TaskId taskId, long startedAtEpochMilli, Duration delay) {
        Duration safeDelay = normalizeDelay(delay);
        if (safeDelay.compareTo(MIN_LEASE_DELAY) < 0) {
            safeDelay = MIN_LEASE_DELAY;
        }
        
        log.debug("Scheduling lease check via local scheduler: taskId={} delay={}s", 
            taskId.getValue(), safeDelay.getSeconds());
        Instant runAt = Instant.now().plus(safeDelay);
        taskScheduler.schedule(() -> localRetryExecutor.executeLeaseCheck(taskId, startedAtEpochMilli), runAt);
    }
    
    private Duration normalizeDelay(Duration delay) {
        if (delay == null || delay.isNegative()) {
            return Duration.ZERO;
        }
        return delay;
    }
    
}
