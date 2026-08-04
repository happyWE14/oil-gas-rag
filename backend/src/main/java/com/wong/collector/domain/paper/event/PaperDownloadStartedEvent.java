package com.wong.collector.domain.paper.event;

import com.wong.collector.domain.paper.model.Paper;

/** 下载开始事件 */
public class PaperDownloadStartedEvent extends PaperEvent {

    public PaperDownloadStartedEvent(Paper paper) {
        super(paper);
    }

    @Override
    public String eventName() {
        return "paper.download.started";
    }
}
