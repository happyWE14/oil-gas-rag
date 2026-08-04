package com.wong.collector.application.assembler;

import org.springframework.stereotype.Component;

import com.wong.collector.application.dto.response.TaskDTO;
import com.wong.collector.domain.task.model.TaskRecord;

/**
 * 任务 DTO 装配器。
 */
@Component
public class TaskAssembler {

    public TaskDTO toDTO(TaskRecord record) {
        if (record == null) {
            return null;
        }
        return TaskDTO.builder()
            .id(record.getId() == null ? null : record.getId().getValue())
            .paperId(record.getPaperId())
            .taskType(record.getTaskType().name())
            .taskName(record.getTaskName())
            .status(record.getStatus().name())
            .description(record.getDescription())
            .startTime(record.getStartTime())
            .endTime(record.getEndTime())
            .durationMs(record.getDurationMs())
            .errorMessage(record.getErrorMessage())
            .errorCategory(record.getErrorCategory() == null ? null : record.getErrorCategory().name())
            .errorReason(record.getErrorReason() == null ? null : record.getErrorReason().name())
            .nextRetryAt(record.getNextRetryAt())
            .retryCount(record.getRetryCount())
            .maxRetries(record.getMaxRetries())
            .parameters(record.getParameters())
            .result(record.getResult())
            .createdBy(record.getCreatedBy())
            .build();
    }
}
