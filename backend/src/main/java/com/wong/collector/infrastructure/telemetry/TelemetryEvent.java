package com.wong.collector.infrastructure.telemetry;

import com.wong.collector.domain.task.model.TaskType;

import lombok.Getter;
import lombok.experimental.SuperBuilder;

/**
 * 通用观测事件载体。
 */
@Getter
@SuperBuilder
public class TelemetryEvent {

    private final TelemetryEventType type;
    private final String callId;
    private final String taskId;
    private final TaskType taskType;
    private final String paperId;
    private final TelemetryScene scene;
    private final String modelName;
    private final String rawOutput;
    private final boolean success;
    private final Long latencyMs;
    private final String errorMessage;
}
