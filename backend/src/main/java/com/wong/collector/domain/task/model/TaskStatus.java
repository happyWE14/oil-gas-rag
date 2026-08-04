package com.wong.collector.domain.task.model;

/**
 * 任务执行状态。
 */
public enum TaskStatus {
    WAITING,
    RUNNING,
    SUCCEED,
    FAILED,
    CANCELED
}
