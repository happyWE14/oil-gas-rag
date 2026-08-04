package com.wong.collector.domain.paper.event;

import com.wong.collector.domain.paper.model.Paper;

/** 预分析启动事件 */
public class PaperPreAnalysisStartedEvent extends PaperEvent {

    public PaperPreAnalysisStartedEvent(Paper paper) {
        super(paper);
    }

    @Override
    public String eventName() {
        return "paper.pre-analysis.started";
    }
}
