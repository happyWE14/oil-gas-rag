package com.wong.collector.domain.paper.event;

import com.wong.collector.domain.paper.model.Paper;

/** 向量化开始事件 */
public class PaperEmbeddingStartedEvent extends PaperEvent {

    public PaperEmbeddingStartedEvent(Paper paper) {
        super(paper);
    }

    @Override
    public String eventName() {
        return "paper.embedding.started";
    }
}
