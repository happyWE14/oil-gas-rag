package com.wong.collector.application.task.processor;

import com.wong.collector.application.dto.request.PreAnalysisResultRequest;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wong.collector.application.service.PaperApplicationService;
import com.wong.collector.application.workflow.preanalysis.PaperRelevanceAnalyzer;
import com.wong.collector.application.workflow.dto.PreAnalysisDecision;
import com.wong.collector.domain.common.exception.IllegalStateTransitionException;
import com.wong.collector.domain.paper.model.Paper;
import com.wong.collector.domain.paper.model.PaperState;
import com.wong.collector.domain.paper.repository.PaperRepository;
import com.wong.collector.domain.task.model.TaskRecord;
import com.wong.collector.domain.task.model.TaskType;
import org.springframework.stereotype.Component;

@Component
public class PreAnalysisTaskProcessor extends AbstractPaperTaskProcessor {

    private final PaperRelevanceAnalyzer paperRelevanceAnalyzer;
    private final PaperApplicationService paperApplicationService;
    private final ObjectMapper objectMapper;

    public PreAnalysisTaskProcessor(PaperRepository paperRepository,
                                     PaperRelevanceAnalyzer paperRelevanceAnalyzer,
                                     PaperApplicationService paperApplicationService,
                                     ObjectMapper objectMapper) {
        super(paperRepository);
        this.paperRelevanceAnalyzer = paperRelevanceAnalyzer;
        this.paperApplicationService = paperApplicationService;
        this.objectMapper = objectMapper;
    }

    @Override
    public TaskType supports() {
        return TaskType.PAPER_PRE_ANALYSIS;
    }

    @Override
    protected PaperState getCompletedState() {
        // 预分析不使用默认的 isAtLeast 跳过逻辑
        return null;
    }

    @Override
    protected boolean shouldSkip(TaskRecord record, Paper paper) {
        PaperState state = paper.getState();
        // 只有 DISCOVERED 和 PRE_ANALYZING 状态才执行预分析
        return state != PaperState.DISCOVERED && state != PaperState.PRE_ANALYZING;
    }

    @Override
    protected String skipReason(TaskRecord record, Paper paper) {
        return "skipped(state=" + paper.getState() + ")";
    }

    @Override
    protected String doProcess(TaskRecord record, Paper paper) {
        String paperId = record.getPaperId();
        paperApplicationService.startPreAnalysis(paperId);

        PreAnalysisDecision decision = paperRelevanceAnalyzer.analyze(
            paperId,
            paper.getTitle().value(),
            paper.getAbstractContent()
        );

        PreAnalysisResultRequest request = new PreAnalysisResultRequest();
        request.setRelevant(decision.relevant());
        request.setScore(decision.score());
        request.setAnalyzer(decision.analyzer());
        request.setReason(decision.reason());
        request.setRawResult(decision.rawResult());
        request.setKeywordsSnapshot(serializeKeywords(decision));
        request.setThresholdUsed(decision.thresholdUsed());
        request.setHasExtractableMaterialInfo(decision.hasExtractableMaterialInfo());

        try {
            paperApplicationService.completePreAnalysis(paperId, request);
            return "relevant=" + decision.relevant() + ",score=" + decision.score();
        } catch (IllegalStateTransitionException ignored) {
            return "skipped(state_changed)";
        }
    }

    private String serializeKeywords(PreAnalysisDecision decision) {
        if (decision.keywordsUsed() == null) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(decision.keywordsUsed());
        } catch (Exception e) {
            return "[]";
        }
    }
}
