package com.wong.collector.domain.paper.event;

import java.util.List;

import com.wong.collector.domain.paper.model.MaterialExtraction;
import com.wong.collector.domain.paper.model.Paper;
import lombok.Getter;

/** 材料提取完成事件 */
@Getter
public class PaperMaterialExtractedEvent extends PaperEvent {

    private final List<MaterialExtraction> materials;

    public PaperMaterialExtractedEvent(Paper paper, List<MaterialExtraction> materials) {
        super(paper);
        this.materials = materials;
    }

    @Override
    public String eventName() {
        return "paper.extract.completed";
    }
}
