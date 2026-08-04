package com.wong.collector.interfaces.ws.dto;

import java.time.Instant;

import com.wong.collector.domain.paper.model.PaperState;
import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class PaperStateChangePayload {
    String paperId;
    PaperState from;
    PaperState to;
    PaperState current;
    Instant occurredOn;
}
