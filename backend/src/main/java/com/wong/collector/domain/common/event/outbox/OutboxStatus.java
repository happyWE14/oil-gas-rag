package com.wong.collector.domain.common.event.outbox;

/**
 * 发件箱状态。
 */
public enum OutboxStatus {
    PENDING,
    SENT,
    FAILED
}
