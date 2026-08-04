package com.wong.collector.application.workflow.pipeline;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.springframework.stereotype.Component;

import com.wong.collector.application.task.TaskOrchestrator;
import com.wong.collector.domain.task.model.TaskType;
import com.wong.collector.infrastructure.config.properties.PipelineProperties;
import com.wong.collector.infrastructure.config.properties.PipelineProperties.Stage;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 基于配置的事件驱动流水线调度器。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PipelineOrchestrator {

    private final TaskOrchestrator taskOrchestrator;
    private final PipelineProperties pipelineProperties;
    private Map<String, Stage> stageMap;

    /**
     * 根据事件 simpleName 触发后续任务。
     */
    public void handleEvent(String eventSimpleName, String paperId) {
        Stage stage = resolveStages().get(eventSimpleName);
        if (stage == null || !stage.isEnabled()) {
            return;
        }
        try {
            boolean accepted = taskOrchestrator.trySchedule(stage.getTaskType(), paperId,
                stage.getDescription(), null);
            if (!accepted) {
                log.warn("pipeline task rejected: event={} paperId={} task={}", eventSimpleName, paperId, stage.getTaskType());
            }
        } catch (Exception ex) {
            log.error("schedule pipeline task failed: event={} paperId={} task={}", eventSimpleName, paperId, stage.getTaskType(), ex);
        }
    }

    private Map<String, Stage> resolveStages() {
        if (stageMap != null) {
            return stageMap;
        }
        Map<String, Stage> map = new HashMap<>();
        List<Stage> configured = pipelineProperties.getStages();
        if (configured == null || configured.isEmpty()) {
            configured = defaultStages();
        }
        for (Stage stage : configured) {
            if (stage.getOnEvent() == null || stage.getTaskType() == null) {
                continue;
            }
            map.put(stage.getOnEvent(), stage);
        }
        this.stageMap = map;
        return stageMap;
    }

    private List<Stage> defaultStages() {
        return List.of(
            build("PaperPreRelevantEvent", TaskType.PAPER_DOWNLOAD, "自动下载任务"),
            build("PaperDownloadedEvent", TaskType.PAPER_TRANSFORM, "PDF 转换任务"),
            build("PaperTransformedEvent", TaskType.PAPER_EMBEDDING, "向量化任务"),
            build("PaperEmbeddingCompletedEvent", TaskType.MATERIAL_EXTRACTION, "材料提取任务")
        );
    }

    private Stage build(String event, TaskType taskType, String desc) {
        Stage stage = new Stage();
        stage.setOnEvent(event);
        stage.setTaskType(taskType);
        stage.setDescription(desc);
        stage.setEnabled(true);
        return stage;
    }
}
