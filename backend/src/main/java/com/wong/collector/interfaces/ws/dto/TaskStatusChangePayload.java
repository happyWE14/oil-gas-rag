package com.wong.collector.interfaces.ws.dto;

import java.time.Instant;

import com.wong.collector.domain.task.model.TaskStatus;
import com.wong.collector.domain.task.model.TaskType;
import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class TaskStatusChangePayload {
    Long taskId;
    TaskType taskType;
    TaskStatus from;
    TaskStatus to;
    String taskName;
    String paperId;
    Instant occurredOn;
}
