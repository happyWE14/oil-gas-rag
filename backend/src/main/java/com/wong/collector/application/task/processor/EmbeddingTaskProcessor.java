package com.wong.collector.application.task.processor;

import org.springframework.stereotype.Component;

import com.wong.collector.application.workflow.embedding.PaperEmbeddingExecutor;
import com.wong.collector.application.service.PaperApplicationService;
import com.wong.collector.domain.common.exception.IllegalStateTransitionException;
import com.wong.collector.domain.paper.model.Paper;
import com.wong.collector.domain.paper.model.PaperState;
import com.wong.collector.domain.paper.repository.PaperRepository;
import com.wong.collector.domain.task.model.TaskRecord;
import com.wong.collector.domain.task.model.TaskType;

@Component
public class EmbeddingTaskProcessor extends AbstractPaperTaskProcessor {

    private final PaperApplicationService paperApplicationService;
    private final PaperEmbeddingExecutor embeddingExecutor;

    public EmbeddingTaskProcessor(PaperRepository paperRepository,
                                   PaperApplicationService paperApplicationService,
                                   PaperEmbeddingExecutor embeddingExecutor) {
        super(paperRepository);
        this.paperApplicationService = paperApplicationService;
        this.embeddingExecutor = embeddingExecutor;
    }

    @Override
    public TaskType supports() {
        return TaskType.PAPER_EMBEDDING;
    }

    @Override
    protected PaperState getCompletedState() {
        return PaperState.EMBEDDING_COMPLETED;
    }

    @Override
    protected boolean shouldSkip(TaskRecord record, Paper paper) {
        PaperState state = paper.getState();
        if (state == PaperState.TRANSFORMED || state == PaperState.EMBEDDING) {
            return false;
        }
        return super.shouldSkip(record, paper);
    }

    @Override
    protected String doProcess(TaskRecord record, Paper paper) {
        String paperId = record.getPaperId();
        PaperState state = paper.getState();

        if (state == PaperState.TRANSFORMED) {
            paperApplicationService.startEmbedding(paperId);
        }

        try {
            return embeddingExecutor.embed(paperId);
        } catch (IllegalStateTransitionException ignored) {
            return "skipped(state_changed)";
        }
    }
}
