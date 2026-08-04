package com.wong.collector.domain.paper.event;

import com.wong.collector.domain.paper.model.Paper;
import lombok.Getter;

/** 预分析失败事件 */
@Getter
public class PreAnalysisFailedEvent extends PaperEvent {

    private final String reason;

    public PreAnalysisFailedEvent(Paper paper, String reason) {
        super(paper);
        this.reason = reason;
    }

    @Override
    public String eventName() {
        return "paper.pre-analysis.failed";
    }
}
