package com.wong.collector.domain.paper.event;

import com.wong.collector.domain.paper.model.Paper;
import com.wong.collector.domain.paper.model.TransformProvider;
import lombok.Getter;

/** 转换开始事件 */
@Getter
public class PaperTransformStartedEvent extends PaperEvent {

    private final TransformProvider provider;

    public PaperTransformStartedEvent(Paper paper, TransformProvider provider) {
        super(paper);
        this.provider = provider;
    }

    @Override
    public String eventName() {
        return "paper.transform.started";
    }
}
