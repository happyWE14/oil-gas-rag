package com.wong.collector.domain.paper.event;

import com.wong.collector.domain.common.event.DomainEvent;
import lombok.Getter;

/**
 * 材料提取完成事件
 */
@Getter
public class MaterialExtractedEvent extends DomainEvent {

    private static final long serialVersionUID = 1L;

    private final String paperId;
    private final String extractionId;

    public MaterialExtractedEvent(String paperId, String extractionId) {
        this.paperId = paperId;
        this.extractionId = extractionId;
    }

    @Override
    public String getAggregateId() {
        return paperId; // 返回聚合根ID
    }

    @Override
    public String eventName() {
        return "MATERIAL_EXTRACTED";
    }
}
