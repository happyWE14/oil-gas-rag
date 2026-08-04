package com.wong.collector.application.dto.request;

import java.time.LocalDateTime;

import jakarta.validation.constraints.Min;

import lombok.Data;

/**
 * 更新搜索配置请求。
 */
@Data
public class UpdateSearchConfigRequest {

    private String query;

    @Min(1)
    private Integer pageSize;

    private String searchFields;

    private String sortOrder;

    private String filters;

    private String cronExpression;

    private LocalDateTime nextRunTime;

    private String status;

    @Min(1)
    private Integer maxEmptyPages;
}
