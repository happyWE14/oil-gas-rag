package com.wong.collector.application.task.processor;

import org.springframework.stereotype.Component;

import com.wong.collector.application.workflow.transform.PaperTransformExecutor;
import com.wong.collector.application.service.PaperApplicationService;
import com.wong.collector.domain.common.exception.IllegalStateTransitionException;
import com.wong.collector.domain.paper.model.Paper;
import com.wong.collector.domain.paper.model.PaperState;
import com.wong.collector.domain.paper.repository.PaperRepository;
import com.wong.collector.domain.paper.model.TransformProvider;
import com.wong.collector.domain.task.model.TaskRecord;
import com.wong.collector.domain.task.model.TaskType;
import com.wong.collector.infrastructure.config.properties.TransformBusinessProperties;

@Component
public class TransformTaskProcessor extends AbstractPaperTaskProcessor {

    private final PaperApplicationService paperApplicationService;
    private final PaperTransformExecutor transformExecutor;
    private final TransformBusinessProperties transformBusinessProperties;

    public TransformTaskProcessor(PaperRepository paperRepository,
                                   PaperApplicationService paperApplicationService,
                                   PaperTransformExecutor transformExecutor,
                                   TransformBusinessProperties transformBusinessProperties) {
        super(paperRepository);
        this.paperApplicationService = paperApplicationService;
        this.transformExecutor = transformExecutor;
        this.transformBusinessProperties = transformBusinessProperties;
    }

    @Override
    public TaskType supports() {
        return TaskType.PAPER_TRANSFORM;
    }

    @Override
    protected PaperState getCompletedState() {
        return PaperState.TRANSFORMED;
    }

    @Override
    protected boolean shouldSkip(TaskRecord record, Paper paper) {
        PaperState state = paper.getState();
        if (state == PaperState.DOWNLOADED || state == PaperState.TRANSFORMING) {
            return false;
        }
        return super.shouldSkip(record, paper);
    }

    @Override
    protected String doProcess(TaskRecord record, Paper paper) {
        String paperId = record.getPaperId();
        PaperState state = paper.getState();

        if (state == PaperState.DOWNLOADED) {
            TransformProvider provider = transformBusinessProperties.getProvider();
            paperApplicationService.startTransform(paperId, provider);
        }

        try {
            String markdown = transformExecutor.transform(paperId);
            return "转换完成，Markdown：" + markdown;
        } catch (IllegalStateTransitionException ignored) {
            return "skipped(state_changed)";
        }
    }
}
