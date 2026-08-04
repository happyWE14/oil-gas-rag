package com.wong.collector.infrastructure.persistence.converter;

import org.springframework.stereotype.Component;

import com.wong.collector.domain.search.model.SearchConfig;
import com.wong.collector.domain.search.model.SearchConfigId;
import com.wong.collector.domain.search.model.SearchProvider;
import com.wong.collector.domain.search.model.SearchStatus;
import com.wong.collector.infrastructure.persistence.po.SearchConfigPO;

/**
 * SearchConfig 与持久化对象互转。
 */
@Component
public class SearchConfigConverter {

    public SearchConfig toAggregate(SearchConfigPO po) {
        if (po == null) {
            return null;
        }
        return SearchConfig.restore(
            SearchConfigId.of(po.getId()),
            SearchProvider.valueOf(po.getPlatform()),
            po.getQuery(),
            po.getCurrentPage() == null ? 1 : po.getCurrentPage(),
            po.getPageSize() == null ? 20 : po.getPageSize(),
            po.getTotalPage(),
            po.getTotalCount(),
            po.getSearchFields(),
            po.getSortOrder(),
            po.getFilters(),
            po.getSearchStatus() == null ? SearchStatus.ACTIVE : SearchStatus.valueOf(po.getSearchStatus()),
            po.getLastRunTime(),
            po.getNextRunTime(),
            po.getCronExpression(),
            po.getMaxEmptyPages(),
            po.getEmptyPageCount(),
            po.getLastFailureReason(),
            po.getQuerySnapshot(),
            po.getKeywordsSnapshot(),
            po.getTemplateName(),
            po.getBatchId()
        );
    }

    public SearchConfigPO toPO(SearchConfig config) {
        SearchConfigPO po = new SearchConfigPO();
        if (config.getId() != null) {
            po.setId(config.getId().getValue());
        }
        po.setPlatform(config.getProvider().name());
        po.setQuery(config.getQuery());
        po.setCurrentPage(config.getCurrentPage());
        po.setPageSize(config.getPageSize());
        po.setTotalPage(config.getTotalPage());
        po.setTotalCount(config.getTotalCount());
        po.setSearchFields(config.getSearchFields());
        po.setSortOrder(config.getSortOrder());
        po.setFilters(config.getFilters());
        po.setSearchStatus(config.getStatus().name());
        po.setLastRunTime(config.getLastRunTime());
        po.setNextRunTime(config.getNextRunTime());
        po.setCronExpression(config.getCronExpression());
        po.setMaxEmptyPages(config.getMaxEmptyPages());
        po.setEmptyPageCount(config.getEmptyPageCount());
        po.setLastFailureReason(config.getLastFailureReason());
        po.setQuerySnapshot(config.getQuerySnapshot());
        po.setKeywordsSnapshot(config.getKeywordsSnapshot());
        po.setTemplateName(config.getTemplateName());
        po.setBatchId(config.getBatchId());
        return po;
    }
}
