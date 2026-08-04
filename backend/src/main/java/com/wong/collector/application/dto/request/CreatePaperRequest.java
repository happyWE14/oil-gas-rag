package com.wong.collector.application.dto.request;

import java.util.List;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import lombok.Data;

/**
 * 创建论文的命令。
 */
@Data
public class CreatePaperRequest {

    @NotBlank
    private String title;

    @NotEmpty
    private List<String> authors;

    private String doi;

    @Positive
    private Integer yearPublished;

    @NotBlank
    private String paperSource;

    private String abstractContent;

    @NotBlank
    private String downloadUrl;

    private Integer citationCount;
}
