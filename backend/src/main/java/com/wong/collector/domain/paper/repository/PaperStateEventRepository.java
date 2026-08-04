package com.wong.collector.domain.paper.repository;

import java.util.List;

import com.wong.collector.domain.paper.model.PaperId;
import com.wong.collector.domain.paper.model.PaperState;
import com.wong.collector.domain.paper.model.PaperStateEvent;

/**
 * 状态事件仓储。
 */
public interface PaperStateEventRepository {

    void save(PaperStateEvent event);

    List<PaperStateEvent> findRecentEvents(PaperId paperId, int limit);

    long countByState(PaperState state);
}
