package com.wong.collector.domain.paper.event;

import com.wong.collector.domain.common.event.DomainEvent;

import lombok.Getter;

/**
 * 请求对指定论文做 citation/reference 扩展。
 * <p>
 * 该事件只表达“需要扩展”的意图，由应用层监听后调度任务；避免 workflow/search 直接依赖 TaskOrchestrator 造成循环依赖。
 */
@Getter
public class PaperRelationExpansionRequestedEvent extends DomainEvent {

    private final String paperId;

    public PaperRelationExpansionRequestedEvent(String paperId) {
        this.paperId = paperId;
    }

    @Override
    public String getAggregateId() {
        return paperId;
    }

    @Override
    public String eventName() {
        return "paper.relation_expansion.requested";
    }
}

