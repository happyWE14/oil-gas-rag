package com.wong.collector.domain.paper.event;

import com.wong.collector.domain.paper.model.Paper;
import lombok.Getter;

/** 下载失败事件 */
@Getter
public class PaperDownloadFailedEvent extends PaperEvent {

    private final String reason;

    public PaperDownloadFailedEvent(Paper paper, String reason) {
        super(paper);
        this.reason = reason;
    }

    @Override
    public String eventName() {
        return "paper.download.failed";
    }
}
