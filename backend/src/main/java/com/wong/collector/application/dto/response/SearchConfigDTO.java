package com.wong.collector.application.dto.response;

import java.time.LocalDateTime;
import java.util.List;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class SearchConfigDTO {
    Long id;
    String provider;
    String query;
    int currentPage;
    int pageSize;
    Integer totalPage;
    Integer totalCount;
    String searchFields;
    String sortOrder;
    String filters;
    String status;
    LocalDateTime lastRunTime;
    LocalDateTime nextRunTime;
    String cronExpression;
    int maxEmptyPages;
    int emptyPageCount;
    List<String> providers;
    String lastFailureReason;
    String querySnapshot;
    String keywordsSnapshot;
    String templateName;
    String batchId;
}
