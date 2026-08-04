package com.wong.collector.domain.paper.event;

import com.wong.collector.domain.paper.model.Paper;

/** 向量化完成事件 */
public class PaperEmbeddingCompletedEvent extends PaperEvent {

    public PaperEmbeddingCompletedEvent(Paper paper) {
        super(paper);
    }

    @Override
    public String eventName() {
        return "paper.embedding.completed";
    }
}
