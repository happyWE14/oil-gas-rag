package com.wong.collector.domain.paper.event;

import com.wong.collector.domain.paper.model.Paper;
import lombok.Getter;

/** 转换完成事件 */
@Getter
public class PaperTransformedEvent extends PaperEvent {

    private final String markdownPath;

    public PaperTransformedEvent(Paper paper, String markdownPath) {
        super(paper);
        this.markdownPath = markdownPath;
    }

    @Override
    public String eventName() {
        return "paper.transform.completed";
    }
}
