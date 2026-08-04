package com.wong.collector.application.dto.response;

import java.time.LocalDateTime;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class PaperDTO {
    String paperId;
    String title;
    String authors;
    String state;
    Integer yearPublished;
    String paperSource;
    LocalDateTime createdAt;
}
