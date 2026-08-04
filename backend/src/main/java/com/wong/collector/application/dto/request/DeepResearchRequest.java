package com.wong.collector.application.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record DeepResearchRequest(
    @NotBlank(message = "Query cannot be empty")
    @Size(min = 2, max = 500, message = "Query must be between 2 and 500 characters")
    String query
) {}
