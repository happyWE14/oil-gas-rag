package com.wong.collector.domain.task.model;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Objects;

import com.wong.collector.domain.common.aggregate.AggregateRoot;
import com.wong.collector.domain.common.exception.DomainException;
import com.wong.collector.domain.task.event.TaskStatusChangedEvent;

import lombok.Getter;

/**
 * 任务执行记录聚合。
 */
@Getter
public class TaskRecord extends AggregateRoot<TaskId> {

    private String paperId;
    private TaskType taskType;
    private String taskName;
    private TaskStatus status;
    private String description;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private Long durationMs;
    private String errorMessage;
    private TaskErrorCategory errorCategory;
    private FailureReason errorReason;
    private LocalDateTime nextRetryAt;
    private int retryCount;
    private int maxRetries;
    private String parameters;
    private String result;
    private String createdBy;

    private TaskRecord() {
    }

    public static TaskRecord create(TaskType type, String taskName, String description,
                                    String parameters, String paperId, int maxRetries,
                                    String createdBy) {
        Objects.requireNonNull(type, "任务类型不能为空");
        if (taskName == null || taskName.isBlank()) {
            throw new DomainException("任务名称不能为空");
        }
        TaskRecord record = new TaskRecord();
        record.taskType = type;
        record.taskName = taskName;
        record.description = description;
        record.parameters = parameters;
        record.paperId = paperId;
        record.maxRetries = maxRetries <= 0 ? 3 : maxRetries;
        record.createdBy = createdBy == null ? "SYSTEM" : createdBy;
        record.status = TaskStatus.WAITING;
        record.retryCount = 0;
        record.errorCategory = null;
        record.errorReason = null;
        record.nextRetryAt = null;
        return record;
    }

    public static TaskRecord restore(TaskId id, String paperId, TaskType taskType,
                                     String taskName, TaskStatus status, String description,
                                     LocalDateTime startTime, LocalDateTime endTime,
                                     Long durationMs, String errorMessage, TaskErrorCategory errorCategory,
                                     FailureReason errorReason, LocalDateTime nextRetryAt, int retryCount, int maxRetries,
                                     String parameters, String result, String createdBy) {
        TaskRecord record = new TaskRecord();
        record.setId(id);
        record.paperId = paperId;
        record.taskType = taskType;
        record.taskName = taskName;
        record.status = status;
        record.description = description;
        record.startTime = startTime;
        record.endTime = endTime;
        record.durationMs = durationMs;
        record.errorMessage = errorMessage;
        record.errorCategory = errorCategory;
        record.errorReason = errorReason;
        record.nextRetryAt = nextRetryAt;
        record.retryCount = retryCount;
        record.maxRetries = maxRetries;
        record.parameters = parameters;
        record.result = result;
        record.createdBy = createdBy;
        return record;
    }

    public void start() {
        changeStatus(TaskStatus.RUNNING);
        this.startTime = LocalDateTime.now();
        this.nextRetryAt = null;
        this.errorCategory = null;
        this.errorReason = null;
    }

    public void succeed(String result) {
        changeStatus(TaskStatus.SUCCEED);
        this.result = result;
        this.errorMessage = null;
        this.errorCategory = null;
        this.errorReason = null;
        this.nextRetryAt = null;
        finishTask();
    }

    public void failRetryable(String errorMessage, FailureReason reason, LocalDateTime nextRetryAt) {
        changeStatus(TaskStatus.FAILED);
        this.errorMessage = errorMessage;
        this.errorCategory = TaskErrorCategory.TRANSIENT;
        this.errorReason = reason;
        this.nextRetryAt = nextRetryAt;
        this.retryCount++;
        finishTask();
    }

    public void failPermanent(String errorMessage, FailureReason reason) {
        changeStatus(TaskStatus.FAILED);
        this.errorMessage = errorMessage;
        this.errorCategory = TaskErrorCategory.PERMANENT;
        this.errorReason = reason;
        this.nextRetryAt = null;
        this.retryCount++;
        // Ensure canRetry() is false for permanent failures while preserving monotonic retryCount history.
        this.maxRetries = Math.min(this.maxRetries, this.retryCount);
        finishTask();
    }

    public void cancel(String reason) {
        changeStatus(TaskStatus.CANCELED);
        this.errorMessage = reason;
        finishTask();
    }

    private void finishTask() {
        this.endTime = LocalDateTime.now();
        if (startTime != null) {
            this.durationMs = Duration.between(startTime, endTime).toMillis();
        }
    }

    public boolean canRetry() {
        return this.retryCount < this.maxRetries && this.status == TaskStatus.FAILED;
    }

    public void resetForRetry() {
        changeStatus(TaskStatus.WAITING);
        this.errorMessage = null;
        this.errorCategory = null;
        this.errorReason = null;
        this.nextRetryAt = null;
        this.startTime = null;
        this.endTime = null;
        this.durationMs = null;
        this.result = null;
    }

    public void extendBudgetAndResetForManualRetry(int extraAttempts) {
        if (extraAttempts <= 0) {
            extraAttempts = 1;
        }
        // Preserve monotonic retryCount history; manual retry extends the budget instead of resetting attempt history.
        this.maxRetries = Math.max(this.maxRetries, this.retryCount + extraAttempts);
        resetForRetry();
    }

    private void changeStatus(TaskStatus newStatus) {
        TaskStatus previous = this.status;
        if (previous == newStatus) {
            return;
        }
        this.status = newStatus;
        registerEvent(new TaskStatusChangedEvent(this, previous, newStatus));
    }
}
