package com.wong.collector.application.task;

/**
 * 任务被背压策略拒绝时抛出的异常。
 */
public class TaskRejectedException extends RuntimeException {

    public TaskRejectedException(String message) {
        super(message);
    }
}
