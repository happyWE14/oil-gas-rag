package com.wong.collector.application.workflow.semanticscholar;

import org.springframework.stereotype.Component;

import com.wong.collector.infrastructure.config.properties.SemanticScholarApiProperties;
import com.wong.collector.infrastructure.external.client.support.RateLimiter;

/**
 * Shared rate limiter for all Semantic Scholar API calls inside this service.
 */
@Component
public class SemanticScholarRequestGate {

    private final RateLimiter limiter;

    public SemanticScholarRequestGate(SemanticScholarApiProperties properties) {
        this.limiter = new RateLimiter(
            properties.getRateLimit().getIntervalMs(),
            properties.getRateLimit().getMaxConcurrency()
        );
    }

    public void acquire() {
        limiter.acquire();
    }

    public void release() {
        limiter.release();
    }
}

