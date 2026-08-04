package com.wong.collector.application.assembler;

import java.util.List;

import org.springframework.stereotype.Component;

import com.wong.collector.application.dto.response.SearchConfigDTO;
import com.wong.collector.domain.search.model.SearchConfig;

/**
 * 搜索配置 DTO 装配器。
 */
@Component
public class SearchConfigAssembler {

    public SearchConfigDTO toDTO(SearchConfig config) {
        if (config == null) {
            return null;
        }
        return SearchConfigDTO.builder()
            .id(config.getId() == null ? null : config.getId().getValue())
            .provider(config.getProvider().name())
            .query(config.getQuery())
            .currentPage(config.getCurrentPage())
            .pageSize(config.getPageSize())
            .totalPage(config.getTotalPage())
            .totalCount(config.getTotalCount())
            .searchFields(config.getSearchFields())
            .sortOrder(config.getSortOrder())
            .filters(config.getFilters())
            .status(config.getStatus().name())
            .lastRunTime(config.getLastRunTime())
            .nextRunTime(config.getNextRunTime())
            .cronExpression(config.getCronExpression())
            .maxEmptyPages(config.getMaxEmptyPages())
            .emptyPageCount(config.getEmptyPageCount())
            .providers(List.of(config.getProvider().name()))
            .lastFailureReason(config.getLastFailureReason())
            .querySnapshot(config.getQuerySnapshot())
            .keywordsSnapshot(config.getKeywordsSnapshot())
            .templateName(config.getTemplateName())
            .batchId(config.getBatchId())
            .build();
    }
}
