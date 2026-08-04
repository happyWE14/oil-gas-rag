package com.wong.collector.application.event.handler;

import java.util.Map;

import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wong.collector.application.task.TaskOrchestrator;
import com.wong.collector.domain.paper.event.PaperRelationExpansionRequestedEvent;
import com.wong.collector.domain.task.model.TaskType;
import com.wong.collector.infrastructure.config.properties.SemanticScholarRelationExpansionProperties;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 监听“关系扩展请求”并调度 relation expand 任务。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RelationExpansionTaskHandler {

    private final TaskOrchestrator taskOrchestrator;
    private final SemanticScholarRelationExpansionProperties properties;
    private final ObjectMapper objectMapper;

    @EventListener
    public void handle(PaperRelationExpansionRequestedEvent event) {
        if (!properties.isEnabled()) {
            return;
        }
        if (event == null || event.getPaperId() == null || event.getPaperId().isBlank()) {
            return;
        }
        int limit = Math.max(0, properties.getLimit());
        if (limit <= 0) {
            return;
        }
        int maxDepth = Math.max(1, properties.getMaxDepth());

        String parameters = null;
        try {
            parameters = objectMapper.writeValueAsString(Map.of(
                "depth", 1,
                "maxDepth", maxDepth,
                "limit", limit
            ));
        } catch (Exception ignored) {
            // keep null
        }

        boolean accepted = taskOrchestrator.trySchedule(
            TaskType.PAPER_RELATION_EXPAND,
            event.getPaperId(),
            "Semantic Scholar 引用/参考扩展",
            parameters
        );
        if (!accepted) {
            log.warn("relation expansion task rejected due to backlog, paperId={}", event.getPaperId());
        }
    }
}

