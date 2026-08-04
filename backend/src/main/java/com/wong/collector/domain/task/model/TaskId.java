package com.wong.collector.domain.task.model;

import com.wong.collector.domain.common.valueobject.BaseEntityId;

/**
 * 任务ID值对象。
 */
public class TaskId extends BaseEntityId<Long> {

    private TaskId(Long value) {
        super(value);
    }

    public static TaskId of(Long value) {
        return new TaskId(value);
    }
}
