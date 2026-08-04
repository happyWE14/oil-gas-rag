package com.wong.collector.domain.task.model;

/**
 * Task failure category for retry decisions and audit.
 */
public enum TaskErrorCategory {
    /**
     * Transient failures that are expected to succeed on retry (timeouts, 429/5xx, upstream unavailability).
     */
    TRANSIENT,
    /**
     * Permanent failures that should not be retried automatically (invalid input, 401/403, 404, misconfiguration).
     */
    PERMANENT
}
