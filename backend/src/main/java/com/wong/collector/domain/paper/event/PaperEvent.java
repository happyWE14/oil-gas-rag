package com.wong.collector.domain.paper.event;

import com.wong.collector.domain.common.event.DomainEvent;
import com.wong.collector.domain.paper.model.Paper;
import com.wong.collector.domain.paper.model.PaperId;
import com.wong.collector.domain.paper.model.PaperState;
import lombok.Getter;

/**
 * Paper 聚合事件基类。
 */
@Getter
public abstract class PaperEvent extends DomainEvent {

    private final PaperId paperId;
    private final PaperState state;

    protected PaperEvent(Paper paper) {
        this.paperId = paper.getId();
        this.state = paper.getState();
    }

    @Override
    public String getAggregateId() {
        return paperId == null ? null : paperId.getValue();
    }

}
