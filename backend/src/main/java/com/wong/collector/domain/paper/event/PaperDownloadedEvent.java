package com.wong.collector.domain.paper.event;

import com.wong.collector.domain.paper.model.Paper;
import lombok.Getter;

/** 下载完成事件 */
@Getter
public class PaperDownloadedEvent extends PaperEvent {

    private final String ossUrl;
    private final Long fileSize;

    public PaperDownloadedEvent(Paper paper, String ossUrl, Long fileSize) {
        super(paper);
        this.ossUrl = ossUrl;
        this.fileSize = fileSize;
    }

    @Override
    public String eventName() {
        return "paper.download.completed";
    }
}
