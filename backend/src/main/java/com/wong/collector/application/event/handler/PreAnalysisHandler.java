package com.wong.collector.application.event.handler;

import java.util.Map;
import java.util.HashMap;
import java.util.Optional;

import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wong.collector.application.task.TaskOrchestrator;
import com.wong.collector.domain.paper.event.PaperDiscoveredEvent;
import com.wong.collector.domain.task.model.TaskType;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 监听论文发现事件并触发自动预分析。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PreAnalysisHandler {

    private final TaskOrchestrator taskOrchestrator;
    private final ObjectMapper objectMapper;

    @EventListener
    public void onPaperDiscovered(PaperDiscoveredEvent event) {
        try {
            schedulePreAnalysis(event);
        } catch (Exception ex) {
            // 防止单条数据异常影响其他 discovered 事件
            log.error("schedule pre-analysis failed, paperId={}, err={}", event.getPaperId().getValue(), ex.getMessage(), ex);
        }
    }

    private void schedulePreAnalysis(PaperDiscoveredEvent event) {
        log.info("schedule pre-analysis task for {}", event.getPaperId().getValue());
        String title = Optional.ofNullable(event.getTitle()).filter(t -> !t.isBlank()).orElse("Untitled");
        String abstractContent = Optional.ofNullable(event.getAbstractContent()).orElse("");
        boolean missingAbstract = abstractContent.isBlank();

        boolean accepted = false;
        try {
            Map<String, Object> payload = new HashMap<>();
            payload.put("title", title);
            payload.put("abstract", abstractContent);
            payload.put("missingAbstract", missingAbstract);
            accepted = taskOrchestrator.trySchedule(TaskType.PAPER_PRE_ANALYSIS, event.getPaperId().getValue(),
                "预分析任务", objectMapper.writeValueAsString(payload));
        } catch (Exception e) {
            log.warn("serialize pre-analysis payload failed, fallback to task without payload");
        }
        if (!accepted) {
            accepted = taskOrchestrator.trySchedule(TaskType.PAPER_PRE_ANALYSIS, event.getPaperId().getValue(), "预分析任务", null);
        }
        if (!accepted) {
            log.warn("pre-analysis task rejected due to backlog, paperId={}", event.getPaperId().getValue());
        }
    }
}
