package com.wong.collector.domain.task.event;

import com.wong.collector.domain.common.event.DomainEvent;
import com.wong.collector.domain.task.model.TaskId;
import com.wong.collector.domain.task.model.TaskRecord;
import com.wong.collector.domain.task.model.TaskStatus;
import com.wong.collector.domain.task.model.TaskType;
import lombok.Getter;

/**
 * 任务状态变化事件，用于通知实时通道更新。
 */
@Getter
public class TaskStatusChangedEvent extends DomainEvent {

    private final TaskId taskId;
    private final TaskType taskType;
    private final TaskStatus from;
    private final TaskStatus to;
    private final String taskName;
    private final String paperId;

    public TaskStatusChangedEvent(TaskRecord record, TaskStatus from, TaskStatus to) {
        this.taskId = record.getId();
        this.taskType = record.getTaskType();
        this.from = from;
        this.to = to;
        this.taskName = record.getTaskName();
        this.paperId = record.getPaperId();
    }

    @Override
    public String getAggregateId() {
        return taskId == null ? null : String.valueOf(taskId.getValue());
    }

    @Override
    public String eventName() {
        return "task.status.changed";
    }
}
