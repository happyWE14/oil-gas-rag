package com.wong.collector.application.task;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.wong.collector.domain.task.model.TaskRecord;
import com.wong.collector.domain.task.model.TaskType;
import com.wong.collector.domain.task.repository.TaskRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Fallback recovery scheduler for due FAILED tasks and stale RUNNING tasks.
 * <p>
 * With MQ-driven retry/lease mechanisms in place, this scheduler serves as a safety net
 * and runs at a lower frequency (default 5 minutes).
 * <p>
 * {@link TaskRepository#findRecoverable(int)} is responsible for honoring next_retry_at gating.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TaskRecoveryScheduler {

    private final TaskRepository taskRepository;
    private final TaskOrchestrator taskOrchestrator;
    private final AtomicBoolean running = new AtomicBoolean(false);

    @Scheduled(fixedDelayString = "${task.recovery.fixed-delay-ms:300000}")
    public void recover() {
        if (!running.compareAndSet(false, true)) {
            return;
        }
        try {
            int recovered = 0;

            List<TaskRecord> due = taskRepository.findRecoverable(200);
            if (!due.isEmpty()) {
                recovered += due.size();
                log.debug("periodic recover due task(s): {}", due.size());
                due.forEach(record -> taskOrchestrator.recover(record.getId()));
            }

            for (TaskType type : TaskType.values()) {
                List<TaskRecord> stale = taskRepository.findStaleRunning(type, type.profile().staleRunningTimeout(), 50);
                if (stale.isEmpty()) {
                    continue;
                }
                recovered += stale.size();
                log.debug("periodic recover stale RUNNING task(s): type={} count={}", type, stale.size());
                stale.forEach(record -> taskOrchestrator.recover(record.getId()));
            }

            if (recovered == 0) {
                return;
            }
        } finally {
            running.set(false);
        }
    }
}
