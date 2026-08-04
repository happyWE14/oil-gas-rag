package com.wong.collector.application.task.processor;

import com.wong.collector.domain.task.model.TaskRecord;
import com.wong.collector.domain.task.model.TaskType;

/**
 * 任务处理器接口，定义每类任务的具体执行逻辑。
 */
public interface TaskProcessor {

    TaskType supports();

    /**
     * 执行任务，返回执行结果描述。
     */
    String process(TaskRecord record) throws Exception;
}
