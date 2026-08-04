package com.wong.collector.domain.search.repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import com.wong.collector.domain.search.model.SearchConfig;
import com.wong.collector.domain.search.model.SearchConfigId;
import com.wong.collector.domain.search.model.SearchStatus;

/**
 * 搜索配置仓储接口。
 */
public interface SearchConfigRepository {

    Optional<SearchConfig> findById(SearchConfigId id);

    SearchConfig save(SearchConfig config);

    void delete(SearchConfigId id);

    List<SearchConfig> findByStatus(SearchStatus status);

    List<SearchConfig> findAll();

    List<SearchConfig> findDueConfigs(LocalDateTime deadline);
}
