package com.wong.collector.domain.search.model;

import java.time.LocalDateTime;

import com.wong.collector.domain.common.aggregate.AggregateRoot;
import com.wong.collector.domain.common.exception.DomainException;

import lombok.Getter;

/**
 * 搜索配置聚合，负责描述一次论文搜索任务的策略。
 */
@Getter
public class SearchConfig extends AggregateRoot<SearchConfigId> {

    private SearchProvider provider;
    private String query;
    private int currentPage;
    private int pageSize;
    private Integer totalPage;
    private Integer totalCount;
    private String searchFields;
    private String sortOrder;
    private String filters;
    private SearchStatus status;
    private LocalDateTime lastRunTime;
    private LocalDateTime nextRunTime;
    private String cronExpression;
    private int maxEmptyPages;
    private int emptyPageCount;
    private String lastFailureReason;
    // 查询快照与模板信息，兼容 JSON/文本存储
    private String querySnapshot;
    private String keywordsSnapshot;
    private String templateName;
    private String batchId;

    private SearchConfig() {
    }

    public static SearchConfig create(SearchProvider provider, String query, int pageSize,
                                      String searchFields, String sortOrder, String filters,
                                      String cronExpression, int maxEmptyPages) {
        if (provider == null) {
            throw new DomainException("搜索平台不能为空");
        }
        if (query == null || query.isBlank()) {
            throw new DomainException("搜索关键词不能为空");
        }
        if (pageSize <= 0) {
            throw new DomainException("分页大小必须大于0");
        }
        SearchConfig config = new SearchConfig();
        config.provider = provider;
        config.query = query;
        config.pageSize = pageSize;
        config.currentPage = 1;
        config.searchFields = searchFields;
        config.sortOrder = sortOrder;
        config.filters = filters;
        config.status = SearchStatus.ACTIVE;
        config.cronExpression = cronExpression;
        config.maxEmptyPages = maxEmptyPages <= 0 ? 1 : maxEmptyPages;
        config.emptyPageCount = 0;
        return config;
    }

    public static SearchConfig restore(SearchConfigId id,
                                       SearchProvider provider,
                                       String query,
                                       int currentPage,
                                       int pageSize,
                                       Integer totalPage,
                                       Integer totalCount,
                                       String searchFields,
                                      String sortOrder,
                                      String filters,
                                       SearchStatus status,
                                       LocalDateTime lastRunTime,
                                       LocalDateTime nextRunTime,
                                       String cronExpression,
                                       Integer maxEmptyPages,
                                       Integer emptyPageCount,
                                       String lastFailureReason,
                                       String querySnapshot,
                                       String keywordsSnapshot,
                                       String templateName,
                                       String batchId) {
        SearchConfig config = new SearchConfig();
        config.setId(id);
        config.provider = provider;
        config.query = query;
        config.currentPage = currentPage;
        config.pageSize = pageSize;
        config.totalPage = totalPage;
        config.totalCount = totalCount;
        config.searchFields = searchFields;
        config.sortOrder = sortOrder;
        config.filters = filters;
        config.status = status;
        config.lastRunTime = lastRunTime;
        config.nextRunTime = nextRunTime;
        config.cronExpression = cronExpression;
        config.maxEmptyPages = maxEmptyPages == null || maxEmptyPages <= 0 ? 1 : maxEmptyPages;
        config.emptyPageCount = emptyPageCount == null ? 0 : emptyPageCount;
        config.lastFailureReason = lastFailureReason;
        config.querySnapshot = querySnapshot;
        config.keywordsSnapshot = keywordsSnapshot;
        config.templateName = templateName;
        config.batchId = batchId;
        return config;
    }

    public void updateQuery(String newQuery, Integer newPageSize) {
        boolean changed = false;
        if (newQuery != null && !newQuery.isBlank() && !newQuery.equals(this.query)) {
            this.query = newQuery;
            changed = true;
        }
        if (newPageSize != null && newPageSize > 0 && newPageSize != this.pageSize) {
            this.pageSize = newPageSize;
            changed = true;
        }
        if (changed) {
            // 变更查询视为重新开始一次搜索，重置分页、统计和空页计数，避免被旧状态影响
            this.currentPage = 1;
            this.totalCount = null;
            this.totalPage = null;
            this.emptyPageCount = 0;
            this.lastFailureReason = null;
            if (this.status != SearchStatus.PAUSED) {
                this.status = SearchStatus.ACTIVE;
            }
        }
    }

    public void markRunning(LocalDateTime runTime) {
        this.status = SearchStatus.ACTIVE;
        this.lastRunTime = runTime == null ? LocalDateTime.now() : runTime;
    }

    public void markCompleted(int currentPage, Integer totalPage, Integer totalCount) {
        this.status = SearchStatus.COMPLETED;
        this.currentPage = currentPage;
        this.totalPage = totalPage;
        this.totalCount = totalCount;
    }

    public void markFailed(String reason) {
        this.status = SearchStatus.FAILED;
        this.lastFailureReason = reason;
    }

    public void pause() {
        this.status = SearchStatus.PAUSED;
    }

    public void resume() {
        this.status = SearchStatus.ACTIVE;
    }

    public void scheduleNext(LocalDateTime nextRunTime) {
        this.nextRunTime = nextRunTime;
    }

    public void updateCron(String cronExpression) {
        this.cronExpression = cronExpression;
    }

    public void updateOptions(String searchFields, String sortOrder, String filters) {
        if (searchFields != null) {
            this.searchFields = searchFields;
        }
        if (sortOrder != null) {
            this.sortOrder = sortOrder;
        }
        if (filters != null) {
            this.filters = filters;
        }
    }

    public int currentOffset() {
        return (currentPage - 1) * pageSize;
    }

    public void recordBatchImported(int importedCount) {
        this.totalCount = (this.totalCount == null ? 0 : this.totalCount) + Math.max(importedCount, 0);
        this.currentPage = this.currentPage + 1;
        this.lastRunTime = LocalDateTime.now();
        if (importedCount <= 0) {
            this.emptyPageCount++;
        } else {
            this.emptyPageCount = 0;
        }
    }

    public void updateMaxEmptyPages(int maxEmptyPages) {
        if (maxEmptyPages > 0) {
            this.maxEmptyPages = maxEmptyPages;
            if (this.emptyPageCount > maxEmptyPages) {
                this.emptyPageCount = maxEmptyPages;
            }
        }
    }

    public boolean exhaustedByEmptyPages() {
        return maxEmptyPages > 0 && emptyPageCount >= maxEmptyPages;
    }

    public void resetEmptyCounter() {
        this.emptyPageCount = 0;
    }

    public void markCompletedByEmptyResult() {
        this.status = SearchStatus.COMPLETED;
    }

    public void attachSnapshot(String querySnapshot, String keywordsSnapshot,
                               String templateName, String batchId) {
        this.querySnapshot = querySnapshot;
        this.keywordsSnapshot = keywordsSnapshot;
        this.templateName = templateName;
        this.batchId = batchId;
    }
}
