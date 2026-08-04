package com.wong.collector.application.dto.search;

import java.time.Instant;

public record ResearchSession(
    String id,
    String query,
    Instant createdAt
) {
    public ResearchSession(String id, String query) {
        this(id, query, Instant.now());
    }
}
