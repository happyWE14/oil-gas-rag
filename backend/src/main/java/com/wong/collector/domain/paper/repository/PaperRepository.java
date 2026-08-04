package com.wong.collector.domain.paper.repository;

import java.util.List;
import java.util.Optional;

import com.wong.collector.domain.common.model.PageResult;
import com.wong.collector.domain.paper.model.Paper;
import com.wong.collector.domain.paper.model.PaperId;
import com.wong.collector.domain.paper.model.PaperState;

/**
 * 论文聚合仓储接口。
 */
public interface PaperRepository {

    Optional<Paper> findById(PaperId id);

    Paper save(Paper paper);

    void delete(PaperId id);

    List<Paper> findByState(PaperState state, int limit);

    /**
     * 分页查询论文列表（预留多条件过滤扩展点）。
     */
    PageResult<Paper> page(PaperQuery query);

    long countByState(PaperState state);

    boolean exists(PaperId id);

    /**
     * 基于 DOI（大小写不敏感）查找论文主键。
     * <p>
     * 用于跨来源（CORE/ARXIV/SEMANTIC_SCHOLAR 等）去重与归并。
     */
    Optional<PaperId> findIdByDoi(String doi);
}
