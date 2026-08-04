package com.wong.collector.application.dto.request;

import java.util.List;

import jakarta.validation.constraints.Min;
import lombok.Data;

/**
 * 创建搜索配置请求。
 */
@Data
public class CreateSearchConfigRequest {

    /**
     * 支持多个provider；为空时默认使用单个provider字段或CORE。
     */
    private List<String> providers;

    private String provider;

    private String query;

    @Min(1)
    private int pageSize = 20;

    private String searchFields;

    private String sortOrder;

    private String filters;

    private String cronExpression;

    @Min(1)
    private Integer maxEmptyPages;

    /**
     * 是否根据关键词簇自动生成查询（使用配置模板）。
     */
    private Boolean autoGenerate = false;

    /**
     * 自动生成时的最大查询条数（为空使用配置默认值）。
     */
    private Integer maxQueries;
}
