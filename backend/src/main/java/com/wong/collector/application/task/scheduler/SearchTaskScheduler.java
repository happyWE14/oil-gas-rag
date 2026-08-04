package com.wong.collector.application.task.scheduler;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wong.collector.application.task.TaskOrchestrator;
import com.wong.collector.domain.search.model.SearchConfig;
import com.wong.collector.domain.search.repository.SearchConfigRepository;
import com.wong.collector.domain.task.model.TaskType;
import com.wong.collector.infrastructure.config.properties.SearchBusinessProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 自动调度搜索任务。
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "paper.search.auto-schedule-enabled", havingValue = "true")
public class SearchTaskScheduler {

    private final SearchConfigRepository searchConfigRepository;
    private final TaskOrchestrator taskOrchestrator;
    private final SearchBusinessProperties searchProperties;
    private final ObjectMapper objectMapper;

    @Scheduled(fixedDelayString = "${paper.search.scheduler-fixed-delay-ms:60000}")
    public void pollAndSchedule() {
        LocalDateTime now = LocalDateTime.now();
        List<SearchConfig> dueConfigs = searchConfigRepository.findDueConfigs(now);
        if (dueConfigs.isEmpty()) {
            return;
        }
        log.info("found {} search configs due for execution", dueConfigs.size());
        for (SearchConfig config : dueConfigs) {
            try {
                if (scheduleSearchTask(config)) {
                    config.scheduleNext(now.plusMinutes(searchProperties.getSchedulerIntervalMinutes()));
                    searchConfigRepository.save(config);
                }
            } catch (Exception ex) {
                log.warn("schedule search config {} failed: {}", config.getId().getValue(), ex.getMessage());
            }
        }
    }

    private boolean scheduleSearchTask(SearchConfig config) throws JsonProcessingException {
        if (config.getId() == null || config.getId().getValue() == null) {
            log.warn("skip scheduling search task due to missing config id");
            return false;
        }
        Map<String, Object> payload = Map.of("configId", config.getId().getValue());
        boolean accepted = taskOrchestrator.trySchedule(TaskType.PAPER_SEARCH, null, "自动搜索-" + config.getId().getValue(),
            objectMapper.writeValueAsString(payload));
        if (!accepted) {
            log.warn("search task rejected due to backlog, configId={}", config.getId().getValue());
        }
        return accepted;
    }
}
