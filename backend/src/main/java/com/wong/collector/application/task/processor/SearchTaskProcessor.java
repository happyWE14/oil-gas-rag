package com.wong.collector.application.task.processor;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wong.collector.application.workflow.search.SearchExecutor;
import com.wong.collector.domain.common.exception.NotFoundException;
import com.wong.collector.domain.search.model.SearchConfig;
import com.wong.collector.domain.search.model.SearchConfigId;
import com.wong.collector.domain.search.model.SearchProvider;
import com.wong.collector.domain.search.repository.SearchConfigRepository;
import com.wong.collector.domain.task.model.TaskRecord;
import com.wong.collector.domain.task.model.TaskType;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Component
public class SearchTaskProcessor implements TaskProcessor {

    private final SearchConfigRepository searchConfigRepository;
    private final Map<SearchProvider, SearchExecutor> executorMap;
    private final ObjectMapper objectMapper;

    public SearchTaskProcessor(SearchConfigRepository searchConfigRepository,
                               List<SearchExecutor> executors,
                               ObjectMapper objectMapper) {
        this.searchConfigRepository = searchConfigRepository;
        this.executorMap = new EnumMap<>(SearchProvider.class);
        executors.forEach(executor -> this.executorMap.put(executor.supports(), executor));
        this.objectMapper = objectMapper;
    }

    @Override
    public TaskType supports() {
        return TaskType.PAPER_SEARCH;
    }

    @Override
    public String process(TaskRecord record) throws Exception {
        Long configId = extractConfigId(record);
        SearchConfig config = searchConfigRepository.findById(SearchConfigId.of(configId))
            .orElseThrow(() -> new NotFoundException("搜索配置不存在:" + configId));
        int imported = executeByProvider(config);
        config.recordBatchImported(imported);
        if (imported == 0 && config.exhaustedByEmptyPages()) {
            config.markCompletedByEmptyResult();
        }
        searchConfigRepository.save(config);
        return "imported=" + imported;
    }

    private int executeByProvider(SearchConfig config) {
        SearchProvider provider = config.getProvider() == null ? SearchProvider.CORE : config.getProvider();
        SearchExecutor executor = executorMap.get(provider);
        if (executor == null) {
            throw new NotFoundException("暂未支持的搜索平台:" + provider);
        }
        return executor.importPapers(config);
    }

    private Long extractConfigId(TaskRecord record) throws Exception {
        if (record.getParameters() == null) {
            throw new IllegalStateException("搜索任务缺少参数");
        }
        Map<?, ?> param = objectMapper.readValue(record.getParameters(), Map.class);
        Object value = param.get("configId");
        if (value == null) {
            throw new IllegalStateException("搜索任务缺少configId");
        }
        return Long.parseLong(value.toString());
    }
}
