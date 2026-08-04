package com.wong.collector.domain.paper.event;

import com.wong.collector.domain.common.event.DomainEvent;
import lombok.Getter;

/**
 * 模型输出持久化事件，异步落库不影响主业务。
 */
@Getter
public class PaperModelOutputCapturedEvent extends DomainEvent {

    private final String paperId;
    private final String taskType;
    private final String modelName;
    private final String modelOutput;

    public PaperModelOutputCapturedEvent(String paperId, String taskType, String modelName, String modelOutput) {
        this.paperId = paperId;
        this.taskType = taskType;
        this.modelName = modelName;
        this.modelOutput = modelOutput;
    }

    @Override
    public String getAggregateId() {
        return paperId;
    }

    @Override
    public String eventName() {
        return "paper.model_output_captured";
    }
}

