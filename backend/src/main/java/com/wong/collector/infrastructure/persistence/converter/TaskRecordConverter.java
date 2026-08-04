package com.wong.collector.infrastructure.persistence.converter;

import org.springframework.stereotype.Component;

import com.wong.collector.domain.task.model.FailureReason;
import com.wong.collector.domain.task.model.TaskId;
import com.wong.collector.domain.task.model.TaskErrorCategory;
import com.wong.collector.domain.task.model.TaskRecord;
import com.wong.collector.domain.task.model.TaskStatus;
import com.wong.collector.domain.task.model.TaskType;
import com.wong.collector.infrastructure.persistence.po.TaskRecordPO;

import lombok.extern.slf4j.Slf4j;

/**
 * TaskRecord 与持久化对象转换。
 */
@Slf4j
@Component
public class TaskRecordConverter {

    public TaskRecord toAggregate(TaskRecordPO po) {
        if (po == null) {
            return null;
        }
        return TaskRecord.restore(
            TaskId.of(po.getId()),
            po.getPaperId(),
            TaskType.valueOf(po.getTaskType()),
            po.getTaskName(),
            TaskStatus.valueOf(po.getStatus()),
            po.getDescription(),
            po.getStartTime(),
            po.getEndTime(),
            po.getDurationMs(),
            po.getErrorMessage(),
            parseCategory(po.getErrorCategory()),
            parseReason(po.getErrorReason()),
            po.getNextRetryAt(),
            po.getRetryCount() == null ? 0 : po.getRetryCount(),
            po.getMaxRetries() == null ? 3 : po.getMaxRetries(),
            po.getParameters(),
            po.getResult(),
            po.getCreatedBy()
        );
    }

    public TaskRecordPO toPO(TaskRecord record) {
        TaskRecordPO po = new TaskRecordPO();
        if (record.getId() != null) {
            po.setId(record.getId().getValue());
        }
        po.setPaperId(record.getPaperId());
        po.setTaskType(record.getTaskType().name());
        po.setTaskName(record.getTaskName());
        po.setStatus(record.getStatus().name());
        po.setDescription(record.getDescription());
        po.setStartTime(record.getStartTime());
        po.setEndTime(record.getEndTime());
        po.setDurationMs(record.getDurationMs());
        po.setErrorMessage(record.getErrorMessage());
        po.setErrorCategory(record.getErrorCategory() == null ? null : record.getErrorCategory().name());
        po.setErrorReason(record.getErrorReason() == null ? null : record.getErrorReason().name());
        po.setRetryCount(record.getRetryCount());
        po.setMaxRetries(record.getMaxRetries());
        po.setNextRetryAt(record.getNextRetryAt());
        po.setParameters(record.getParameters());
        po.setResult(record.getResult());
        po.setCreatedBy(record.getCreatedBy());
        return po;
    }

    private TaskErrorCategory parseCategory(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return TaskErrorCategory.valueOf(value);
        } catch (IllegalArgumentException e) {
            log.warn("Unknown TaskErrorCategory in DB: '{}', returning null", value);
            return null;
        }
    }

    private FailureReason parseReason(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return FailureReason.valueOf(value);
        } catch (IllegalArgumentException e) {
            log.warn("Unknown FailureReason in DB: '{}', returning null", value);
            return null;
        }
    }
}
