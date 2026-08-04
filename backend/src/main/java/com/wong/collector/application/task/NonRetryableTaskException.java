package com.wong.collector.application.task;

/**
 * 标记型异常：用于告诉调度器不要重试。
 */
public class NonRetryableTaskException extends RuntimeException {
    public NonRetryableTaskException(String message) {
        super(message);
    }

    public NonRetryableTaskException(String message, Throwable cause) {
        super(message, cause);
    }
}
