package com.wong.collector.application.task;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Semaphore;
import java.util.concurrent.ThreadLocalRandom;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.wong.collector.application.task.processor.TaskProcessor;
import com.wong.collector.application.task.scheduler.LocalRetryExecutor;
import com.wong.collector.application.task.scheduler.TaskDeferredScheduler;
import com.wong.collector.domain.common.event.EventPublisher;
import com.wong.collector.domain.common.exception.NotFoundException;
import com.wong.collector.domain.task.model.FailureReason;
import com.wong.collector.domain.task.model.TaskExecutionProfile;
import com.wong.collector.domain.task.model.TaskId;
import com.wong.collector.domain.task.model.TaskRecord;
import com.wong.collector.domain.task.model.TaskStatus;
import com.wong.collector.domain.task.model.TaskType;
import com.wong.collector.domain.task.repository.TaskRepository;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
public class TaskOrchestrator implements LocalRetryExecutor {

    private final TaskRepository taskRepository;
    private final EventPublisher eventPublisher;
    private final Map<String, ThreadPoolTaskExecutor> executorRegistry;
    private final ThreadPoolTaskExecutor defaultExecutor;
    private final TaskScheduler taskScheduler;
    private final TransactionTemplate transactionTemplate;
    private final Map<TaskType, TaskProcessor> processorMap;
    private final Map<TaskType, Semaphore> concurrencyGates = new ConcurrentHashMap<>();
    private final TaskFailureClassifier failureClassifier;
    private final BackoffPolicy backoffPolicy;
    private final PaperTaskFailureHandler paperTaskFailureHandler;
    private final TaskDeferredScheduler deferredScheduler;

    public TaskOrchestrator(TaskRepository taskRepository,
                            EventPublisher eventPublisher,
                            Map<String, ThreadPoolTaskExecutor> executorRegistry,
                            TaskScheduler taskScheduler,
                            TransactionTemplate transactionTemplate,
                            TaskFailureClassifier failureClassifier,
                            BackoffPolicy backoffPolicy,
                            PaperTaskFailureHandler paperTaskFailureHandler,
                            TaskDeferredScheduler deferredScheduler,
                            List<TaskProcessor> processors) {
        this.taskRepository = taskRepository;
        this.eventPublisher = eventPublisher;
        this.executorRegistry = executorRegistry;
        this.taskScheduler = taskScheduler;
        this.transactionTemplate = transactionTemplate;
        this.failureClassifier = failureClassifier;
        this.backoffPolicy = backoffPolicy;
        this.paperTaskFailureHandler = paperTaskFailureHandler;
        this.deferredScheduler = deferredScheduler;
        this.processorMap = new EnumMap<>(TaskType.class);
        processors.forEach(processor -> this.processorMap.put(processor.supports(), processor));
        this.defaultExecutor = resolveDefaultExecutor();
        initConcurrencyGates();
    }

    public void schedule(TaskType type, String paperId, String description) {
        schedule(type, paperId, description, null);
    }
    public void schedule(TaskType type, String paperId, String description, String parameters) {
        if (!trySchedule(type, paperId, description, parameters)) {
            throw new TaskRejectedException("Task backlog exceeded for type " + type.name());
        }
    }

    public boolean trySchedule(TaskType type, String paperId, String description, String parameters) {
        if (isBacklogExceeded(type)) {
            log.warn("skip scheduling {} due to backlog threshold", type);
            return false;
        }
        return Boolean.TRUE.equals(transactionTemplate.execute(status -> {
            TaskRecord record = TaskRecord.create(type, type.name(), description, parameters, paperId, 3, "SYSTEM");
            try {
                TaskRecord saved = taskRepository.save(record);
                dispatchAfterCommit(saved.getId());
                return true;
            } catch (DataIntegrityViolationException dup) {
                log.info("skip scheduling duplicate task: type={} paperId={}", type, paperId);
                return true;
            }
        }));
    }

    public void resume(TaskId taskId) {
        dispatch(taskId, Duration.ZERO, false);
    }

    public DispatchOutcome resumeFromRetryMessage(TaskId taskId, int messageAttempt) {
        Optional<TaskRecord> optRecord = taskRepository.findById(taskId);
        if (optRecord.isEmpty()) {
            log.warn("retry message for non-existent task: taskId={}", taskId.getValue());
            return DispatchOutcome.noop("TASK_NOT_FOUND", null);
        }
        TaskRecord record = optRecord.get();
        TaskStatus status = record.getStatus();
        if (status == TaskStatus.SUCCEED || status == TaskStatus.CANCELED) {
            log.debug("retry message ignored, task already terminal: taskId={} status={}", taskId.getValue(), status);
            return DispatchOutcome.noop("TERMINAL_STATUS:" + status.name(), null);
        }
        if (status == TaskStatus.FAILED && messageAttempt > 0 && messageAttempt < record.getRetryCount()) {
            log.debug("stale retry message ignored: taskId={} messageAttempt={} currentRetryCount={}", 
                taskId.getValue(), messageAttempt, record.getRetryCount());
            return DispatchOutcome.noop("STALE_RETRY_MESSAGE", record.getNextRetryAt());
        }
        if (status == TaskStatus.FAILED && messageAttempt > record.getRetryCount()) {
            log.warn("future retry message received: taskId={} messageAttempt={} currentRetryCount={}", 
                taskId.getValue(), messageAttempt, record.getRetryCount());
        }
        return submitWithOutcome(taskId, false);
    }

    public DispatchOutcome resumeWithOutcome(TaskId taskId) {
        return submitWithOutcome(taskId, false);
    }

    public void recover(TaskId taskId) {
        dispatch(taskId, Duration.ZERO, true);
    }
    
    @Override
    public void executeRetry(TaskId taskId) {
        resume(taskId);
    }
    
    @Override
    public void executeLeaseCheck(TaskId taskId, long startedAtEpochMilli) {
        Optional<TaskRecord> optRecord = taskRepository.findById(taskId);
        if (optRecord.isEmpty()) {
            log.debug("Task not found for lease check: taskId={}", taskId.getValue());
            return;
        }
        TaskRecord record = optRecord.get();
        if (record.getStatus() != TaskStatus.RUNNING) {
            log.debug("Task no longer running, lease check skipped: taskId={} status={}", 
                taskId.getValue(), record.getStatus());
            return;
        }
        long currentStartedAt = record.getStartTime() != null 
            ? record.getStartTime().atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli() 
            : 0;
        if (currentStartedAt != startedAtEpochMilli) {
            log.debug("Task restarted since lease message sent, skipping: taskId={}", taskId.getValue());
            return;
        }
        TaskExecutionProfile profile = record.getTaskType().profile();
        Duration staleTimeout = profile != null && profile.staleRunningTimeout() != null 
            ? profile.staleRunningTimeout() 
            : Duration.ofMinutes(10);
        long elapsedMs = System.currentTimeMillis() - startedAtEpochMilli;
        long timeoutMs = staleTimeout.toMillis();
        
        if (elapsedMs < timeoutMs) {
            Duration remaining = Duration.ofMillis(timeoutMs - elapsedMs);
            log.debug("Lease check arrived early, rescheduling: taskId={} remaining={}s", 
                taskId.getValue(), remaining.getSeconds());
            deferredScheduler.scheduleLeaseCheck(taskId, startedAtEpochMilli, remaining);
            return;
        }
        
        log.warn("Stale running task detected via lease check: taskId={} elapsed={}ms timeout={}ms", 
            taskId.getValue(), elapsedMs, timeoutMs);
        recover(taskId);
    }

    private boolean isBacklogExceeded(TaskType type) {
        TaskExecutionProfile profile = type.profile();
        if (profile == null || profile.backlogThreshold() <= 0) {
            return false;
        }
        long waiting = taskRepository.countByTypeAndStatus(type, TaskStatus.WAITING);
        return waiting >= profile.backlogThreshold();
    }

    private void dispatchAfterCommit(TaskId taskId) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    try {
                        dispatch(taskId, Duration.ZERO, false);
                    } catch (Exception ex) {
                        log.error("dispatch after commit failed, taskId={}", taskId.getValue(), ex);
                    }
                }
            });
        } else {
            dispatch(taskId, Duration.ZERO, false);
        }
    }

    private void dispatch(TaskId taskId, Duration delay, boolean recoveryMode) {
        if (delay.isZero()) {
            submitWithOutcome(taskId, recoveryMode);
        } else {
            taskScheduler.schedule(() -> submitWithOutcome(taskId, recoveryMode), Instant.now().plus(delay));
        }
    }

    private DispatchOutcome submitWithOutcome(TaskId taskId, boolean recoveryMode) {
        TaskRecord record = taskRepository.findById(taskId)
            .orElseThrow(() -> new NotFoundException("任务不存在:" + taskId.getValue()));
        TaskStatus status = record.getStatus();
        if (status == TaskStatus.SUCCEED || status == TaskStatus.CANCELED) {
            log.info("task {} skipped, status={}", taskId.getValue(), status);
            return DispatchOutcome.noop("TERMINAL_STATUS:" + status.name(), null);
        }
        if (status == TaskStatus.FAILED) {
            if (!record.canRetry()) {
                log.info("task {} skipped, retry limit reached", taskId.getValue());
                return DispatchOutcome.noop("RETRY_LIMIT_REACHED", record.getNextRetryAt());
            }
            if (record.getNextRetryAt() != null) {
                LocalDateTime now = LocalDateTime.now();
                if (record.getNextRetryAt().isAfter(now)) {
                    Duration wait = Duration.between(now, record.getNextRetryAt());
                    log.debug("task {} not due yet, scheduling retry: nextRetryAt={}", taskId.getValue(), record.getNextRetryAt());
                    deferredScheduler.scheduleRetry(taskId, record.getRetryCount(), wait);
                    return DispatchOutcome.deferred("NOT_DUE_NEXT_RETRY_AT", record.getNextRetryAt(), record.getNextRetryAt());
                }
            }
            record.resetForRetry();
            taskRepository.save(record);
            publishDomainEvents(record);
        } else if (status == TaskStatus.RUNNING) {
            if (!recoveryMode) {
                log.info("task {} skipped, status=RUNNING", taskId.getValue());
                return DispatchOutcome.noop("RUNNING", null);
            }
            TaskType taskType = record.getTaskType();
            int attempt = record.getRetryCount() + 1;
            boolean canAutoRetry = attempt <= record.getMaxRetries();
            if (!canAutoRetry) {
                log.info("task {} cannot be recovered, retry limit reached", taskId.getValue());
                record.failPermanent("stale running exceeded retry limit", FailureReason.CONCURRENCY_CONFLICT);
                taskRepository.save(record);
                publishDomainEvents(record);
                paperTaskFailureHandler.onTerminalFailure(record, "stale running exceeded retry limit");
                return DispatchOutcome.noop("RETRY_LIMIT_REACHED", null);
            }
            Duration delay = backoffPolicy.compute(taskType, attempt, FailureDecision.serverError(null));
            LocalDateTime nextRetryAt = delay == null ? LocalDateTime.now() : LocalDateTime.now().plus(delay);
            record.failRetryable("recovered from stale running", FailureReason.CONCURRENCY_CONFLICT, nextRetryAt);
            taskRepository.save(record);
            publishDomainEvents(record);
            deferredScheduler.scheduleRetry(taskId, attempt, delay == null ? Duration.ZERO : delay);
            return DispatchOutcome.deferred("RECOVERED_STALE_RUNNING", nextRetryAt, nextRetryAt);
        }

        TaskProcessor processor = processorMap.get(record.getTaskType());
        if (processor == null) {
            log.error("No processor for task type {}", record.getTaskType());
            return DispatchOutcome.blocked("NO_PROCESSOR:" + record.getTaskType().name(), null);
        }
        ThreadPoolTaskExecutor executor = resolveExecutor(record.getTaskType());
        Semaphore gate = concurrencyGates.get(record.getTaskType());
        if (gate != null && !gate.tryAcquire()) {
            log.debug("task {} waiting for available slot, scheduling retry", taskId.getValue());
            int jitterSeconds = ThreadLocalRandom.current().nextInt(3, 9);
            Duration jitteredDelay = Duration.ofSeconds(jitterSeconds);
            LocalDateTime scheduledAt = LocalDateTime.now().plusSeconds(jitterSeconds);
            deferredScheduler.scheduleRetry(taskId, record.getRetryCount(), jitteredDelay);
            return DispatchOutcome.deferred("CONCURRENCY_LIMIT", scheduledAt, scheduledAt);
        }
        executor.execute(() -> {
            try {
                runTask(taskId, processor);
            } finally {
                if (gate != null) {
                    gate.release();
                }
            }
        });
        return DispatchOutcome.queued(LocalDateTime.now());
    }

    private void runTask(TaskId taskId, TaskProcessor processor) {
        TaskRecord record = taskRepository.findById(taskId)
            .orElseThrow(() -> new NotFoundException("任务不存在:" + taskId.getValue()));
        TaskType taskType = record.getTaskType();
        if (record.getStatus() != TaskStatus.WAITING) {
            log.info("task {} skipped, status={}", taskId.getValue(), record.getStatus());
            return;
        }
        try {
            record.start();
            if (!taskRepository.tryStart(record)) {
                log.info("task {} skipped, already started by another worker", taskId.getValue());
                return;
            }
            publishDomainEvents(record);
            scheduleLeaseCheck(taskId, record);
            String result = processor.process(record);
            record.succeed(result);
            taskRepository.save(record);
            publishDomainEvents(record);
        } catch (Exception ex) {
            FailureDecision decision = failureClassifier.classify(ex);
            String message = ex.getMessage() == null ? ex.getClass().getSimpleName() : ex.getMessage();
            int attempt = record.getRetryCount() + 1;
            boolean canAutoRetry = decision.isRetryable() && attempt <= record.getMaxRetries();
            Duration delay = null;
            LocalDateTime nextRetryAt = null;
            if (decision.isRetryable() && canAutoRetry) {
                delay = backoffPolicy.compute(taskType, attempt, decision);
                nextRetryAt = LocalDateTime.now().plus(delay);
            }
            log.warn("task {} failed: {} (reason={} category={} canAutoRetry={} nextRetryAt={})",
                taskId.getValue(), message, decision.reason(), decision.category(), canAutoRetry, nextRetryAt);
            if (decision.isRetryable()) {
                record.failRetryable(message, decision.reason(), nextRetryAt);
            } else {
                record.failPermanent(message, decision.reason());
            }
            taskRepository.save(record);
            publishDomainEvents(record);
            if (canAutoRetry) {
                deferredScheduler.scheduleRetry(taskId, attempt, delay == null ? Duration.ZERO : delay);
            } else {
                paperTaskFailureHandler.onTerminalFailure(record, message);
            }
        }
    }

    public enum DispatchStatus {
        QUEUED,
        DEFERRED,
        NOOP,
        BLOCKED
    }

    public record DispatchOutcome(DispatchStatus status, String reason, LocalDateTime nextRetryAt, LocalDateTime scheduledAt) {
        static DispatchOutcome queued(LocalDateTime scheduledAt) {
            return new DispatchOutcome(DispatchStatus.QUEUED, null, null, scheduledAt);
        }

        static DispatchOutcome deferred(String reason, LocalDateTime nextRetryAt, LocalDateTime scheduledAt) {
            return new DispatchOutcome(DispatchStatus.DEFERRED, reason, nextRetryAt, scheduledAt);
        }

        static DispatchOutcome noop(String reason, LocalDateTime nextRetryAt) {
            return new DispatchOutcome(DispatchStatus.NOOP, reason, nextRetryAt, null);
        }

        static DispatchOutcome blocked(String reason, LocalDateTime nextRetryAt) {
            return new DispatchOutcome(DispatchStatus.BLOCKED, reason, nextRetryAt, null);
        }
    }

    private ThreadPoolTaskExecutor resolveExecutor(TaskType type) {
        TaskExecutionProfile profile = type.profile();
        if (profile == null || profile.executorBean() == null) {
            return defaultExecutor;
        }
        return executorRegistry.getOrDefault(profile.executorBean(), defaultExecutor);
    }

    private ThreadPoolTaskExecutor resolveDefaultExecutor() {
        return Optional.ofNullable(executorRegistry.get("taskWorkerExecutor"))
            .orElseGet(() -> executorRegistry.values().stream()
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("No ThreadPoolTaskExecutor configured")));
    }

    private void initConcurrencyGates() {
        for (TaskType value : TaskType.values()) {
            TaskExecutionProfile profile = value.profile();
            if (profile != null && profile.maxConcurrency() > 0) {
                concurrencyGates.put(value, new Semaphore(profile.maxConcurrency()));
            }
        }
    }

    private void scheduleLeaseCheck(TaskId taskId, TaskRecord record) {
        TaskExecutionProfile profile = record.getTaskType().profile();
        Duration leaseTimeout = profile != null && profile.staleRunningTimeout() != null 
            ? profile.staleRunningTimeout() 
            : Duration.ofMinutes(10);
        long startedAtEpochMilli = record.getStartTime() != null 
            ? record.getStartTime().atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli() 
            : System.currentTimeMillis();
        deferredScheduler.scheduleLeaseCheck(taskId, startedAtEpochMilli, leaseTimeout);
    }

    private void publishDomainEvents(TaskRecord record) {
        if (record == null || record.getDomainEvents().isEmpty()) {
            return;
        }
        record.getDomainEvents().forEach(eventPublisher::publish);
        record.clearDomainEvents();
    }
}
