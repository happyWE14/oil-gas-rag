package com.wong.collector.domain.paper.event;

import com.wong.collector.domain.paper.model.Paper;
import com.wong.collector.domain.paper.model.PaperState;
import lombok.Getter;

/** 状态变更事件 */
@Getter
public class PaperStateChangedEvent extends PaperEvent {

    private final PaperState from;
    private final PaperState to;

    public PaperStateChangedEvent(Paper paper, PaperState from, PaperState to) {
        super(paper);
        this.from = from;
        this.to = to;
    }

    @Override
    public String eventName() {
        return "paper.state.changed";
    }
}
