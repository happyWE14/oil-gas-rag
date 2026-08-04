package com.wong.collector.application.service;

import java.util.*;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import com.wong.collector.application.assembler.TaskAssembler;
import com.wong.collector.application.dto.request.CreateTaskRequest;
import com.wong.collector.application.dto.request.TaskStatusUpdateRequest;
import com.wong.collector.application.dto.response.TaskDTO;
import com.wong.collector.application.dto.response.TaskRetryResultDTO;
import com.wong.collector.application.dto.response.TaskSummaryDTO;
import com.wong.collector.application.task.TaskOrchestrator;
import com.wong.collector.domain.common.event.EventPublisher;
import com.wong.collector.domain.common.exception.NotFoundException;
import com.wong.collector.domain.task.model.FailureReason;
import com.wong.collector.domain.task.model.TaskExecutionProfile;
import com.wong.collector.domain.task.model.TaskId;
import com.wong.collector.domain.task.model.TaskRecord;
import com.wong.collector.domain.task.model.TaskStatus;
import com.wong.collector.domain.task.model.TaskType;
import com.wong.collector.domain.task.repository.TaskRepository;

import lombok.RequiredArgsConstructor;

/**
 * 任务记录应用服务。
 */
@Service
@RequiredArgsConstructor
public class TaskApplicationService {

    private final TaskRepository repository;
    private final TaskAssembler assembler;
    private final TaskOrchestrator taskOrchestrator;
    private final EventPublisher eventPublisher;
    private final TransactionTemplate transactionTemplate;

    @Transactional(rollbackFor = Exception.class)
    public TaskDTO createTask(CreateTaskRequest request) {
        TaskRecord record = TaskRecord.create(
            TaskType.valueOf(request.getTaskType().toUpperCase(Locale.ROOT)),
            request.getTaskName(),
            request.getDescription(),
            request.getParameters(),
            request.getPaperId(),
            request.getMaxRetries(),
            request.getCreatedBy()
        );
        TaskRecord saved = repository.save(record);
        publishDomainEvents(saved);
        return assembler.toDTO(saved);
    }

    @Transactional(rollbackFor = Exception.class)
    public TaskDTO updateStatus(Long id, TaskStatusUpdateRequest request) {
        TaskRecord record = loadTask(id);
        TaskStatus status = TaskStatus.valueOf(request.getStatus().toUpperCase(Locale.ROOT));
        switch (status) {
            case RUNNING -> record.start();
            case SUCCEED -> record.succeed(request.getResult());
            case FAILED -> record.failPermanent(request.getErrorMessage(), FailureReason.UNKNOWN);
            case CANCELED -> record.cancel(request.getErrorMessage());
            case WAITING -> {
                // 保持等待状态
            }
        }
        TaskRecord saved = repository.save(record);
        publishDomainEvents(saved);
        return assembler.toDTO(saved);
    }

    @Transactional(readOnly = true)
    public TaskDTO getTask(Long id) {
        return assembler.toDTO(loadTask(id));
    }

    @Transactional(readOnly = true)
    public List<TaskDTO> listTasks(String status, String paperId, int limit) {
        TaskStatus parsed = status == null ? null : TaskStatus.valueOf(status.toUpperCase(Locale.ROOT));
        List<TaskRecord> records;
        if (paperId != null && !paperId.isBlank()) {
            records = repository.findRecentByPaperId(paperId, limit);
            if (parsed != null) {
                records = records.stream()
                    .filter(r -> r.getStatus() == parsed)
                    .collect(Collectors.toList());
            }
        } else {
            records = repository.findByStatus(parsed, limit);
        }
        return records.stream().map(assembler::toDTO).collect(Collectors.toList());
    }

    @Transactional(rollbackFor = Exception.class)
    public void deleteTask(Long id) {
        repository.delete(TaskId.of(id));
    }

    @Transactional(readOnly = true)
    public TaskSummaryDTO summary() {
        Map<String, Long> waiting = new HashMap<>();
        Map<String, Long> running = new HashMap<>();
        Map<String, Long> failed = new HashMap<>();
        Map<String, Long> succeed = new HashMap<>();
        Map<String, Long> canceled = new HashMap<>();
        Map<String, Long> threshold = new HashMap<>();
        for (TaskType type : TaskType.values()) {
            waiting.put(type.name(), repository.countByTypeAndStatus(type, TaskStatus.WAITING));
            running.put(type.name(), repository.countByTypeAndStatus(type, TaskStatus.RUNNING));
            failed.put(type.name(), repository.countByTypeAndStatus(type, TaskStatus.FAILED));
            succeed.put(type.name(), repository.countByTypeAndStatus(type, TaskStatus.SUCCEED));
            canceled.put(type.name(), repository.countByTypeAndStatus(type, TaskStatus.CANCELED));
            TaskExecutionProfile profile = type.profile();
            threshold.put(type.name(), profile == null ? 0L : profile.backlogThreshold());
        }
        return TaskSummaryDTO.builder()
            .waiting(waiting)
            .running(running)
            .failed(failed)
            .succeed(succeed)
            .canceled(canceled)
            .backlogThreshold(threshold)
            .build();
    }

    public TaskRetryResultDTO retry(Long id, int extraAttempts) {
        RetrySnapshot snapshot = transactionTemplate.execute(status -> {
            TaskRecord record = loadTask(id);
            TaskStatus previous = record.getStatus();
            // Manual retry extends budget instead of resetting attempt history.
            record.extendBudgetAndResetForManualRetry(extraAttempts);
            TaskRecord saved = repository.save(record);
            publishDomainEvents(saved);
            return new RetrySnapshot(previous, saved);
        });
        if (snapshot == null) {
            throw new IllegalStateException("Retry transaction failed");
        }

        TaskOrchestrator.DispatchOutcome outcome = taskOrchestrator.resumeWithOutcome(snapshot.record().getId());
        TaskRecord latest = repository.findById(snapshot.record().getId()).orElse(snapshot.record());

        return TaskRetryResultDTO.builder()
            .taskId(latest.getId() == null ? null : latest.getId().getValue())
            .previousStatus(snapshot.previous().name())
            .currentStatus(latest.getStatus().name())
            .retryCount(latest.getRetryCount())
            .maxRetries(latest.getMaxRetries())
            .nextRetryAt(latest.getNextRetryAt())
            .dispatchStatus(outcome.status().name())
            .reason(outcome.reason())
            .scheduledAt(outcome.scheduledAt())
            .build();
    }

    @Transactional(rollbackFor = Exception.class)
    public void cancel(Long id, TaskStatusUpdateRequest request) {
        TaskRecord record = loadTask(id);
        record.cancel(request == null ? "MANUAL_CANCEL" : request.getErrorMessage());
        TaskRecord saved = repository.save(record);
        publishDomainEvents(saved);
    }

    private TaskRecord loadTask(Long id) {
        return repository.findById(TaskId.of(id))
            .orElseThrow(() -> new NotFoundException("任务不存在: " + id));
    }

    private void publishDomainEvents(TaskRecord record) {
        record.getDomainEvents().forEach(eventPublisher::publish);
        record.clearDomainEvents();
    }

    private record RetrySnapshot(TaskStatus previous, TaskRecord record) {}
}
