package com.wong.collector.infrastructure.telemetry;

/**
 * 观测事件处理器，按需落库或推送监控。
 */
public interface TelemetryProcessor {

    boolean supports(TelemetryEvent event);

    void process(TelemetryEvent event);
}
