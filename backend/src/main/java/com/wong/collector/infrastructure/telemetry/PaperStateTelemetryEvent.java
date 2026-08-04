package com.wong.collector.infrastructure.telemetry;

import java.time.Instant;

import lombok.Getter;
import lombok.experimental.SuperBuilder;

/**
 * 论文状态流转观测事件，附带必要上下文。
 */
@Getter
@SuperBuilder
public class PaperStateTelemetryEvent extends TelemetryEvent {

    private final String fromState;
    private final String toState;
    private final String operator;
    private final String reason;
    private final Instant occurredAt;
}
