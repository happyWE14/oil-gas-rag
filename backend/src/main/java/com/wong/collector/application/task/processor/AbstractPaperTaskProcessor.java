package com.wong.collector.application.task.processor;

import com.wong.collector.domain.common.exception.NotFoundException;
import com.wong.collector.domain.paper.model.Paper;
import com.wong.collector.domain.paper.model.PaperId;
import com.wong.collector.domain.paper.model.PaperState;
import com.wong.collector.domain.paper.repository.PaperRepository;
import com.wong.collector.domain.task.model.TaskRecord;

/**
 * 论文任务处理器模板基类。
 * 封装通用流程：加载论文 → 状态守卫 → 执行业务逻辑。
 */
public abstract class AbstractPaperTaskProcessor implements TaskProcessor {

    protected final PaperRepository paperRepository;

    protected AbstractPaperTaskProcessor(PaperRepository paperRepository) {
        this.paperRepository = paperRepository;
    }

    @Override
    public final String process(TaskRecord record) throws Exception {
        String paperId = record.getPaperId();
        Paper paper = paperRepository.findById(PaperId.of(paperId))
            .orElseThrow(() -> new NotFoundException("Paper not found: " + paperId));

        if (shouldSkip(record, paper)) {
            return skipReason(record, paper);
        }

        return doProcess(record, paper);
    }

    /**
     * 判断是否应跳过此任务（幂等性守卫）。
     * 默认实现：当前状态已达到或超过完成状态时跳过。
     * 子类可覆盖以实现自定义逻辑。
     */
    protected boolean shouldSkip(TaskRecord record, Paper paper) {
        PaperState completedState = getCompletedState();
        if (completedState == null) {
            return false;
        }
        return paper.getState().isAtLeast(completedState);
    }

    /**
     * 返回跳过原因。
     */
    protected String skipReason(TaskRecord record, Paper paper) {
        return "skipped(already=" + paper.getState() + ")";
    }

    /**
     * 返回此任务完成后的目标状态，用于默认跳过判断。
     * 返回 null 表示不使用默认跳过逻辑。
     */
    protected abstract PaperState getCompletedState();

    /**
     * 执行实际业务逻辑。
     */
    protected abstract String doProcess(TaskRecord record, Paper paper) throws Exception;
}
