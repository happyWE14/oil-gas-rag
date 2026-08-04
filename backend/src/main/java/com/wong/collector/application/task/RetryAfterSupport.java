package com.wong.collector.application.task;

import java.time.Duration;

import com.dtflys.forest.http.ForestResponse;

/**
 * Shared Retry-After header parser.
 */
public final class RetryAfterSupport {

    private RetryAfterSupport() {
    }

    public static Duration parse(ForestResponse<?> response) {
        if (response == null) {
            return null;
        }
        return parse(response.getHeaderValue("Retry-After"));
    }

    public static Duration parse(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            long seconds = Long.parseLong(value.trim());
            return seconds <= 0 ? null : Duration.ofSeconds(seconds);
        } catch (Exception ignored) {
            return null;
        }
    }
}

