package com.wong.collector.infrastructure.telemetry;

/**
 * 观测场景，标识本次调用/任务的业务语境。
 */
public enum TelemetryScene {
    RELEVANCE,
    MATERIAL_IDENTIFICATION,
    MATERIAL_PROPERTY_EXTRACTION,
    OTHER
}
