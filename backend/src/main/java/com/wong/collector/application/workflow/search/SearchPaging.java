package com.wong.collector.application.workflow.search;

import com.wong.collector.domain.search.model.SearchConfig;

/**
 * Shared paging calculation for SearchExecutor implementations.
 */
public record SearchPaging(int pageSize, int offset, int currentPage) {

    public static SearchPaging of(SearchConfig config, int maxResults) {
        int pageSize = config == null ? 1 : config.getPageSize();
        pageSize = Math.max(1, pageSize);
        if (maxResults > 0) {
            pageSize = Math.min(pageSize, maxResults);
        }

        int currentPage = config == null ? 1 : config.getCurrentPage();
        currentPage = Math.max(1, currentPage);
        int offset = Math.max(0, (currentPage - 1) * pageSize);
        return new SearchPaging(pageSize, offset, currentPage);
    }
}

