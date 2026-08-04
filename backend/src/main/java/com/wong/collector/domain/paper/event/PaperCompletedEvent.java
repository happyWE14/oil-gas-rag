package com.wong.collector.domain.paper.event;

import com.wong.collector.domain.paper.model.Paper;

/** 流程完成事件 */
public class PaperCompletedEvent extends PaperEvent {

    public PaperCompletedEvent(Paper paper) {
        super(paper);
    }

    @Override
    public String eventName() {
        return "paper.completed";
    }
}
