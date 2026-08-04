package com.wong.collector.application.task.processor;

import org.springframework.stereotype.Component;

import com.wong.collector.application.dto.request.DownloadCompletedRequest;
import com.wong.collector.application.workflow.download.PaperDownloadExecutor;
import com.wong.collector.application.service.PaperApplicationService;
import com.wong.collector.domain.common.exception.IllegalStateTransitionException;
import com.wong.collector.domain.paper.model.Paper;
import com.wong.collector.domain.paper.model.PaperState;
import com.wong.collector.domain.paper.repository.PaperRepository;
import com.wong.collector.domain.task.model.TaskRecord;
import com.wong.collector.domain.task.model.TaskType;

@Component
public class DownloadTaskProcessor extends AbstractPaperTaskProcessor {

    private final PaperApplicationService paperApplicationService;
    private final PaperDownloadExecutor downloadExecutor;

    public DownloadTaskProcessor(PaperRepository paperRepository,
                                  PaperApplicationService paperApplicationService,
                                  PaperDownloadExecutor downloadExecutor) {
        super(paperRepository);
        this.paperApplicationService = paperApplicationService;
        this.downloadExecutor = downloadExecutor;
    }

    @Override
    public TaskType supports() {
        return TaskType.PAPER_DOWNLOAD;
    }

    @Override
    protected PaperState getCompletedState() {
        return PaperState.DOWNLOADED;
    }

    @Override
    protected boolean shouldSkip(TaskRecord record, Paper paper) {
        PaperState state = paper.getState();
        if (state == PaperState.PRE_RELEVANT || state == PaperState.DOWNLOADING) {
            return false;
        }
        return super.shouldSkip(record, paper);
    }

    @Override
    protected String skipReason(TaskRecord record, Paper paper) {
        return "skipped(state=" + paper.getState() + ")";
    }

    @Override
    protected String doProcess(TaskRecord record, Paper paper) {
        String paperId = record.getPaperId();
        PaperState state = paper.getState();

        if (state == PaperState.PRE_RELEVANT) {
            paperApplicationService.startDownload(paperId);
        }

        PaperDownloadExecutor.DownloadResult result = downloadExecutor.download(paperId, paper.getDownloadUrl());
        DownloadCompletedRequest request = new DownloadCompletedRequest();
        request.setOssUrl(result.ossUrl());
        request.setFileSize(result.fileSize());

        try {
            paperApplicationService.completeDownload(paperId, request);
        } catch (IllegalStateTransitionException ignored) {
            return "skipped(state_changed)";
        }
        return "下载完成，存储：" + result.ossUrl();
    }
}
