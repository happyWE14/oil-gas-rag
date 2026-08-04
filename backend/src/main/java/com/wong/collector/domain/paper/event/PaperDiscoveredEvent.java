package com.wong.collector.domain.paper.event;

import com.wong.collector.domain.paper.model.Paper;
import lombok.Getter;

/**
 * 论文被发现事件。
 */
@Getter
public class PaperDiscoveredEvent extends PaperEvent {

    private final String title;
    private final String abstractContent;

    public PaperDiscoveredEvent(Paper paper) {
        super(paper);
        this.title = paper.getTitle().value();
        this.abstractContent = paper.getAbstractContent();
    }

    @Override
    public String eventName() {
        return "paper.discovered";
    }
}
