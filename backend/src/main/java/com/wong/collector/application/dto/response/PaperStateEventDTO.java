package com.wong.collector.application.dto.response;

import java.time.LocalDateTime;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class PaperStateEventDTO {
    String fromState;
    String toState;
    String operator;
    String reason;
    LocalDateTime occurredAt;
}

