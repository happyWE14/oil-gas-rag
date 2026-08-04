package com.wong.collector.application.dto.request;

import jakarta.validation.constraints.NotBlank;

import lombok.Data;

@Data
public class TransformCompletedRequest {

    @NotBlank
    private String markdownPath;
}
