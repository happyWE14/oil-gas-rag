package com.wong.collector.infrastructure.repository;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import com.wong.collector.domain.task.model.TaskType;
import org.springframework.stereotype.Repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;

import com.wong.collector.domain.task.model.TaskId;
import com.wong.collector.domain.task.model.TaskRecord;
import com.wong.collector.domain.task.model.TaskStatus;
import com.wong.collector.domain.task.repository.TaskRepository;
import com.wong.collector.infrastructure.persistence.converter.TaskRecordConverter;
import com.wong.collector.infrastructure.persistence.mapper.TaskRecordMapper;
import com.wong.collector.infrastructure.persistence.po.TaskRecordPO;

import lombok.RequiredArgsConstructor;

@Repository
@RequiredArgsConstructor
public class TaskRepositoryImpl implements TaskRepository {

    private final TaskRecordMapper mapper;
    private final TaskRecordConverter converter;

    @Override
    public Optional<TaskRecord> findById(TaskId id) {
        if (id == null) {
            return Optional.empty();
        }
        TaskRecordPO po = mapper.selectById(id.getValue());
        return Optional.ofNullable(converter.toAggregate(po));
    }

    @Override
    public TaskRecord save(TaskRecord record) {
        TaskRecordPO po = converter.toPO(record);
        if (po.getId() == null) {
            mapper.insert(po);
        } else {
            mapper.updateById(po);
        }
        return converter.toAggregate(mapper.selectById(po.getId()));
    }

    @Override
    public boolean tryStart(TaskRecord record) {
        if (record == null || record.getId() == null || record.getId().getValue() == null) {
            return false;
        }
        // TaskOrchestrator calls TaskRecord.start() first to register domain events; we persist it atomically here.
        if (record.getStatus() != TaskStatus.RUNNING) {
            return false;
        }
        int updated = mapper.startIfWaiting(
            record.getId().getValue(),
            TaskStatus.WAITING.name(),
            TaskStatus.RUNNING.name(),
            record.getStartTime()
        );
        return updated > 0;
    }

    @Override
    public void delete(TaskId id) {
        if (id != null) {
            mapper.deleteById(id.getValue());
        }
    }

    @Override
    public List<TaskRecord> findByStatus(TaskStatus status, int limit) {
        LambdaQueryWrapper<TaskRecordPO> wrapper = new LambdaQueryWrapper<>();
        if (status != null) {
            wrapper.eq(TaskRecordPO::getStatus, status.name());
        }
        wrapper.orderByDesc(TaskRecordPO::getCreateTime).last("limit " + Math.max(limit, 1));
        return mapper.selectList(wrapper).stream()
            .map(converter::toAggregate)
            .collect(Collectors.toList());
    }

    @Override
    public List<TaskRecord> findRecent(int limit) {
        LambdaQueryWrapper<TaskRecordPO> wrapper = new LambdaQueryWrapper<TaskRecordPO>()
            .orderByDesc(TaskRecordPO::getCreateTime)
            .last("limit " + Math.max(limit, 1));
        return mapper.selectList(wrapper).stream()
            .map(converter::toAggregate)
            .collect(Collectors.toList());
    }

    @Override
    public List<TaskRecord> findRecoverable(int limit) {
        LocalDateTime now = LocalDateTime.now();
        LambdaQueryWrapper<TaskRecordPO> wrapper = new LambdaQueryWrapper<TaskRecordPO>()
            .and(w -> w.eq(TaskRecordPO::getStatus, TaskStatus.WAITING.name())
                .or(w2 -> w2.eq(TaskRecordPO::getStatus, TaskStatus.FAILED.name())
                    .apply("retry_count < max_retries")
                    // Backward compatibility: treat NULL as transient for legacy rows.
                    .and(w21 -> w21.isNull(TaskRecordPO::getErrorCategory)
                        .or(w22 -> w22.eq(TaskRecordPO::getErrorCategory, "TRANSIENT")))
                    // Only pick tasks due for retry (or with no schedule).
                    .and(w23 -> w23.isNull(TaskRecordPO::getNextRetryAt)
                        .or(w24 -> w24.le(TaskRecordPO::getNextRetryAt, now))))
                )
            .last("limit " + Math.max(limit, 1));
        return mapper.selectList(wrapper).stream()
            .map(converter::toAggregate)
            .collect(Collectors.toList());
    }

    @Override
    public List<TaskRecord> findStaleRunning(TaskType type, Duration timeout, int limit) {
        if (type == null) {
            return List.of();
        }
        Duration effective = (timeout == null || timeout.isZero() || timeout.isNegative())
            ? Duration.ofMinutes(10)
            : timeout;
        LocalDateTime threshold = LocalDateTime.now().minus(effective);
        LambdaQueryWrapper<TaskRecordPO> wrapper = new LambdaQueryWrapper<TaskRecordPO>()
            .eq(TaskRecordPO::getStatus, TaskStatus.RUNNING.name())
            .eq(TaskRecordPO::getTaskType, type.name())
            .lt(TaskRecordPO::getUpdateTime, threshold)
            .last("limit " + Math.max(limit, 1));
        return mapper.selectList(wrapper).stream()
            .map(converter::toAggregate)
            .collect(Collectors.toList());
    }

    @Override
    public long countByTypeAndStatus(TaskType type, TaskStatus status) {
        LambdaQueryWrapper<TaskRecordPO> wrapper = new LambdaQueryWrapper<TaskRecordPO>()
            .eq(TaskRecordPO::getTaskType, type.name())
            .eq(TaskRecordPO::getStatus, status.name());
        return mapper.selectCount(wrapper);
    }

    @Override
    public List<TaskRecord> findRecentByPaperId(String paperId, int limit) {
        LambdaQueryWrapper<TaskRecordPO> wrapper = new LambdaQueryWrapper<TaskRecordPO>()
            .eq(TaskRecordPO::getPaperId, paperId)
            .orderByDesc(TaskRecordPO::getUpdateTime)
            .last("limit " + Math.max(limit, 1));
        return mapper.selectList(wrapper).stream()
            .map(converter::toAggregate)
            .collect(Collectors.toList());
    }
}
