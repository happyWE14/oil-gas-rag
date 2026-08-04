package com.wong.collector.application.task.processor;

import org.springframework.stereotype.Component;

import com.wong.collector.application.workflow.material.MaterialExtractionExecutor;
import com.wong.collector.application.service.PaperApplicationService;
import com.wong.collector.domain.common.exception.IllegalStateTransitionException;
import com.wong.collector.domain.paper.model.Paper;
import com.wong.collector.domain.paper.model.PaperState;
import com.wong.collector.domain.paper.repository.PaperRepository;
import com.wong.collector.domain.task.model.TaskRecord;
import com.wong.collector.domain.task.model.TaskType;

@Component
public class MaterialExtractionTaskProcessor extends AbstractPaperTaskProcessor {

    private final PaperApplicationService paperApplicationService;
    private final MaterialExtractionExecutor extractionExecutor;

    public MaterialExtractionTaskProcessor(PaperRepository paperRepository,
                                            PaperApplicationService paperApplicationService,
                                            MaterialExtractionExecutor extractionExecutor) {
        super(paperRepository);
        this.paperApplicationService = paperApplicationService;
        this.extractionExecutor = extractionExecutor;
    }

    @Override
    public TaskType supports() {
        return TaskType.MATERIAL_EXTRACTION;
    }

    @Override
    protected PaperState getCompletedState() {
        return PaperState.MATERIAL_EXTRACTED;
    }

    @Override
    protected boolean shouldSkip(TaskRecord record, Paper paper) {
        PaperState state = paper.getState();
        if (state == PaperState.EMBEDDING_COMPLETED || state == PaperState.EXTRACTING) {
            return false;
        }
        return super.shouldSkip(record, paper);
    }

    @Override
    protected String doProcess(TaskRecord record, Paper paper) {
        String paperId = record.getPaperId();
        PaperState state = paper.getState();

        if (state == PaperState.EMBEDDING_COMPLETED) {
            paperApplicationService.startExtraction(paperId);
        }

        try {
            return extractionExecutor.extract(paperId);
        } catch (IllegalStateTransitionException ignored) {
            return "skipped(state_changed)";
        }
    }
}
