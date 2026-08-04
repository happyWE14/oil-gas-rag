package com.wong.collector.domain.paper.event;

import com.wong.collector.domain.paper.model.Paper;
import lombok.Getter;

/** 预分析不相关事件 */
@Getter
public class PreAnalysisIrrelevantEvent extends PaperEvent {

    private final double score;
    private final String reason;

    public PreAnalysisIrrelevantEvent(Paper paper, double score, String reason) {
        super(paper);
        this.score = score;
        this.reason = reason;
    }

    @Override
    public String eventName() {
        return "paper.pre-analysis.irrelevant";
    }
}
