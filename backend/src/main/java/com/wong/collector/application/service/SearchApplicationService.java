package com.wong.collector.application.service;

import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.wong.collector.application.assembler.SearchConfigAssembler;
import com.wong.collector.application.dto.request.CreateSearchConfigRequest;
import com.wong.collector.application.dto.request.UpdateSearchConfigRequest;
import com.wong.collector.application.dto.response.SearchConfigDTO;
import com.wong.collector.domain.common.exception.NotFoundException;
import com.wong.collector.domain.search.model.SearchConfig;
import com.wong.collector.domain.search.model.SearchConfigId;
import com.wong.collector.domain.search.model.SearchProvider;
import com.wong.collector.domain.search.model.SearchStatus;
import com.wong.collector.domain.search.repository.SearchConfigRepository;
import com.wong.collector.infrastructure.config.properties.SearchBusinessProperties;
import com.wong.collector.application.workflow.search.SearchQueryBuilder;
import com.wong.collector.application.workflow.search.SearchQueryBuilder.SearchQueryPlan;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 搜索配置应用服务。
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class SearchApplicationService {

    private final SearchConfigRepository repository;
    private final SearchConfigAssembler assembler;
    private final SearchBusinessProperties searchProperties;
    private final SearchQueryBuilder searchQueryBuilder;
    private final ObjectMapper objectMapper;

    @Transactional(rollbackFor = Exception.class)
    public SearchConfigDTO createConfig(CreateSearchConfigRequest request) {
        List<SearchConfig> configs = buildConfigs(request);
        if (configs.isEmpty()) {
            throw new IllegalStateException("搜索配置解析失败");
        }
        List<SearchConfig> saved = configs.stream()
            .map(repository::save)
            .toList();
        return assembler.toDTO(saved.get(0));
    }

    @Transactional(rollbackFor = Exception.class)
    public SearchConfigDTO updateConfig(Long id, UpdateSearchConfigRequest request) {
        SearchConfig config = loadConfig(id);
        config.updateQuery(request.getQuery(), request.getPageSize());
        config.updateOptions(request.getSearchFields(), request.getSortOrder(), request.getFilters());
        if (request.getCronExpression() != null) {
            config.updateCron(request.getCronExpression());
        }
        if (request.getNextRunTime() != null) {
            config.scheduleNext(request.getNextRunTime());
        }
        if (request.getMaxEmptyPages() != null) {
            config.updateMaxEmptyPages(request.getMaxEmptyPages());
        }
        if (request.getStatus() != null) {
            applyStatus(config, request.getStatus());
        }
        SearchConfig saved = repository.save(config);
        return assembler.toDTO(saved);
    }

    @Transactional(readOnly = true)
    public List<SearchConfigDTO> listConfigs(String status) {
        SearchStatus searchStatus = status == null ? null : SearchStatus.valueOf(status.toUpperCase(Locale.ROOT));
        List<SearchConfig> configs = searchStatus == null ? repository.findAll() : repository.findByStatus(searchStatus);
        return configs.stream().map(assembler::toDTO).collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public SearchConfigDTO getConfig(Long id) {
        return assembler.toDTO(loadConfig(id));
    }

    @Transactional(readOnly = true)
    public SearchConfigDTO getConfigStats(Long id) {
        return assembler.toDTO(loadConfig(id));
    }

    @Transactional(rollbackFor = Exception.class)
    public void pause(Long id) {
        SearchConfig config = loadConfig(id);
        config.pause();
        repository.save(config);
    }

    @Transactional(rollbackFor = Exception.class)
    public void resume(Long id) {
        SearchConfig config = loadConfig(id);
        config.resume();
        repository.save(config);
    }

    @Transactional(rollbackFor = Exception.class)
    public void deleteConfig(Long id) {
        repository.delete(SearchConfigId.of(id));
    }

    private SearchConfig loadConfig(Long id) {
        return repository.findById(SearchConfigId.of(id))
            .orElseThrow(() -> new NotFoundException("搜索配置不存在: " + id));
    }

    private List<SearchConfig> buildConfigs(CreateSearchConfigRequest request) {
        List<SearchProvider> providers = parseProviders(request.getProviders(), request.getProvider());
        if (providers.isEmpty()) {
            providers = List.of(SearchProvider.CORE);
        }
        if (!Boolean.TRUE.equals(request.getAutoGenerate())) {
            if (request.getQuery() == null || request.getQuery().isBlank()) {
                throw new IllegalArgumentException("手工模式下 query 不能为空");
            }
        }
        int emptyLimit = request.getMaxEmptyPages() == null || request.getMaxEmptyPages() <= 0
            ? searchProperties.getMaxEmptyPages()
            : request.getMaxEmptyPages();
        // 自动生成模式：基于配置的关键词簇构造多条查询
        if (Boolean.TRUE.equals(request.getAutoGenerate())) {
            Integer maxQueries = request.getMaxQueries();
            return providers.stream()
                .flatMap(provider -> searchQueryBuilder.build(provider).stream()
                    .limit(maxQueries == null || maxQueries <= 0 ? Long.MAX_VALUE : maxQueries)
                    .map(plan -> buildConfigFromPlan(provider, plan, request, emptyLimit)))
                .collect(Collectors.toList());
        }
        // 手工模式：沿用单一 query
        return providers.stream()
            .map(provider -> buildConfig(provider, request, emptyLimit))
            .collect(Collectors.toList());
    }

    private SearchConfig buildConfig(SearchProvider provider, CreateSearchConfigRequest request, int emptyLimit) {
        return SearchConfig.create(
            provider,
            request.getQuery(),
            request.getPageSize(),
            request.getSearchFields(),
            request.getSortOrder(),
            request.getFilters(),
            request.getCronExpression(),
            emptyLimit
        );
    }

    private SearchConfig buildConfigFromPlan(SearchProvider provider, SearchQueryPlan plan,
                                             CreateSearchConfigRequest request, int emptyLimit) {
        SearchConfig config = SearchConfig.create(
            provider,
            plan.getQuery(),
            request.getPageSize(),
            request.getSearchFields(),
            request.getSortOrder(),
            request.getFilters(),
            request.getCronExpression(),
            emptyLimit
        );
        config.attachSnapshot(
            serializeSnapshot(plan.getQuerySnapshot()),
            serializeSnapshot(plan.getKeywordsSnapshot()),
            plan.getTemplateName(),
            plan.getBatchId());
        return config;
    }

    private String serializeSnapshot(Object snapshot) {
        if (snapshot == null) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(snapshot);
        } catch (JsonProcessingException e) {
            log.warn("Failed to serialize search snapshot, fallback to null: {}", e.getMessage());
            return null;
        }
    }

    private List<SearchProvider> parseProviders(List<String> providers, String legacyProvider) {
        if ((providers == null || providers.isEmpty()) && legacyProvider != null) {
            providers = List.of(legacyProvider);
        }
        if (providers == null || providers.isEmpty()) {
            return List.of();
        }
        return providers.stream()
            .map(value -> SearchProvider.valueOf(value.toUpperCase(Locale.ROOT)))
            .collect(Collectors.toList());
    }

    private void applyStatus(SearchConfig config, String statusText) {
        SearchStatus status = SearchStatus.valueOf(statusText.toUpperCase(Locale.ROOT));
        switch (status) {
            case ACTIVE -> config.resume();
            case PAUSED -> config.pause();
            case FAILED -> config.markFailed("手动设置为失败");
            case COMPLETED -> config.markCompleted(config.getCurrentPage(), config.getTotalPage(), config.getTotalCount());
        }
    }
}
