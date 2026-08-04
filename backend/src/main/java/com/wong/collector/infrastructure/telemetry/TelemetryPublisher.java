package com.wong.collector.infrastructure.telemetry;

/**
 * 观测事件发布接口。
 */
public interface TelemetryPublisher {

    void publish(TelemetryEvent event);
}
