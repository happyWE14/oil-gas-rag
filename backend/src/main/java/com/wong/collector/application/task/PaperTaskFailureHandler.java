package com.wong.collector.application.task;

import org.springframework.stereotype.Component;

import com.wong.collector.application.service.PaperApplicationService;
import com.wong.collector.domain.task.model.TaskRecord;
import com.wong.collector.domain.task.model.TaskType;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Applies terminal task failure to the Paper aggregate (best-effort).
 * <p>
 * Transient failures should not corrupt Paper state; only terminal failures are mapped here.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PaperTaskFailureHandler {

    private final PaperApplicationService paperApplicationService;

    public void onTerminalFailure(TaskRecord record, String errorMessage) {
        if (record == null || record.getTaskType() == null) {
            return;
        }
        String paperId = record.getPaperId();
        if (paperId == null || paperId.isBlank()) {
            return;
        }
        TaskType type = record.getTaskType();
        try {
            switch (type) {
                case PAPER_PRE_ANALYSIS -> paperApplicationService.failPreAnalysis(paperId, errorMessage);
                case PAPER_DOWNLOAD -> paperApplicationService.failDownload(paperId, errorMessage);
                case PAPER_TRANSFORM -> paperApplicationService.failTransform(paperId, errorMessage);
                case PAPER_EMBEDDING -> paperApplicationService.failEmbedding(paperId, errorMessage);
                case MATERIAL_EXTRACTION -> paperApplicationService.failExtraction(paperId, errorMessage);
                default -> {
                }
            }
        } catch (Exception ex) {
            log.warn("apply terminal failure to paper failed: taskType={} paperId={} reason={}",
                type, paperId, ex.getMessage());
        }
    }
}

