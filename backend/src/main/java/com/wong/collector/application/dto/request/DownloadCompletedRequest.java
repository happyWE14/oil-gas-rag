package com.wong.collector.application.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import lombok.Data;

@Data
public class DownloadCompletedRequest {

    @NotBlank
    private String ossUrl;

    @NotNull
    private Long fileSize;
}
