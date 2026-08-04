package com.wong.collector.domain.task.repository;

import java.time.Duration;
import java.util.List;
import java.util.Optional;

import com.wong.collector.domain.task.model.TaskId;
import com.wong.collector.domain.task.model.TaskRecord;
import com.wong.collector.domain.task.model.TaskStatus;
import com.wong.collector.domain.task.model.TaskType;

/**
 * 任务记录仓储接口。
 */
public interface TaskRepository {

    Optional<TaskRecord> findById(TaskId id);

    TaskRecord save(TaskRecord record);

    /**
     * Attempt to start a WAITING task atomically.
     * <p>
     * This is used to avoid duplicate execution when multiple dispatchers/resumers race.
     *
     * @return true if the task was started successfully; false if it was already started by another worker
     */
    boolean tryStart(TaskRecord record);

    void delete(TaskId id);

    List<TaskRecord> findByStatus(TaskStatus status, int limit);

    List<TaskRecord> findRecent(int limit);

    List<TaskRecord> findRecoverable(int limit);

    List<TaskRecord> findStaleRunning(TaskType type, Duration timeout, int limit);

    long countByTypeAndStatus(TaskType type, TaskStatus status);

    List<TaskRecord> findRecentByPaperId(String paperId, int limit);
}
