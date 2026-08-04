package com.wong.collector.domain.paper.event;

import com.wong.collector.domain.paper.model.Paper;

/** 材料提取开始事件 */
public class PaperExtractionStartedEvent extends PaperEvent {

    public PaperExtractionStartedEvent(Paper paper) {
        super(paper);
    }

    @Override
    public String eventName() {
        return "paper.extract.started";
    }
}
