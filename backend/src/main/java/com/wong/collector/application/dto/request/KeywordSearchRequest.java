package com.wong.collector.application.dto.request;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record KeywordSearchRequest(
    @NotBlank(message = "Query cannot be empty")
    @Size(min = 2, max = 200, message = "Query must be between 2 and 200 characters")
    String query,
    @Valid Filters filters,
    @Valid Pagination pagination
) {
    public record Filters(
        String type,
        List<String> paperState,
        Double minConfidence,
        YearRange yearRange
    ) {
        public Filters {
            if (type == null || type.isBlank()) {
                type = "all";
            }
        }
    }

    public record YearRange(Integer min, Integer max) {}

    public record Pagination(
        @Min(1) int page,
        @Min(1) @Max(100) int size
    ) {
        public Pagination {
            if (page < 1) page = 1;
            if (size < 1) size = 20;
            if (size > 100) size = 100;
        }
    }

    public KeywordSearchRequest {
        if (pagination == null) {
            pagination = new Pagination(1, 20);
        }
        if (filters == null) {
            filters = new Filters("all", null, null, null);
        }
    }
}
