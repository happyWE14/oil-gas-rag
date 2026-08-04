package com.wong.collector.domain.paper.event;

import com.wong.collector.domain.paper.model.Paper;
import lombok.Getter;

/** 放弃处理事件 */
@Getter
public class PaperAbandonedEvent extends PaperEvent {

    private final String reason;

    public PaperAbandonedEvent(Paper paper, String reason) {
        super(paper);
        this.reason = reason;
    }

    @Override
    public String eventName() {
        return "paper.abandoned";
    }
}
